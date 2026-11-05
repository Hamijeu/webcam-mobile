package com.hamijeu.offlinecctv;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.Test;

public class OverlayTextTest {
    @Test
    public void timestampUsesDeviceLocalDateAndTime() {
        String timestamp = OverlayText.timestamp(
                Instant.parse("2026-07-12T09:08:07Z"),
                ZoneId.of("Asia/Bangkok")
        );

        assertEquals("2026-07-12 16:08:07", timestamp);
    }
}
