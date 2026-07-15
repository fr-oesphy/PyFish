package fr.oesphy.pyfish.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public record PyFishPyzMod(Path archivePath, PyFishPyzManifest manifest, String requirements, String mainCode) {
    // Treat archives like untrusted input even though their Python code is trusted at run
    // time. These limits prevent accidental zip bombs from exhausting startup memory.
    private static final int MAX_ENTRY_COUNT = 10_000;
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 128L * 1024L * 1024L;
    private static final long MAX_MANIFEST_SIZE = 64L * 1024L;
    private static final long MAX_REQUIREMENTS_SIZE = 1024L * 1024L;
    private static final long MAX_MAIN_SIZE = 8L * 1024L * 1024L;

    public static PyFishPyzMod inspect(Path archivePath) throws IOException {
        Path normalizedPath = archivePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IOException("Archive does not exist or is not a regular file");
        }
        if (!normalizedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pyz")) {
            throw new IOException("Archive filename must end with .pyz");
        }

        try (ZipFile archive = new ZipFile(normalizedPath.toFile(), StandardCharsets.UTF_8)) {
            validateEntries(archive);

            ZipEntry manifestEntry = optionalRootEntry(archive, PyFishPyzManifest.MANIFEST_NAME);
            if (manifestEntry == null) {
                manifestEntry = requiredRootEntry(archive, PyFishPyzManifest.LEGACY_MANIFEST_NAME);
            }
            ZipEntry mainEntry = requiredRootEntry(archive, "__main__.py");

            String manifestJson = readUtf8(archive, manifestEntry, MAX_MANIFEST_SIZE, manifestEntry.getName());
            PyFishPyzManifest manifest;
            try {
                manifest = PyFishPyzManifest.parse(manifestJson);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }

            ZipEntry requirementsEntry = archive.getEntry("requirements.txt");
            String requirements = requirementsEntry == null
                    ? ""
                    : readUtf8(archive, requirementsEntry, MAX_REQUIREMENTS_SIZE, "requirements.txt");
            String mainCode = readUtf8(archive, mainEntry, MAX_MAIN_SIZE, "__main__.py");

            return new PyFishPyzMod(normalizedPath, manifest, requirements, mainCode);
        }
    }

    private static void validateEntries(ZipFile archive) throws IOException {
        Enumeration<? extends ZipEntry> entries = archive.entries();
        Set<String> names = new HashSet<>();
        int entryCount = 0;
        long totalSize = 0L;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            entryCount++;
            if (entryCount > MAX_ENTRY_COUNT) {
                throw new IOException("Archive contains too many entries (maximum " + MAX_ENTRY_COUNT + ")");
            }
            if (!names.add(name)) {
                throw new IOException("Archive contains duplicate entry '" + name + "'");
            }
            if (isUnsafeEntryName(name)) {
                throw new IOException("Archive contains unsafe entry path '" + name + "'");
            }

            long size = entry.getSize();
            // ZIP entries may report -1 here. readUtf8 enforces its own streamed limit for
            // every file PyFish actually consumes.
            if (size > 0L) {
                totalSize += size;
                if (totalSize > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                    throw new IOException("Archive is too large after decompression (maximum 128 MiB)");
                }
            }
        }
    }

    private static boolean isUnsafeEntryName(String name) {
        // Backslashes are rejected instead of normalized so the same archive cannot resolve
        // differently on Windows and Unix hosts.
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.indexOf('\\') >= 0) {
            return true;
        }
        for (String part : name.split("/")) {
            if (part.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static ZipEntry requiredRootEntry(ZipFile archive, String name) throws IOException {
        ZipEntry entry = optionalRootEntry(archive, name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Archive is missing required root file '" + name + "'");
        }
        return entry;
    }

    private static ZipEntry optionalRootEntry(ZipFile archive, String name) {
        ZipEntry entry = archive.getEntry(name);
        return entry != null && !entry.isDirectory() ? entry : null;
    }

    private static String readUtf8(ZipFile archive, ZipEntry entry, long maximumSize, String label)
            throws IOException {
        if (entry.isDirectory()) {
            throw new IOException(label + " must be a file");
        }
        if (entry.getSize() > maximumSize) {
            throw new IOException(label + " is too large");
        }

        try (InputStream input = archive.getInputStream(entry);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maximumSize) {
                    throw new IOException(label + " is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}