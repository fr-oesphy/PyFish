package fr.oesphy.pyfish.runtime;

import fr.oesphy.pyfish.PyFish;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PythonDependencyManager {
    private static final Logger LOGGER = PyFish.LOGGER;
    private final Path libsRoot;
    private final java.util.Map<String, String> pending = new java.util.LinkedHashMap<>();

    public PythonDependencyManager() {
        // Keep third-party packages out of the game and loader classpaths. Each mod gets a
        // target directory that can be added to GraalPy's sys.path independently.
        this.libsRoot = Paths.get("pyfish", "libs");
        try {
            Files.createDirectories(this.libsRoot);
        } catch (IOException e) {
            LOGGER.error("[PyFish] Could not create libs directory: {}", e.getMessage());
        }
    }

    /**
     * Materializes a mod's requirements into its private target directory. The hash marker
     * is written only after pip succeeds, so interrupted installations are retried.
     */
    public Path installDependencies(String modId, String requirementsContent) {
        if (requirementsContent == null || requirementsContent.isBlank()) return null;
        pending.put(modId, requirementsContent);
        return libsRoot.resolve(modId);
    }

    public void prepareDependencies(Context context) {
        for (var entry : pending.entrySet()) {
            if (installDependenciesNow(context, entry.getKey(), entry.getValue()) == null) {
                throw new IllegalStateException("Could not install Python dependencies for " + entry.getKey());
            }
        }
        pending.clear();
    }

    private Path installDependenciesNow(Context context, String modId, String requirementsContent) {
        if (requirementsContent == null || requirementsContent.isBlank()) {
            return null;
        }

        try {
            String hash = requirementsHash(requirementsContent, GraalPyRuntimeResources.GRAALPY_VERSION,
                    System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
            Path modLibPath = libsRoot.resolve(modId);
            Path hashFile = modLibPath.resolve(".hash");

            // A matching requirements file does not make native wheels portable across
            // interpreter upgrades or platforms.
            if (Files.exists(modLibPath) && Files.exists(hashFile)) {
                String existingHash = Files.readString(hashFile);
                if (existingHash.trim().equals(hash)) {
                    LOGGER.info("[PyFish] Dependencies for '{}' are up to date.", modId);
                    return modLibPath;
                }
            }

            LOGGER.info("[PyFish] Installing dependencies for '{}'...", modId);

            // pip --target adds and overwrites files but does not reliably remove packages
            // dropped from requirements, so changed declarations start from a clean target.
            if (Files.exists(modLibPath)) {
                deleteDirectory(modLibPath.toFile());
            }
            Files.createDirectories(modLibPath);

            boolean success = installWithPip(context, modId, modLibPath, requirementsContent);

            if (success) {
                Files.writeString(hashFile, hash);
                LOGGER.info("[PyFish] Dependencies for '{}' installed successfully.", modId);
                return modLibPath;
            } else {
                LOGGER.error("[PyFish] Failed to install dependencies for '{}'.", modId);
                return null;
            }

        } catch (Exception e) {
            LOGGER.error("[PyFish] Error managing dependencies for '{}': {}", modId, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Path ensurePipExtracted() {
        Path pipDir = libsRoot.resolve("pip_extracted").resolve(GraalPyRuntimeResources.GRAALPY_VERSION);
        if (!Files.exists(pipDir)) {
            try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("pip.whl")) {
                if (is == null) {
                    LOGGER.error("[PyFish] pip.whl not found in resources!");
                    return null;
                }
                Files.createDirectories(pipDir);
                try (ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        Path entryPath = pipDir.resolve(entry.getName());
                        // Wheel paths are still archive input; do not allow a malformed bundled
                        // wheel to escape the extraction directory.
                        if (!entryPath.normalize().startsWith(pipDir.normalize())) {
                            continue;
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(entryPath);
                        } else {
                            Files.createDirectories(entryPath.getParent());
                            Files.deleteIfExists(entryPath);
                            Files.copy(zis, entryPath);
                        }
                        zis.closeEntry();
                    }
                }
                LOGGER.info("[PyFish] Extracted pip.whl to {}", pipDir);
            } catch (IOException e) {
                LOGGER.error("[PyFish] Failed to extract pip.whl", e);
                return null;
            }
        }
        return pipDir;
    }

    private boolean installWithPip(Context context, String modId, Path targetDir, String requirementsContent) {
        Path pipDir = ensurePipExtracted();
        if (pipDir == null) {
            LOGGER.error("[PyFish] Cannot install dependencies because pip extraction failed.");
            return false;
        }

        try {

            // Let pip parse the original file. Reconstructing argv entries here would subtly
            // break markers, continuation lines and index options.
            Path reqFile = targetDir.resolve("requirements.txt");
            Files.writeString(reqFile, requirementsContent);

            LOGGER.info("[PyFish] Running pip install target={} requirements={}", targetDir, reqFile);

            return PythonPipRunner.run(context, List.of("pip", "install", "--no-cache-dir",
                    "--disable-pip-version-check", "--no-input", "--isolated", "--target",
                    targetDir.toAbsolutePath().toString(), "--requirement", reqFile.toAbsolutePath().toString(),
                    "--no-warn-script-location"), pipDir);

        } catch (Exception e) {
            LOGGER.error("[PyFish] Pip installation failed: " + e.getMessage());
            return false;
        }
    }

    static String requirementsHash(String content, String runtimeVersion, String platform) throws Exception {
        return computeHash(runtimeVersion + "\n" + platform + "\n" + content);
    }

    private static String computeHash(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    // pip writes progress to process streams; line buffering prevents partial messages from
    // interleaving with loader logs.
    private static class LoggingOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final boolean isError;

        public LoggingOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                String message = buffer.toString("UTF-8");
                if (!message.isBlank()) {
                    if (isError) {
                        LOGGER.error("[PyFish/Pip] {}", message);
                    } else {
                        LOGGER.info("[PyFish/Pip] {}", message);
                    }
                }
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }
    }
}
