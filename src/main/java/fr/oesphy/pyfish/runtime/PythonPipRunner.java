package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.nio.file.Path;
import java.util.List;

final class PythonPipRunner {
    private static final Source INSTALLER = Source.newBuilder("python", """
            def install(arguments, pip_directory):
                import sys, runpy
                previous_argv, previous_path = sys.argv[:], sys.path[:]
                try:
                    sys.path.insert(0, str(pip_directory))
                    sys.argv = [str(arg) for arg in arguments]
                    try:
                        runpy.run_module('pip', run_name='__main__')
                    except SystemExit as error:
                        return error.code in (None, 0)
                    return True
                finally:
                    sys.argv[:], sys.path[:] = previous_argv, previous_path
            install
            """, "pyfish-pip-bootstrap.py").option("python.NewGlobals", "true").buildLiteral();

    private PythonPipRunner() { }

    static boolean run(Context context, List<String> arguments, Path pipDirectory) {
        // A second native context in the same JVM would need external isolation tools.
        return context.eval(INSTALLER).execute(arguments.toArray(String[]::new), pipDirectory.toString()).asBoolean();
    }
}
