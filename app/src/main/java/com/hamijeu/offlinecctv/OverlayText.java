package com.hamijeu.offlinecctv;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class OverlayText {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OverlayText() {
    }

    static String timestamp(Instant capturedAt, ZoneId zoneId) {
        return FORMATTER.withZone(zoneId).format(capturedAt);
    }

    static String timestamp(Instant capturedAt) {
        return timestamp(capturedAt, ZoneId.systemDefault());
    }
}
