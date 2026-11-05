package com.hamijeu.offlinecctv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CctvSettingsTest {
    @Test
    public void parseAcceptsValidValues() {
        CctvSettings settings = CctvSettings.parse("5", "1.25", "MEDIUM", "STEALTH", "2");

        assertEquals(5, settings.intervalSeconds);
        assertEquals(1342177280L, settings.storageLimitBytes);
        assertEquals(ResolutionPreset.MEDIUM, settings.resolutionPreset);
        assertEquals(CctvSettings.Mode.STEALTH, settings.mode);
        assertEquals(2, settings.exposureCompensation);
    }

    @Test
    public void parseRejectsIntervalBelowTwoSeconds() {
        try {
            CctvSettings.parse("1", "1", "LOW", "STEALTH", "0");
            fail("Expected invalid interval");
        } catch (IllegalArgumentException ex) {
            assertEquals("Interval minimal 2 detik", ex.getMessage());
        }
    }

    @Test
    public void parseRejectsStorageBelowPointOneGb() {
        try {
            CctvSettings.parse("5", "0.05", "LOW", "STEALTH", "0");
            fail("Expected invalid storage limit");
        } catch (IllegalArgumentException ex) {
            assertEquals("Batas storage minimal 0.1 GB", ex.getMessage());
        }
    }

    @Test
    public void parseRejectsExposureCompensationOutsidePresetRange() {
        try {
            CctvSettings.parse("5", "1", "LOW", "STEALTH", "8");
            fail("Expected invalid compensation");
        } catch (IllegalArgumentException ex) {
            assertEquals("Exposure compensation harus -4 sampai 4", ex.getMessage());
        }
    }
}
