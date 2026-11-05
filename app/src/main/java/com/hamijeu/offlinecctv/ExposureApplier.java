package com.hamijeu.offlinecctv;

import android.hardware.camera2.CaptureRequest;

public final class ExposureApplier {
    private ExposureApplier() {
    }

    public static void apply(CaptureRequest.Builder request, CctvSettings settings) {
        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, settings.exposureCompensation);
    }
}
