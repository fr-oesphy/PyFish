package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class NativeDependencyChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    static void run(Context context, Path cache, Path pipDirectory) throws Exception {
        String source;
        try (var stream = NativeDependencyChecks.class.getResourceAsStream("/python/native_dependencies.py")) {
            if (stream == null) throw new IllegalStateException("Native dependency helper is missing");
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Value factory = context.eval(Source.newBuilder("python", source, "native-dependencies-test.py")
                .option("python.NewGlobals", "true").buildLiteral());
        try (var stream = NativeDependencyChecks.class.getResourceAsStream("/python/websocket_compatibility.py")) {
            if (stream == null) throw new IllegalStateException("WebSocket compatibility tests are missing");
            String checks = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("python", source + "\n" + checks, "websocket-compatibility-test.py")
                    .option("python.NewGlobals", "true").buildLiteral()).execute();
        }
        Path wheels = cache.resolve("wheel house 'native'");
        Files.createDirectories(wheels);
        wheel(wheels, "pyfish_native_fixture", "1.0", Map.of("pyfish_native_fixture.py", "VALUE = 42\n"));
        wheel(wheels, "pyfish_native_fixture", "2.0", Map.of("pyfish_native_fixture.py", "VALUE = 84\n"));
        wheel(wheels, "pyfish_native_parent", "1.0", Map.of("pyfish_native_parent.py",
                "from pyfish_native_fixture import VALUE\n"), "pyfish-native-fixture==1.0");
        wheel(wheels, "pyfish_native_bad", "1.0", Map.of("fake.cp313-win_amd64.pyd", "not a GraalPy binary"));
        wheel(wheels, "pyfish_native_collision", "1.0", Map.of("pyfish_native_fixture.py", "VALUE = 99\n"));
        String offline = "--no-index\n--find-links \"" + wheels.toUri() + "\"\n";
        Map<String, String> requirements = Map.of("first", offline
                + "# Pip comments and continuations must retain their usual meaning.\n"
                + "pyfish-native-parent\\\n==1.0 ; python_version >= '3.13'  # fixture\n");
        Path root = cache.resolve("environments-test");
        Value first = plan(context, factory, root, requirements, pipDirectory);
        install(context, first, pipDirectory);
        Path target = Path.of(context.eval("python", "str").execute(first.getMember("target")).asString());
        check(Files.isRegularFile(target.resolve("manifest.json")), "No successful installation marker");
        Value cached = plan(context, factory, root, requirements, pipDirectory);
        check(cached.getMember("cached").asBoolean(), "Identical declarations did not reuse the cache");

        Files.delete(target.resolve("packages/pyfish_native_fixture.py"));
        Value repaired = plan(context, factory, root, requirements, pipDirectory);
        check(!repaired.getMember("cached").asBoolean(), "Damaged environment was reused");
        install(context, repaired, pipDirectory);
        check(Files.exists(target.resolve("packages/pyfish_native_fixture.py")), "Cache was not repaired");
        Files.writeString(target.resolve("manifest.json"), "[]");
        Value corruptManifest = plan(context, factory, root, requirements, pipDirectory);
        check(!corruptManifest.getMember("cached").asBoolean(), "Invalid cache manifest was reused");
        install(context, corruptManifest, pipDirectory);

        Value conflict = plan(context, factory, root, Map.of("first", requirements.get("first"),
                "second", offline + "pyfish-native-fixture==2.0\n"), pipDirectory);
        check(!pip(context, conflict, pipDirectory), "Incompatible mod requirements were accepted");
        conflict.invokeMember("abort");
        check(!Files.exists(Path.of(context.eval("python", "str").execute(conflict.getMember("target")).asString()).resolve("manifest.json")),
                "Failed resolution was committed");
        check(Files.exists(target.resolve("manifest.json")), "Failed resolution damaged a working environment");

        Value missing = plan(context, factory, root, Map.of("missing", offline + "pyfish-wheel-not-found==1\n"), pipDirectory);
        check(!pip(context, missing, pipDirectory), "Missing wheel unexpectedly resolved");
        missing.invokeMember("abort");
        Value bad = plan(context, factory, root, Map.of("bad", offline + "pyfish-native-bad==1.0\n"), pipDirectory);
        check(pip(context, bad, pipDirectory), "Could not create ABI-rejection fixture");
        boolean rejected = false;
        try { bad.invokeMember("commit"); } catch (Exception expected) { rejected = expected.getMessage().contains("CPython"); }
        finally { bad.invokeMember("abort"); }
        check(rejected, "CPython extension was accepted");

        Value collision = plan(context, factory, root, Map.of("collision", offline
                + "pyfish-native-fixture==1.0\npyfish-native-collision==1.0\n"), pipDirectory);
        check(pip(context, collision, pipDirectory), "Could not create import-file collision fixture");
        rejected = false;
        try { collision.invokeMember("commit"); } catch (Exception expected) { rejected = expected.getMessage().contains("conflict"); }
        finally { collision.invokeMember("abort"); }
        check(rejected, "Different distributions overwrote the same import file silently");

        for (String declaration : List.of("sample @ https://example.invalid/sample.tar.gz", "-e .", "--no-binary=:all:",
                "--only-binary=:none:", "-r other-requirements.txt")) {
            rejected = false;
            try { plan(context, factory, root, Map.of("source", declaration), pipDirectory); }
            catch (Exception expected) { rejected = true; }
            check(rejected, "Source installation was accepted: " + declaration);
        }
        repaired.invokeMember("activate");
        rejected = false;
        try {
            plan(context, factory, root, Map.of("first", "--index-url https://example.invalid/first\nsample",
                    "second", "--index-url https://example.invalid/second\nsample"), pipDirectory);
        } catch (Exception expected) { rejected = expected.getMessage().contains("indexes"); }
        check(rejected, "Conflicting package indexes were silently overridden");
        context.eval("python", "import pyfish_native_parent; assert pyfish_native_parent.VALUE == 42");
        System.out.println("Native dependency checks passed: install, cache, repair, conflicts, missing wheels, ABI and source policy");

        if (Boolean.getBoolean("pyfish.test.nativePackages")) {
            String wheelhouse = System.getProperty("pyfish.test.nativeWheelhouse", "");
            String declaration = wheelhouse.isEmpty() ? "numpy==2.4.4\n"
                    : "--no-index\n--find-links \"" + Path.of(wheelhouse).toAbsolutePath().toUri() + "\"\nnumpy==2.4.4\n";
            Value numpy = plan(context, factory, cache.resolve("real-native"), Map.of("numpy-probe", declaration), pipDirectory);
            install(context, numpy, pipDirectory);
            numpy.invokeMember("activate");
            context.eval("python", """
                    import numpy as np
                    import numpy._core._multiarray_umath as native
                    assert native.__file__.lower().endswith(('.pyd', '.so')), native.__file__
                    assert np.array_equal(np.array([[1, 2], [3, 4]]) @ np.array([[5, 6], [7, 8]]), [[19, 22], [43, 50]])
                    assert abs(float(np.linalg.det([[1., 2.], [3., 5.]])) + 1) < 1e-10
                    assert np.allclose(np.fft.fft([1., 0., 0., 0.]), [1., 1., 1., 1.])
                    print('Real native NumPy verified:', np.__version__, native.__file__)
                    """);
        }
    }

    private static Value plan(Context context, Value factory, Path root, Map<String, String> declarations, Path pip) {
        Value serialize = context.eval("python", "lambda values: __import__('json').dumps({str(k): str(v) for k, v in values.items()})");
        return factory.execute(root.toString(), serialize.execute(declarations).asString(),
                GraalPyRuntimeResources.GRAALPY_VERSION, pip.toString(), "test-policy-v1");
    }

    private static boolean pip(Context context, Value transaction, Path directory) {
        Value args = transaction.invokeMember("arguments");
        List<String> arguments = new ArrayList<>();
        for (long index = 0; index < args.getArraySize(); index++) arguments.add(args.getArrayElement(index).asString());
        return PythonPipRunner.run(context, arguments, directory);
    }

    private static void install(Context context, Value transaction, Path directory) {
        if (!transaction.getMember("cached").asBoolean()) {
            check(pip(context, transaction, directory), "Wheel installation failed");
            transaction.invokeMember("commit");
        }
    }

    private static void wheel(Path directory, String name, String version, Map<String, String> content) throws Exception {
        wheel(directory, name, version, content, "");
    }

    private static void wheel(Path directory, String name, String version, Map<String, String> content, String dependency) throws Exception {
        String metadata = name + "-" + version + ".dist-info/";
        Map<String, String> entries = new LinkedHashMap<>(content);
        entries.put(metadata + "METADATA", "Metadata-Version: 2.1\nName: " + name.replace('_', '-') + "\nVersion: " + version + "\n"
                + (dependency.isEmpty() ? "" : "Requires-Dist: " + dependency + "\n"));
        entries.put(metadata + "WHEEL", "Wheel-Version: 1.0\nGenerator: pyfish-test\nRoot-Is-Purelib: true\nTag: py3-none-any\n");
        StringBuilder records = new StringBuilder();
        for (var entry : entries.entrySet()) {
            byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
            records.append(entry.getKey()).append(",sha256=").append(digest).append(',').append(bytes.length).append('\n');
        }
        entries.put(metadata + "RECORD", records + metadata + "RECORD,,\n");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve(name + "-" + version + "-py3-none-any.whl")))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
