package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** Runs in a fresh JVM whose only runtime dependency is the finished mod JAR. */
public final class GraalPyRuntimeJarSmokeTest {
    private GraalPyRuntimeJarSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path cache = Path.of(args[1]).toAbsolutePath();

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
                .option("python.IsolateNativeModules", "true")
                .option("engine.WarnInterpreterOnly", "false");
        runtimeHome.configure(builder);

        try (Context context = builder.build()) {
            context.eval("python", "import ast, json, queue, threading");
            for (String moduleName : List.of(
                    "java_asyncio",
                    "java_multiprocessing",
                    "java_requests",
                    "java_websocket")) {
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
