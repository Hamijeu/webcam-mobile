package com.hamijeu.offlinecctv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

public class StorageCleanerTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void cleanDeletesOldestFilesUntilUnderLimit() throws Exception {
        File cctvFolder = folder.newFolder("OfflineCCTV");
        File oldFile = writeFile(cctvFolder, "old.jpg", 60, 1000);
        File middleFile = writeFile(cctvFolder, "middle.jpg", 60, 2000);
        File newFile = writeFile(cctvFolder, "new.jpg", 60, 3000);

        StorageCleaner.clean(cctvFolder, 120);

        assertFalse(oldFile.exists());
        assertTrue(middleFile.exists());
        assertTrue(newFile.exists());
    }

    @Test
    public void cleanOnlyDeletesInsideProvidedFolder() throws Exception {
        File cctvFolder = folder.newFolder("OfflineCCTV");
        File outsideFolder = folder.newFolder("Other");
        File cctvFile = writeFile(cctvFolder, "old.jpg", 80, 1000);
        File outsideFile = writeFile(outsideFolder, "outside.jpg", 80, 1000);

        StorageCleaner.clean(cctvFolder, 10);

        assertFalse(cctvFile.exists());
        assertTrue(outsideFile.exists());
    }

    private static File writeFile(File parent, String name, int size, long lastModified) throws Exception {
        File file = new File(parent, name);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            byte[] bytes = new byte[size];
            stream.write(bytes);
        }
        if (!file.setLastModified(lastModified)) {
            throw new IllegalStateException("Failed to set last modified");
        }
        return file;
    }
}
