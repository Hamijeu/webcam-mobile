package com.hamijeu.offlinecctv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;

public class PhotoNamerTest {
    @Test
    public void nameUsesSortableTimestampAndJpgExtension() {
        String name = PhotoNamer.name(Instant.parse("2026-07-12T09:08:07.123Z"));

        assertEquals("IMG_20260712_090807_123.jpg", name);
        assertTrue(name.endsWith(".jpg"));
    }
}
