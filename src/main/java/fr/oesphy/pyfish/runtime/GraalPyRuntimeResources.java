package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Materializes GraalPy's internal language home outside the mod classloader.
 * NeoForge's production classloader can load the language classes while hiding
 * their internal resources, so relying on GraalPy's automatic lookup is unsafe.
 */
public final class GraalPyRuntimeResources {
    public static final String GRAALPY_VERSION = "25.0.1";

    private static final String RESOURCE_DIRECTORY = "META-INF/resources";
    private static final String COMPLETE_MARKER = ".complete";

    private GraalPyRuntimeResources() {
    }

    public static Optional<RuntimeHome> prepare(Path resourceRoot) throws IOException {
        Path cacheRoot = Path.of(".pyfish", "runtime-cache").toAbsolutePath().normalize();
        return prepare(resourceRoot, cacheRoot);
    }

    public static Optional<RuntimeHome> prepare(Path resourceRoot, Path cacheRoot) throws IOException {
        if (resourceRoot == null) {
            return Optional.empty();
        }

        if (Files.isRegularFile(resourceRoot)) {
            try (FileSystem archive = FileSystems.newFileSystem(resourceRoot, Map.of())) {
                return prepare(archive.getPath("/"), cacheRoot);
            }
        }

        Path resources = resourceRoot.resolve(RESOURCE_DIRECTORY);
        if (!Files.isRegularFile(resources.resolve("libpython.files"))
                || !Files.isRegularFile(resources.resolve("libgraalpy.files"))) {
            // Development runs keep GraalPy on Gradle's classpath instead of inside
            // the mod output, where the normal internal-resource lookup is reliable.
            return Optional.empty();
        }

        PlatformLayout platform = PlatformLayout.current();
        String fingerprint = fingerprint(resources, platform);
        Path target = cacheRoot
                .resolve("graalpy-" + GRAALPY_VERSION)
                .resolve(platform.cacheKey() + "-" + fingerprint.substring(0, 16));
        RuntimeHome runtimeHome = platform.runtimeHome(target);

        if (isComplete(runtimeHome, fingerprint)) {
            return Optional.of(runtimeHome);
        }

        Files.createDirectories(target.getParent());
        Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
        try (FileChannel lockChannel = FileChannel.open(
                lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = lockChannel.lock()) {
            // Another Minecraft process may have completed the same cache while this
            // process was waiting for the lock.
            if (isComplete(runtimeHome, fingerprint)) {
                return Optional.of(runtimeHome);
            }

            Path staging = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
            try {
                extract(resources, staging, platform);
                RuntimeHome stagedHome = platform.runtimeHome(staging);
                validate(stagedHome);
                Files.writeString(staging.resolve(COMPLETE_MARKER), fingerprint, StandardCharsets.US_ASCII,
                        StandardOpenOption.CREATE_NEW);

                if (Files.exists(target)) {
                    deleteTree(target);
                }
                moveDirectory(staging, target);
            } finally {
                deleteTree(staging);
            }
        }

        validate(runtimeHome);
        return Optional.of(runtimeHome);
    }

    private static void extract(Path resources, Path staging, PlatformLayout platform) throws IOException {
        Files.createDirectories(staging);
        copyManifestEntries(resources, "libpython.files", "libpython", staging.resolve(platform.stdlibDirectory()));
        copyManifestEntries(resources, "libgraalpy.files", "libgraalpy", staging.resolve(platform.coreDirectory()));
        copyManifestEntries(resources, "include.files", "include", staging.resolve(platform.includeDirectory()));

        String nativePrefix = platform.resourceDirectory();
        copyManifestEntries(resources, nativePrefix + "/native.files", nativePrefix, staging);
    }

    private static void copyManifestEntries(Path resources, String manifestName, String sourcePrefix, Path targetRoot)
            throws IOException {
        Path manifest = resources.resolve(manifestName);
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("Missing GraalPy resource manifest: " + manifestName);
        }

        String normalizedPrefix = RESOURCE_DIRECTORY + "/" + sourcePrefix + "/";
        for (String rawLine : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String entry = rawLine.split("\\s+=\\s+", 2)[0].trim().replace('\\', '/');
            if (entry.isEmpty()) {
                continue;
            }
            if (!entry.startsWith(normalizedPrefix)) {
                throw new IOException("Unexpected GraalPy resource path in " + manifestName + ": " + entry);
            }

            Path relative = Path.of(entry.substring(normalizedPrefix.length())).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Unsafe GraalPy resource path: " + entry);
            }

            Path source = resources.resolve(entry.substring((RESOURCE_DIRECTORY + "/").length()));
            if (!Files.isRegularFile(source)) {
                // Launchers from the embedded distribution are deliberately excluded
                // from PyFish's JAR. The embedded context never executes them.
                if (entry.endsWith(".bat") || entry.endsWith(".exe")) {
                    continue;
                }
                throw new IOException("Missing GraalPy resource declared by " + manifestName + ": " + entry);
            }
            Path destination = targetRoot.resolve(relative).normalize();
            if (!destination.startsWith(targetRoot.normalize())) {
                throw new IOException("GraalPy resource escaped its cache directory: " + entry);
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fingerprint(Path resources, PlatformLayout platform) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }

        for (String file : List.of(
                "libpython.sha256",
                "libgraalpy.sha256",
                "include.sha256",
                platform.resourceDirectory() + "/native.sha256")) {
            Path checksum = resources.resolve(file);
            if (!Files.isRegularFile(checksum)) {
                throw new IOException("Missing GraalPy resource checksum: " + file);
            }
            digest.update(Files.readAllBytes(checksum));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isComplete(RuntimeHome runtimeHome, String fingerprint) {
        try {
            Path marker = runtimeHome.root().resolve(COMPLETE_MARKER);
            return Files.isRegularFile(marker)
                    && Files.readString(marker, StandardCharsets.US_ASCII).equals(fingerprint)
                    && hasRequiredFiles(runtimeHome);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void validate(RuntimeHome runtimeHome) throws IOException {
        if (!hasRequiredFiles(runtimeHome)) {
            throw new IOException("Extracted GraalPy runtime is incomplete: " + runtimeHome.root());
        }
    }

    private static boolean hasRequiredFiles(RuntimeHome runtimeHome) {
        return Files.isRegularFile(runtimeHome.coreHome().resolve("__graalpython__.py"))
                && Files.isRegularFile(runtimeHome.stdlibHome().resolve("json/__init__.py"))
                && Files.isRegularFile(runtimeHome.stdlibHome().resolve("threading.py"))
                && Files.isRegularFile(runtimeHome.stdlibHome().resolve("queue.py"))
                && Files.isRegularFile(runtimeHome.stdlibHome().resolve("ast.py"));
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record RuntimeHome(Path root, Path coreHome, Path stdlibHome) {
        public Context.Builder configure(Context.Builder builder) {
            String rootPath = root.toAbsolutePath().toString();
            return builder
                    .option("python.CoreHome", coreHome.toAbsolutePath().toString())
                    .option("python.StdLibHome", stdlibHome.toAbsolutePath().toString())
                    .option("python.SysPrefix", rootPath)
                    .option("python.SysBasePrefix", rootPath)
                    .option("python.CAPI", coreHome.toAbsolutePath().toString());
        }
    }

    private record PlatformLayout(
            String os,
            String architecture,
            String coreDirectory,
            String stdlibDirectory,
            String includeDirectory) {

        private static PlatformLayout current() {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String os;
            if (osName.contains("win")) {
                os = "windows";
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                os = "darwin";
            } else if (osName.contains("linux")) {
                os = "linux";
            } else {
                throw new IllegalStateException("Unsupported operating system for GraalPy: " + osName);
            }

            String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String architecture;
            if (archName.equals("amd64") || archName.equals("x86_64")) {
                architecture = "amd64";
            } else if (archName.equals("aarch64") || archName.equals("arm64")) {
                architecture = "aarch64";
            } else {
                throw new IllegalStateException("Unsupported architecture for GraalPy: " + archName);
            }

            if (os.equals("windows")) {
                return new PlatformLayout(os, architecture, "lib-graalpython", "Lib", "Include");
            }
            return new PlatformLayout(os, architecture, "lib/graalpy25.0", "lib/python3.12", "include/python3.12");
        }

        private String resourceDirectory() {
            return os + "/" + architecture;
        }

        private String cacheKey() {
            return os + "-" + architecture;
        }

        private RuntimeHome runtimeHome(Path root) {
            return new RuntimeHome(root, root.resolve(coreDirectory), root.resolve(stdlibDirectory));
        }
    }
}
