package com.hamijeu.offlinecctv;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.SurfaceTexture;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CaptureService extends Service {
    private static final String TAG = "OfflineCCTV";
    private static final String CHANNEL_ID = "capture";
    private static final int NOTIFICATION_ID = 7;
    private static final int MAX_FAILURES = 3;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private PowerManager.WakeLock wakeLock;
    private int intervalSeconds = 5;
    private long storageLimitBytes = 512L * 1024L * 1024L;
    private ResolutionPreset resolutionPreset = ResolutionPreset.MEDIUM;
    private int exposureCompensation = 0;
    private int failureCount;
    private boolean captureInFlight;
    private File outputFolder;
    private SurfaceTexture dummyPreviewTexture;
    private Surface dummyPreviewSurface;

    private final Runnable captureRunnable = this::captureStill;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        cameraThread = new HandlerThread("OfflineCCTV-Camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        readSettings(intent);
        startForegroundCompat(notification("Starting capture"));
        acquireWakeLock();
        outputFolder = resolveOutputFolder();
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            Log.e(TAG, "Failed to create output folder: " + outputFolder);
            stopSelf();
            return START_NOT_STICKY;
        }
        openCamera();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cameraHandler.removeCallbacksAndMessages(null);
        closeCamera();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
        super.onDestroy();
    }

    private void readSettings(Intent intent) {
        if (intent == null) {
            return;
        }
        intervalSeconds = intent.getIntExtra(CctvSettings.EXTRA_INTERVAL_SECONDS, intervalSeconds);
        storageLimitBytes = intent.getLongExtra(CctvSettings.EXTRA_STORAGE_LIMIT_BYTES, storageLimitBytes);
        String preset = intent.getStringExtra(CctvSettings.EXTRA_RESOLUTION_PRESET);
        if (preset != null) {
            resolutionPreset = ResolutionPreset.valueOf(preset);
        }
        exposureCompensation = intent.getIntExtra(CctvSettings.EXTRA_EXPOSURE_COMPENSATION, exposureCompensation);
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing camera permission");
            stopSelf();
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = chooseCameraId(manager);
            Size size = chooseJpegSize(manager, cameraId);
            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::saveImage, cameraHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                    recordFailure("Camera disconnected");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    recordFailure("Camera error " + error);
                }
            }, cameraHandler);
        } catch (CameraAccessException | RuntimeException ex) {
            recordFailure("Open camera failed: " + ex.getMessage());
        }
    }

    private String chooseCameraId(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            if (fallback == null) {
                fallback = id;
            }
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("No camera found");
        }
        return fallback;
    }

    private Size chooseJpegSize(CameraManager manager, String cameraId) throws CameraAccessException {
        StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("No stream configuration");
        }
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<ResolutionPreset.SizeOption> options = new ArrayList<>();
        for (Size size : sizes) {
            options.add(new ResolutionPreset.SizeOption(size.getWidth(), size.getHeight()));
        }
        ResolutionPreset.SizeOption selected = resolutionPreset.select(options);
        return new Size(selected.width, selected.height);
    }

    private void createSession() {
        try {
            dummyPreviewTexture = new SurfaceTexture(0);
            dummyPreviewTexture.setDefaultBufferSize(640, 480);
            dummyPreviewSurface = new Surface(dummyPreviewTexture);
            cameraDevice.createCaptureSession(
                    Arrays.asList(imageReader.getSurface(), dummyPreviewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            updateNotification("Running: every " + intervalSeconds + "s");
                            startRepeatingPreview();
                            scheduleNext(1800);
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            recordFailure("Camera session configure failed");
                        }
                    },
                    cameraHandler
            );
        } catch (CameraAccessException | RuntimeException ex) {
            recordFailure("Create session failed: " + ex.getMessage());
        }
    }

    private void startRepeatingPreview() {
        if (cameraDevice == null || captureSession == null || dummyPreviewSurface == null) {
            return;
        }
        try {
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(dummyPreviewSurface);
            ExposureApplier.apply(request, currentExposureSettings());
            captureSession.setRepeatingRequest(request.build(), null, cameraHandler);
        } catch (CameraAccessException | RuntimeException ex) {
            recordFailure("Preview settle failed: " + ex.getMessage());
        }
    }

    private void captureStill() {
        if (cameraDevice == null || captureSession == null || imageReader == null || captureInFlight) {
            scheduleNext(1000);
            return;
        }
        try {
            captureInFlight = true;
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(imageReader.getSurface());
            if (dummyPreviewSurface != null) {
                request.addTarget(dummyPreviewSurface);
            }
            ExposureApplier.apply(request, currentExposureSettings());
            captureSession.capture(request.build(), new CameraCaptureSession.CaptureCallback() {
            }, cameraHandler);
        } catch (CameraAccessException | RuntimeException ex) {
            captureInFlight = false;
            recordFailure("Capture failed: " + ex.getMessage());
            scheduleNext(intervalSeconds * 1000L);
        }
    }

    private CctvSettings currentExposureSettings() {
        return new CctvSettings(
                intervalSeconds,
                storageLimitBytes,
                resolutionPreset,
                CctvSettings.Mode.STEALTH,
                exposureCompensation
        );
    }

    private void saveImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                captureInFlight = false;
                scheduleNext(intervalSeconds * 1000L);
                return;
            }
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            Instant capturedAt = Instant.now();
            byte[] outputBytes = OverlayRenderer.renderTimestamp(bytes, capturedAt);
            File output = new File(outputFolder, PhotoNamer.name(capturedAt));
            try (FileOutputStream stream = new FileOutputStream(output)) {
                stream.write(outputBytes);
            }
            StorageCleaner.clean(outputFolder, storageLimitBytes);
            failureCount = 0;
            updateNotification("Last photo: " + output.getName());
        } catch (Exception ex) {
            recordFailure("Save image failed: " + ex.getMessage());
        } finally {
            if (image != null) {
                image.close();
            }
            captureInFlight = false;
            scheduleNext(intervalSeconds * 1000L);
        }
    }

    private void scheduleNext(long delayMs) {
        cameraHandler.removeCallbacks(captureRunnable);
        cameraHandler.postDelayed(captureRunnable, delayMs);
    }

    private void recordFailure(String message) {
        failureCount++;
        Log.e(TAG, message);
        updateNotification(message);
        if (failureCount >= MAX_FAILURES) {
            stopSelf();
        }
    }

    private File resolveOutputFolder() {
        if (Build.VERSION.SDK_INT <= 28) {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "OfflineCCTV");
        }
        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures == null) {
            pictures = getFilesDir();
        }
        return new File(pictures, "OfflineCCTV");
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OfflineCCTV:Capture");
        wakeLock.acquire(12 * 60 * 60 * 1000L);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Offline CCTV capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(message));
    }

    private Notification notification(String message) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Hamijeu - Webcam Mobile")
                .setContentText(message)
                .setOngoing(true)
                .build();
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (dummyPreviewSurface != null) {
            dummyPreviewSurface.release();
            dummyPreviewSurface = null;
        }
        if (dummyPreviewTexture != null) {
            dummyPreviewTexture.release();
            dummyPreviewTexture = null;
        }
    }
}
