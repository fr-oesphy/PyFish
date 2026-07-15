package fr.oesphy.pyfish.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

final class PyFishPyzExecutor {
    // Keep the archive on sys.path after startup: callbacks may lazily import sibling
    // modules long after __main__.py has returned. Only argv and scratch globals are scoped.
    private static final String EXECUTION_SCRIPT = """
            import json as _pyfish_json
            import sys as _pyfish_sys

            _pyfish_archive = _pyfish_pyz_path
            if _pyfish_archive not in _pyfish_sys.path:
                _pyfish_sys.path.append(_pyfish_archive)

            _pyfish_previous_argv = list(_pyfish_sys.argv)
            try:
                _pyfish_sys.argv = [_pyfish_archive]
                _pyfish_metadata = _pyfish_json.loads(_pyfish_pyz_metadata_json)
                _pyfish_filename = _pyfish_archive + "/__main__.py"
                _pyfish_globals = {
                    "__name__": "__main__",
                    "__file__": _pyfish_filename,
                    "__package__": None,
                    "__spec__": None,
                    "PYFISH_MOD": _pyfish_metadata,
                    "PYFISH_MOD_ID": _pyfish_metadata["id"],
                    "PYFISH_MOD_NAME": _pyfish_metadata["name"],
                    "PYFISH_MOD_VERSION": _pyfish_metadata["version"],
                }
                exec(
                    compile(_pyfish_pyz_main_code, _pyfish_filename, "exec"),
                    _pyfish_globals,
                    _pyfish_globals,
                )
            finally:
                _pyfish_sys.argv = _pyfish_previous_argv
            _pyfish_globals
            """;

    private PyFishPyzExecutor() {
    }

    static Value execute(Context context, PyFishPyzMod mod) {
        Value bindings = context.getBindings("python");
        // Bind large source strings as host values instead of quoting them into Python; this
        // avoids escaping bugs and keeps tracebacks tied to the synthetic archive filename.
        bindings.putMember("_pyfish_pyz_path", mod.archivePath().toString());
        bindings.putMember("_pyfish_pyz_metadata_json", mod.manifest().toJson());
        bindings.putMember("_pyfish_pyz_main_code", mod.mainCode());
        try {
            return context.eval("python", EXECUTION_SCRIPT);
        } finally {
            // These names live in the shared context and must never leak into the next mod.
            bindings.removeMember("_pyfish_pyz_path");
            bindings.removeMember("_pyfish_pyz_metadata_json");
            bindings.removeMember("_pyfish_pyz_main_code");
        }
    }
}