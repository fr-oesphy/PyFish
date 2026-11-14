package fr.oesphy.pyfish.runtime;

import com.google.gson.Gson;
import fr.oesphy.pyfish.PyFish;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PythonDependencyManager {
    private static final Logger LOGGER = PyFish.LOGGER;
    private final Path libsRoot;
    private final Map<String, String> requirements = new TreeMap<>();
    private boolean prepared;
    private RuntimeException declarationError;

    public PythonDependencyManager() {
        this(Path.of("pyfish", "libs"));
    }

    PythonDependencyManager(Path libsRoot) {
        this.libsRoot = libsRoot.toAbsolutePath().normalize();
    }

    /** Records declarations before any script is allowed to import third-party packages. */
    public synchronized Path installDependencies(String modId, String content) {
        if (content == null || content.isBlank()) {
            if (prepared && requirements.containsKey(modId)) {
                throw new IllegalStateException("Python requirements removed for " + modId + "; restart Minecraft");
            }
            return null;
        }
        String normalized = content.replace("\r\n", "\n").strip();
        if (normalized.equals(requirements.get(modId))) return null;
        if (prepared) {
            throw new IllegalStateException("Python requirements changed for " + modId
                    + "; restart Minecraft. The active native environment has not been modified.");
        }
        String previous = requirements.putIfAbsent(modId, normalized);
        if (previous != null) {
            declarationError = new IllegalStateException("Different Python requirements declared for mod " + modId);
            throw declarationError;
        }
        return null;
    }

    public synchronized void prepareDependencies(Context context) {
        if (declarationError != null) throw declarationError;
        if (prepared) return;
        if (requirements.isEmpty()) {
            prepared = true;
            return;
        }

        Value installation = null;
        try {
            Files.createDirectories(libsRoot);
            // Multiple Minecraft instances may share a profile. Only installations are locked;
            // completed generations remain immutable while native DLLs are mapped.
            try (FileChannel channel = FileChannel.open(libsRoot.resolve(".installation.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ignored = channel.lock()) {
                Path pipDirectory = ensurePipExtracted();
                String declarations = new Gson().toJson(requirements);
                String requestKey = requirementsHash(declarations,
                        GraalPyRuntimeResources.GRAALPY_VERSION + "/binary-only-v1/" + pipDirectory.getFileName(),
                        System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
                String helper;
                try (InputStream stream = getClass().getResourceAsStream("/python/native_dependencies.py")) {
                    if (stream == null) throw new IOException("Native dependency installer resource is missing");
                    helper = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                Source source = Source.newBuilder("python", helper, "pyfish-native-dependencies.py")
                        .option("python.NewGlobals", "true").buildLiteral();
                installation = context.eval(source).execute(libsRoot.toString(), declarations,
                        GraalPyRuntimeResources.GRAALPY_VERSION, pipDirectory.toString(), requestKey);
                boolean cached = installation.getMember("cached").asBoolean();
                if (!cached) {
                    LOGGER.info("[PyFish/Packages] Resolving GraalPy wheels for mods {}", requirements.keySet());
                    Value args = installation.invokeMember("arguments");
                    List<String> arguments = new ArrayList<>();
                    for (long index = 0; index < args.getArraySize(); index++) {
                        arguments.add(args.getArrayElement(index).asString());
                    }
                    if (!PythonPipRunner.run(context, arguments, pipDirectory)) {
                        throw new IllegalStateException("No compatible wheel set could be resolved for mods "
                                + requirements.keySet() + ". Check the pip messages above for version conflicts or "
                                + "unavailable GraalPy wheels. CPython wheels and automatic source builds are not supported.");
                    }
                    installation.invokeMember("commit");
                }
                String report = installation.invokeMember("activate").asString();
                String compatibility = installation.getMember("compatibility_notice").asString();
                if (!compatibility.isBlank()) LOGGER.warn("[PyFish/Packages] {}", compatibility);
                LOGGER.info("[PyFish/Packages] {} environment: {}", cached ? "Reusing" : "Installed", report);
                prepared = true;
            }
        } catch (Exception error) {
            if (installation != null) {
                try {
                    installation.invokeMember("abort");
                } catch (Exception cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            throw new IllegalStateException("Could not prepare Python packages for " + requirements.keySet()
                    + " with GraalPy " + GraalPyRuntimeResources.GRAALPY_VERSION
                    + " on " + System.getProperty("os.name") + "/" + System.getProperty("os.arch"), error);
        }
    }

    private Path ensurePipExtracted() throws Exception {
        byte[] wheel;
        try (InputStream stream = getClass().getResourceAsStream("/pip.whl")) {
            if (stream == null) throw new IOException("Bundled GraalPy pip wheel is missing");
            wheel = stream.readAllBytes();
        }
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wheel));
        Path directory = libsRoot.resolve("pip_extracted")
                .resolve(GraalPyRuntimeResources.GRAALPY_VERSION + "-" + checksum.substring(0, 16));
        Path marker = directory.resolve(".complete");
        if (Files.isRegularFile(marker) && Files.readString(marker).equals(checksum)
                && Files.isRegularFile(directory.resolve("pip/__main__.py"))) {
            return directory;
        }
        Files.createDirectories(directory);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(wheel))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = directory.resolve(entry.getName()).normalize();
                if (!target.startsWith(directory)) throw new IOException("Unsafe path in bundled pip wheel");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.writeString(marker, checksum, StandardCharsets.US_ASCII);
        return directory;
    }

    static String requirementsHash(String content, String runtimeVersion, String platform) throws Exception {
        byte[] bytes = (runtimeVersion + "\n" + platform + "\n" + content).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
