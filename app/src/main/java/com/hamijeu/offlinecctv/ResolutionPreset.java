package com.hamijeu.offlinecctv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public enum ResolutionPreset {
    LOW,
    MEDIUM,
    HIGH;

    private static final long LOW_TARGET_AREA = 640L * 480L;
    private static final long MEDIUM_TARGET_AREA = 1280L * 720L;

    public SizeOption select(List<SizeOption> supportedSizes) {
        if (supportedSizes == null || supportedSizes.isEmpty()) {
            throw new IllegalArgumentException("No supported camera sizes");
        }

        List<SizeOption> sorted = new ArrayList<>(supportedSizes);
        sorted.sort(Comparator.comparingLong(SizeOption::area));

        if (this == LOW) {
            for (SizeOption size : sorted) {
                if (size.area() >= LOW_TARGET_AREA) {
                    return size;
                }
            }
            return sorted.get(sorted.size() - 1);
        }
        if (this == HIGH) {
            return sorted.get(sorted.size() - 1);
        }

        for (SizeOption size : sorted) {
            if (size.area() >= MEDIUM_TARGET_AREA) {
                return size;
            }
        }
        return sorted.get(sorted.size() / 2);
    }

    public static final class SizeOption {
        public final int width;
        public final int height;

        public SizeOption(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public long area() {
            return (long) width * height;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SizeOption)) {
                return false;
            }
            SizeOption that = (SizeOption) other;
            return width == that.width && height == that.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }
}
