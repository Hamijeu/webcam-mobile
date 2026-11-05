package com.hamijeu.offlinecctv;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class PhotoNamer {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("'IMG_'yyyyMMdd_HHmmss_SSS'.jpg'")
                    .withZone(ZoneOffset.UTC);

    private PhotoNamer() {
    }

    public static String name(Instant instant) {
        return FORMATTER.format(instant);
    }
}
