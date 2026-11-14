package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipInputStream;

/** Standalone entrypoint for testing the installer without starting Minecraft. */
public final class NativeDependencySmokeTest {
    public static void main(String[] args) throws Exception {
        Path cache = Path.of(args[1]).toAbsolutePath();
        var home = GraalPyRuntimeResources.prepare(Path.of(args[0]), cache.resolve("runtime")).orElseThrow();
        Path pip = cache.resolve("pip");
        try (var stream = NativeDependencySmokeTest.class.getResourceAsStream("/pip.whl");
             var zip = new ZipInputStream(stream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path file = pip.resolve(entry.getName()).normalize();
                if (!file.startsWith(pip)) throw new IllegalStateException("Unsafe pip archive");
                if (entry.isDirectory()) Files.createDirectories(file);
                else {
                    Files.createDirectories(file.getParent());
                    Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        try (Context context = home.configure(Context.newBuilder("python").allowAllAccess(true)
                .allowExperimentalOptions(true).option("python.PosixModuleBackend", System.getProperty("pyfish.test.posixBackend", "java"))
                .option("engine.WarnInterpreterOnly", "false")).build()) {
            if (args.length > 2 && (args[2].equals("original-libraries") || args[2].equals("interop-diagnostics"))) {
                String source;
                try (var stream = NativeDependencySmokeTest.class.getResourceAsStream("/python/native_dependencies.py")) {
                    source = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                var factory = context.eval(org.graalvm.polyglot.Source.newBuilder("python", source, "installer.py")
                        .option("python.NewGlobals", "true").buildLiteral());
                String checksResource = args[2].equals("interop-diagnostics") ? "interop_diagnostics" : "original_libraries";
                try (var stream = NativeDependencySmokeTest.class.getResourceAsStream("/python/" + checksResource + ".py")) {
                    String checks = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (args[2].equals("interop-diagnostics")) {
                        try (var originals = NativeDependencySmokeTest.class.getResourceAsStream("/python/original_libraries.py")) {
                            checks = new String(originals.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) + "\n" + checks;
                        }
                    }
                    org.graalvm.polyglot.proxy.ProxyExecutable installer = arguments -> {
                        var values = arguments[0];
                        var command = new java.util.ArrayList<String>();
                        for (long i = 0; i < values.getArraySize(); i++) command.add(values.getArrayElement(i).asString());
                        return PythonPipRunner.run(context, command, pip);
                    };
                    context.eval(org.graalvm.polyglot.Source.newBuilder("python", checks, checksResource + ".py")
                            .option("python.NewGlobals", "true").buildLiteral())
                            .execute(factory, installer, cache.toString(), pip.toString(), GraalPyRuntimeResources.GRAALPY_VERSION);
                }
            } else {
                NativeDependencyChecks.run(context, cache, pip);
            }
        }
    }
}
