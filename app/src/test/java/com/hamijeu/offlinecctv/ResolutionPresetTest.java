package com.hamijeu.offlinecctv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ResolutionPresetTest {
    @Test
    public void selectPicksExpectedSizes() {
        List<ResolutionPreset.SizeOption> sizes = Arrays.asList(
                new ResolutionPreset.SizeOption(4160, 3120),
                new ResolutionPreset.SizeOption(1920, 1080),
                new ResolutionPreset.SizeOption(1280, 720),
                new ResolutionPreset.SizeOption(640, 480),
                new ResolutionPreset.SizeOption(160, 120)
        );

        assertEquals(new ResolutionPreset.SizeOption(640, 480), ResolutionPreset.LOW.select(sizes));
        assertEquals(new ResolutionPreset.SizeOption(1280, 720), ResolutionPreset.MEDIUM.select(sizes));
        assertEquals(new ResolutionPreset.SizeOption(4160, 3120), ResolutionPreset.HIGH.select(sizes));
    }
}
