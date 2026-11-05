package com.hamijeu.offlinecctv;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StorageCleaner {
    private StorageCleaner() {
    }

    public static void clean(File folder, long limitBytes) {
        if (folder == null || limitBytes < 0 || !folder.isDirectory()) {
            return;
        }

        File[] filesArray = folder.listFiles(File::isFile);
        if (filesArray == null) {
            return;
        }

        List<File> files = new ArrayList<>();
        long totalBytes = 0;
        for (File file : filesArray) {
            files.add(file);
            totalBytes += file.length();
        }

        files.sort(Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (totalBytes <= limitBytes) {
                break;
            }
            long length = file.length();
            if (file.delete()) {
                totalBytes -= length;
            }
        }
    }
}
