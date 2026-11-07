package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

/** Runs in a fresh JVM whose only runtime dependency is the finished mod JAR. */
public final class GraalPyRuntimeJarSmokeTest {
    private GraalPyRuntimeJarSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path cache = Path.of(args[1]).toAbsolutePath();

        // GraalJS is single-threaded; Python's GC thread must not enter its context.
        try (Context js = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()) {
            if (js.eval("js", "6 * 7").asInt() != 42) {
                throw new IllegalStateException("Embedded JavaScript engine is unavailable");
            }
        }

        GraalPyRuntimeResources.RuntimeHome runtimeHome = GraalPyRuntimeResources
                .prepare(jar, cache)
                .orElseThrow(() -> new IllegalStateException("Runtime resources were not found in " + jar));

        Context.Builder builder = Context.newBuilder()
                .option("python.PosixModuleBackend", "java")
                .option("python.DontWriteBytecodeFlag", "true")
                .option("python.ForceImportSite", "true")
                .option("python.CheckHashPycsMode", "never")
                .allowAllAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .allowNativeAccess(true)
                .currentWorkingDirectory(Path.of(".").toAbsolutePath())
                .allowExperimentalOptions(true)
                .option("python.EmulateJython", "true")
                .option("engine.WarnInterpreterOnly", "false");
        runtimeHome.configure(builder);

        Path pipDirectory = cache.resolve("pip");
        try (InputStream pip = GraalPyRuntimeJarSmokeTest.class.getClassLoader().getResourceAsStream("pip.whl")) {
            if (pip == null) throw new IllegalStateException("Bundled pip wheel is missing");
            try (var zip = new java.util.zip.ZipInputStream(pip)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path destination = pipDirectory.resolve(entry.getName()).normalize();
                    if (!destination.startsWith(pipDirectory)) throw new IllegalStateException("Unsafe pip wheel entry");
                    if (entry.isDirectory()) Files.createDirectories(destination);
                    else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(zip, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        try (Context context = builder.build()) {
            context.getBindings("python").putMember("_test_cache", cache.toString());
            context.eval("python", """
                    import pathlib, zipfile, sys, runpy
                    root = pathlib.Path(_test_cache)
                    wheel = root / 'pyfish_runtime_probe-1.0-py3-none-any.whl'
                    with zipfile.ZipFile(wheel, 'w') as archive:
                        archive.writestr('pyfish_runtime_probe.py', 'value = 42\\n')
                        archive.writestr('pyfish_runtime_probe-1.0.dist-info/METADATA',
                            'Metadata-Version: 2.1\\nName: pyfish-runtime-probe\\nVersion: 1.0\\n')
                        archive.writestr('pyfish_runtime_probe-1.0.dist-info/WHEEL',
                            'Wheel-Version: 1.0\\nGenerator: pyfish-test\\nRoot-Is-Purelib: true\\nTag: py3-none-any\\n')
                        archive.writestr('pyfish_runtime_probe-1.0.dist-info/RECORD', '')
                    sys.argv = ['pyfish-smoke']
                    """);
            if (!PythonPipRunner.run(context, List.of("pip", "install", "--no-index", "--no-deps", "--no-cache-dir",
                    "--disable-pip-version-check", "--upgrade", "--target", cache.resolve("packages").toString(),
                    cache.resolve("pyfish_runtime_probe-1.0-py3-none-any.whl").toString()), pipDirectory)) {
                throw new IllegalStateException("Offline dependency installation failed");
            }
            context.eval("python", """
                    assert sys.argv == ['pyfish-smoke']
                    sys.path.insert(0, str(root / 'packages'))
                    import pyfish_runtime_probe
                    assert pyfish_runtime_probe.value == 42
                    """);
            for (String module : List.of("ast", "json", "queue", "threading", "copy", "ssl", "hashlib", "sqlite3",
                    "ctypes", "bz2", "lzma", "zlib", "unicodedata", "decimal")) {
                System.out.println("Checking Python module: " + module);
                context.eval("python", "import " + module);
            }
            context.eval("python", """
                    import ast, json, queue, threading, sys, copy, ssl, hashlib, sqlite3
                    import asyncio, concurrent.futures, pathlib, tempfile, zipfile, xml.etree.ElementTree
                    from dataclasses import dataclass
                    from typing import TypeIs, ReadOnly
                    import java
                    assert sys.version_info[:2] == (3, 13), sys.version
                    assert sys.graalpy_version_info[:3] == (25, 3, 4), sys.graalpy_version_info
                    assert json.loads(json.dumps({'value': [1, True, None]}))['value'][0] == 1
                    assert isinstance(ast.parse('x = 1'), ast.Module)
                    q = queue.Queue()
                    worker = threading.Thread(target=lambda: q.put(42))
                    worker.start()
                    worker.join(timeout=10)
                    assert not worker.is_alive() and q.get(timeout=1) == 42
                    @dataclass(frozen=True)
                    class Settings:
                        value: int = 1
                    assert copy.replace(Settings(), value=2).value == 2
                    java_list = java.type('java.util.ArrayList')()
                    java_list.add('pyfish')
                    assert java_list.get(0) == 'pyfish'
                    assert ssl.create_default_context() is not None
                    assert len(hashlib.sha256(b'pyfish').digest()) == 32
                    db = sqlite3.connect(':memory:')
                    assert db.execute('select 42').fetchone()[0] == 42
                    db.close()
                    async def check_async():
                        await asyncio.sleep(0)
                        return 42
                    assert asyncio.run(check_async()) == 42
                    print('Verified Python', sys.version)
                    """);
            for (String moduleName : List.of(
                    "java_asyncio",
                    "java_multiprocessing",
                    "java_requests",
                    "java_websocket",
                    "java_logging",
                    "java_pathlib",
                    "java_pillow",
                    "java_pyperclip",
                    "java_sqlite3",
                    "java_subprocess",
                    "java_uuid")) {
                executeModule(context, moduleName);
            }
        }

        System.out.println("PyFish GraalPy runtime smoke test passed: " + jar.getFileName());
    }

    private static void executeModule(Context context, String moduleName) throws Exception {
        try (InputStream stream = GraalPyRuntimeJarSmokeTest.class.getClassLoader()
                .getResourceAsStream(moduleName + ".py")) {
            if (stream == null) {
                throw new IllegalStateException("Missing Python module: " + moduleName);
            }

            Value bindings = context.getBindings("python");
            bindings.putMember("_smoke_name", moduleName);
            bindings.putMember("_smoke_code", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            context.eval("python", """
                    import sys, types
                    module = types.ModuleType(_smoke_name)
                    sys.modules[_smoke_name] = module
                    try:
                        exec(_smoke_code, module.__dict__)
                    except Exception:
                        sys.modules.pop(_smoke_name, None)
                        raise
                    """);
        }
    }
}
