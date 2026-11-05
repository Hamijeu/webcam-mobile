package com.hamijeu.offlinecctv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final String PREFS = "offline_cctv_settings";

    private EditText intervalInput;
    private EditText storageInput;
    private Spinner compensationSpinner;
    private Spinner resolutionSpinner;
    private Spinner modeSpinner;
    private Button startStopButton;
    private TextView statusText;
    private TextureView previewView;

    private HandlerThread previewThread;
    private Handler previewHandler;
    private CameraDevice previewCamera;
    private CameraCaptureSession previewSession;
    private String previewCameraId;
    private boolean running;
    private boolean stealth;
    private float previousBrightness = -1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPreviewThread();
        startPreviewIfReady();
    }

    @Override
    protected void onPause() {
        saveRawSettings();
        closePreview();
        stopPreviewThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            startPreviewIfReady();
        }
    }

    private void buildUi() {
        int padding = dp(16);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(16, 18, 20));
        scrollView.addView(root);

        TextView title = text("Hamijeu - Webcam Mobile", 22, Color.WHITE);
        root.addView(title);

        statusText = text("Stopped", 15, Color.rgb(168, 176, 184));
        root.addView(statusText);

        previewView = new TextureView(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
        );
        previewParams.setMargins(0, dp(10), 0, dp(10));
        previewView.setLayoutParams(previewParams);
        previewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startPreviewIfReady();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closePreview();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        root.addView(previewView);

        intervalInput = input(loadString("interval", "5"));
        storageInput = input(loadString("storage_gb", "0.5"));
        resolutionSpinner = spinner(new String[]{"LOW", "MEDIUM", "HIGH"});
        modeSpinner = spinner(new String[]{"STEALTH", "LOCK_SCREEN"});
        compensationSpinner = spinner(new String[]{"-4", "-3", "-2", "-1", "0", "1", "2", "3", "4"});

        selectSpinner(resolutionSpinner, loadString("resolution", "LOW"));
        selectSpinner(modeSpinner, loadString("mode", "STEALTH"));
        selectSpinner(compensationSpinner, loadString("compensation", "0"));

        root.addView(label("Interval detik"));
        root.addView(intervalInput);
        root.addView(label("Batas storage GB"));
        root.addView(storageInput);
        root.addView(label("Resolusi"));
        root.addView(resolutionSpinner);
        root.addView(label("Mode"));
        root.addView(modeSpinner);
        root.addView(label("Exposure auto"));
        root.addView(compensationSpinner);

        Button applyPreviewButton = button("Apply Preview");
        applyPreviewButton.setOnClickListener(v -> restartPreview());
        root.addView(applyPreviewButton);

        startStopButton = button("Start");
        startStopButton.setOnClickListener(v -> toggleCapture());
        root.addView(startStopButton);

        Button stealthButton = button("Stealth Screen");
        stealthButton.setOnClickListener(v -> toggleStealth());
        root.addView(stealthButton);

        TextView hint = text("Foto disimpan lokal. Mode lock screen bersifat best-effort.", 13, Color.rgb(168, 176, 184));
        root.addView(hint);

        setContentView(scrollView);
        attachPersistence();
    }

    private TextView label(String value) {
        TextView view = text(value, 14, Color.rgb(241, 243, 245));
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private EditText input(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setBackgroundColor(Color.rgb(27, 31, 35));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(view);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view);
                view.setBackgroundColor(Color.rgb(27, 31, 35));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundColor(Color.rgb(27, 31, 35));
        return spinner;
    }

    private void styleSpinnerText(TextView view) {
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void toggleCapture() {
        if (running) {
            stopService(new Intent(this, CaptureService.class));
            running = false;
            startStopButton.setText("Start");
            statusText.setText("Stopped");
            startPreviewIfReady();
            return;
        }

        if (!hasCameraPermission()) {
            requestNeededPermissions();
            Toast.makeText(this, "Izinkan kamera dulu", Toast.LENGTH_SHORT).show();
            return;
        }

        CctvSettings settings;
        try {
            settings = currentSettings();
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        saveRawSettings();
        closePreview();
        Intent intent = new Intent(this, CaptureService.class);
        putSettingsExtras(intent, settings);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        running = true;
        startStopButton.setText("Stop");
        statusText.setText("Running: " + settings.intervalSeconds + "s, " + settings.resolutionPreset + ", exposure " + settings.exposureCompensation);
    }

    private CctvSettings currentSettings() {
        return CctvSettings.parse(
                intervalInput.getText().toString(),
                storageInput.getText().toString(),
                resolutionSpinner.getSelectedItem().toString(),
                modeSpinner.getSelectedItem().toString(),
                compensationSpinner.getSelectedItem().toString()
        );
    }

    private void putSettingsExtras(Intent intent, CctvSettings settings) {
        intent.putExtra(CctvSettings.EXTRA_INTERVAL_SECONDS, settings.intervalSeconds);
        intent.putExtra(CctvSettings.EXTRA_STORAGE_LIMIT_BYTES, settings.storageLimitBytes);
        intent.putExtra(CctvSettings.EXTRA_RESOLUTION_PRESET, settings.resolutionPreset.name());
        intent.putExtra(CctvSettings.EXTRA_MODE, settings.mode.name());
        intent.putExtra(CctvSettings.EXTRA_EXPOSURE_COMPENSATION, settings.exposureCompensation);
    }

    private void toggleStealth() {
        stealth = !stealth;
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (stealth) {
            saveRawSettings();
            previousBrightness = params.screenBrightness;
            params.screenBrightness = 0.01f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            LinearLayout black = new LinearLayout(this);
            black.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            black.setBackgroundColor(Color.BLACK);
            Button exit = button("Exit");
            exit.setAlpha(0.15f);
            exit.setOnClickListener(v -> toggleStealth());
            black.addView(exit);
            setContentView(black);
        } else {
            params.screenBrightness = previousBrightness;
            window.setAttributes(params);
            getWindow().getDecorView().setSystemUiVisibility(0);
            buildUi();
            startPreviewIfReady();
        }
    }

    private void restartPreview() {
        saveRawSettings();
        closePreview();
        startPreviewIfReady();
    }

    private void attachPersistence() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                saveRawSettings();
            }
        };
        intervalInput.addTextChangedListener(watcher);
        storageInput.addTextChangedListener(watcher);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveRawSettings();
                if (parent == compensationSpinner && previewSession != null && previewView != null && previewView.isAvailable()) {
                    restartPreview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        resolutionSpinner.setOnItemSelectedListener(listener);
        modeSpinner.setOnItemSelectedListener(listener);
        compensationSpinner.setOnItemSelectedListener(listener);
    }

    private void startPreviewThread() {
        if (previewThread != null) {
            return;
        }
        previewThread = new HandlerThread("OfflineCCTV-Preview");
        previewThread.start();
        previewHandler = new Handler(previewThread.getLooper());
    }

    private void stopPreviewThread() {
        if (previewThread != null) {
            previewThread.quitSafely();
            previewThread = null;
            previewHandler = null;
        }
    }

    private void startPreviewIfReady() {
        if (running || stealth || previewView == null || !previewView.isAvailable() || !hasCameraPermission()) {
            return;
        }
        if (previewCamera != null) {
            return;
        }
        startPreviewThread();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            previewCameraId = chooseBackCamera(manager);
            manager.openCamera(previewCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    previewCamera = camera;
                    createPreviewSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    previewCamera = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    previewCamera = null;
                    statusText.setText("Preview camera error " + error);
                }
            }, previewHandler);
        } catch (CameraAccessException | RuntimeException ex) {
            statusText.setText("Preview gagal: " + ex.getMessage());
        }
    }

    private void createPreviewSession() {
        if (previewCamera == null || previewView == null || !previewView.isAvailable()) {
            return;
        }
        try {
            SurfaceTexture texture = previewView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);
            previewCamera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    previewSession = session;
                    updatePreviewRequest(surface);
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    statusText.setText("Preview configure failed");
                }
            }, previewHandler);
        } catch (CameraAccessException | RuntimeException ex) {
            statusText.setText("Preview session gagal: " + ex.getMessage());
        }
    }

    private void updatePreviewRequest(Surface surface) {
        try {
            CctvSettings settings = currentSettings();
            CaptureRequest.Builder request = previewCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            ExposureApplier.apply(request, settings);
            previewSession.setRepeatingRequest(request.build(), null, previewHandler);
            statusText.setText("Preview exposure: " + settings.exposureCompensation);
        } catch (CameraAccessException | RuntimeException ex) {
            statusText.setText("Preview exposure gagal: " + ex.getMessage());
        }
    }

    private String chooseBackCamera(CameraManager manager) throws CameraAccessException {
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

    private void closePreview() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
        if (previewCamera != null) {
            previewCamera.close();
            previewCamera = null;
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (!hasCameraPermission()) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void saveRawSettings() {
        if (intervalInput == null || storageInput == null || resolutionSpinner == null
                || modeSpinner == null || compensationSpinner == null) {
            return;
        }
        prefs().edit()
                .putString("interval", intervalInput.getText().toString())
                .putString("storage_gb", storageInput.getText().toString())
                .putString("resolution", resolutionSpinner.getSelectedItem().toString())
                .putString("mode", modeSpinner.getSelectedItem().toString())
                .putString("compensation", compensationSpinner.getSelectedItem().toString())
                .apply();
    }

    private String loadString(String key, String fallback) {
        return prefs().getString(key, fallback);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void selectSpinner(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
