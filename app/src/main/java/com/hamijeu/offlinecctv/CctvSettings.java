package com.hamijeu.offlinecctv;

import java.math.BigDecimal;

public final class CctvSettings {
    public static final String EXTRA_INTERVAL_SECONDS = "interval_seconds";
    public static final String EXTRA_STORAGE_LIMIT_BYTES = "storage_limit_bytes";
    public static final String EXTRA_RESOLUTION_PRESET = "resolution_preset";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_EXPOSURE_COMPENSATION = "exposure_compensation";

    public enum Mode {
        STEALTH,
        LOCK_SCREEN
    }

    public final int intervalSeconds;
    public final long storageLimitBytes;
    public final ResolutionPreset resolutionPreset;
    public final Mode mode;
    public final int exposureCompensation;

    public CctvSettings(
            int intervalSeconds,
            long storageLimitBytes,
            ResolutionPreset resolutionPreset,
            Mode mode,
            int exposureCompensation
    ) {
        this.intervalSeconds = intervalSeconds;
        this.storageLimitBytes = storageLimitBytes;
        this.resolutionPreset = resolutionPreset;
        this.mode = mode;
        this.exposureCompensation = exposureCompensation;
    }

    public static CctvSettings parse(
            String intervalSecondsText,
            String storageLimitGbText,
            String resolutionPresetText,
            String modeText,
            String exposureCompensationText
    ) {
        int intervalSeconds = parseInt(intervalSecondsText, "Interval harus angka");
        if (intervalSeconds < 2) {
            throw new IllegalArgumentException("Interval minimal 2 detik");
        }

        BigDecimal gb = parseDecimal(storageLimitGbText);
        if (gb.compareTo(new BigDecimal("0.1")) < 0) {
            throw new IllegalArgumentException("Batas storage minimal 0.1 GB");
        }
        long limitBytes = gb.multiply(BigDecimal.valueOf(1024L * 1024L * 1024L)).longValue();

        ResolutionPreset preset = ResolutionPreset.valueOf(resolutionPresetText);
        Mode mode = Mode.valueOf(modeText);
        int compensation = parseInt(exposureCompensationText, "Exposure compensation harus angka");

        if (compensation < -4 || compensation > 4) {
            throw new IllegalArgumentException("Exposure compensation harus -4 sampai 4");
        }

        return new CctvSettings(intervalSeconds, limitBytes, preset, mode, compensation);
    }

    private static int parseInt(String text, String message) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private static BigDecimal parseDecimal(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Batas storage harus angka GB");
        }
    }
}
