import errno
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import site
import sys
import sysconfig
import tempfile
import uuid
from urllib.parse import unquote, urlsplit


_NATIVE_FILE = re.compile(r"\.(pyd|dll|dylib|so(?:\.\d+)*)$", re.I)
_CPYTHON_FILE = re.compile(r"\.cp\d{2,3}[-_.]", re.I)
_FORBIDDEN = {"-r", "--requirement", "-c", "--constraint", "-e", "--editable",
              "--no-binary", "--only-binary", "--platform", "--python-version", "--abi", "--implementation",
              "--target", "--prefix", "--root", "--user", "--config-settings"}


def _filter_websocket_defaults(defaults, sockets):
    known = {}
    for name, value in (("TCP_KEEPIDLE", 30), ("TCP_KEEPINTVL", 10), ("TCP_KEEPCNT", 3)):
        number = getattr(sockets, name, None)
        if number is not None:
            known[(sockets.IPPROTO_TCP, number, value)] = name
    candidates = [option for option in defaults if isinstance(option, tuple) and option in known]
    if not candidates:
        return []
    unsupported_errors = {getattr(errno, name) for name in ("ENOPROTOOPT", "ENOTSUP", "EOPNOTSUPP")
                          if hasattr(errno, name)}
    removed = []
    with sockets.socket(sockets.AF_INET, sockets.SOCK_STREAM) as probe:
        for option in candidates:
            try:
                probe.setsockopt(*option)
            except OSError as error:
                if error.errno not in unsupported_errors:
                    raise
                removed.append(option)
    # _http imports this list by reference. Keep its identity, and mutate only
    # after every probe succeeded so unexpected failures leave it untouched.
    defaults[:] = [option for option in defaults if option not in removed]
    return list(dict.fromkeys(known[option] for option in removed))


def _configure_websocket(packages, root, runtime):
    if sys.implementation.name != "graalpy" or not runtime.startswith("25.3."):
        return ""
    if not any(package["name"].lower().replace("_", "-") == "websocket-client" for package in packages):
        return ""
    import websocket
    from websocket import _http, _socket
    if not Path(websocket.__file__).resolve().is_relative_to(root.resolve()):
        raise RuntimeError("websocket-client was imported outside the managed package environment")
    defaults = getattr(_socket, "DEFAULT_SOCKET_OPTION", None)
    if not isinstance(defaults, list) or getattr(_http, "DEFAULT_SOCKET_OPTION", None) is not defaults:
        raise RuntimeError("Unsupported websocket-client socket defaults layout; compatibility adapter needs review")
    import socket
    removed = _filter_websocket_defaults(defaults, socket)
    if not removed:
        return ""
    return ("websocket-client: disabled unsupported default TCP settings " + ", ".join(removed)
            + "; OS keepalive timings are used. Explicit sockopt settings are not changed.")


def _inside(path, root):
    if not path.resolve().is_relative_to(root.resolve()):
        raise RuntimeError("Dependency path escaped its environment: " + str(path))
    return path


class Installation:
    def __init__(self, root, declarations, runtime, pip_directory, request_key):
        self.requirements = json.loads(str(declarations))
        self.runtime = str(runtime)
        self.stage = None
        self.cached = False
        self.report = None
        self._load_pip(str(pip_directory))
        self._validate_requirements()
        from pip._vendor.packaging.tags import sys_tags
        tag_identity = "\n".join(sorted(str(tag) for tag in sys_tags()))
        identity = {"request": str(request_key), "requirements": self.requirements,
                    "runtime": self.runtime, "python": list(sys.version_info[:3]),
                    "abi": sys.implementation.cache_tag, "platform": sysconfig.get_platform(),
                    "libc": platform.libc_ver(), "tags": hashlib.sha256(tag_identity.encode()).hexdigest(),
                    "policy": "binary-only-v1"}
        self.key = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
        label = re.sub(r"[^a-zA-Z0-9_.-]", "_", sysconfig.get_platform())
        self.base = (Path(str(root)) / "environments" / self.runtime / label).resolve()
        self.base.mkdir(parents=True, exist_ok=True)
        # The caller holds the installation lock. Staging libraries have never been
        # activated, so interrupted installations can be discarded safely.
        for stale in self.base.glob(".install-*"):
            if stale.is_dir() and re.fullmatch(r"\.install-[a-z0-9_]{8}", stale.name):
                _inside(stale, self.base)
                shutil.rmtree(stale)
        self.target = self.base / self.key
        if self._valid_cache():
            self.cached = True
            return
        if self.target.exists():
            # Never overwrite a partially installed environment in place: another game
            # process may still have a native library mapped from it.
            self.target.rename(self.base / (".invalid-" + self.key[:12] + "-" + uuid.uuid4().hex))
        self.stage = Path(tempfile.mkdtemp(prefix=".install-", dir=self.base))
        self.packages = self.stage / "packages"
        self.packages.mkdir()
        self.inputs = self.stage / "requirements"
        self.inputs.mkdir()

    def _load_pip(self, directory):
        previous = sys.path[:]
        try:
            sys.path.insert(0, directory)
            import pip
            if not getattr(pip, "__GRAALPY_PATCHED", False):
                raise RuntimeError("PyFish requires the pip shipped with GraalPy")
            from pip._vendor.packaging.requirements import Requirement
            self.requirement_type = Requirement
        finally:
            sys.path[:] = previous

    def _validate_requirements(self):
        from pip._internal.req.req_file import preprocess
        # Match pip's comments, continuations and environment expansion before
        # fingerprinting; an expanded index or wheel URL changes the environment.
        self.requirements = {mod: "\n".join(line for _, line in preprocess(content))
                             for mod, content in self.requirements.items()}
        primary_index = None
        for mod, content in self.requirements.items():
            for line in content.splitlines():
                option = line.split(None, 1)[0].split("=", 1)[0]
                if option == "--index-url" or (option.startswith("-i") and not option.startswith("--")):
                    tokens = shlex.split(line)
                    if tokens[0].startswith("-i") and tokens[0] != "-i":
                        index = tokens[0][2:]
                    else:
                        index = tokens[0].partition("=")[2] if "=" in tokens[0] else tokens[1]
                    if primary_index is not None and primary_index[1] != index.rstrip("/"):
                        raise RuntimeError(f"Conflicting primary package indexes for mods {primary_index[0]} and {mod}")
                    primary_index = (mod, index.rstrip("/"))
                if option in _FORBIDDEN or (option.startswith(("-r", "-c", "-e")) and not option.startswith("--")):
                    raise RuntimeError(f"Mod {mod}: unsupported requirements option {option}; "
                                       "use flat requirements and prebuilt GraalPy-compatible wheels")
                if line.startswith("-"):
                    continue  # pip validates its remaining requirements-file options.
                declaration = re.split(r"\s+--hash[= ]", line, maxsplit=1)[0]
                try:
                    requirement = self.requirement_type(declaration)
                except Exception:
                    if unquote(urlsplit(declaration).path).lower().endswith(".whl"):
                        continue
                    raise RuntimeError(f"Mod {mod}: expected a package requirement or wheel, not a source tree") from None
                if requirement.name.lower().replace("_", "-") == "pip":
                    raise RuntimeError(f"Mod {mod}: pip is managed by PyFish and cannot be replaced")
                if requirement.url and not unquote(urlsplit(requirement.url).path).lower().endswith(".whl"):
                    raise RuntimeError(f"Mod {mod}: source URLs/VCS builds are disabled; supply a compatible wheel")

    def _valid_cache(self):
        try:
            report = json.loads((self.target / "manifest.json").read_text(encoding="utf-8"))
            if not isinstance(report, dict) or not isinstance(report.get("packages"), list) or not isinstance(report.get("files"), dict):
                return False
            for package in report["packages"]:
                if (not isinstance(package, dict) or not isinstance(package.get("name"), str)
                        or not isinstance(package.get("version"), str) or not isinstance(package.get("native_files"), list)):
                    return False
            if report["key"] != self.key or not (self.target / "packages").is_dir():
                return False
            for relative, size in report["files"].items():
                relative_path = Path(relative)
                if relative_path.is_absolute() or relative_path.drive or ".." in relative_path.parts:
                    return False
                path = self.target / "packages" / relative_path
                if path.is_symlink() or not path.is_file() or path.stat().st_size != size:
                    return False
            self.report = report
            return True
        except (OSError, ValueError, KeyError, TypeError, RuntimeError):
            return False

    def arguments(self):
        if self.cached:
            return []
        arguments = ["pip", "install", "--isolated", "--disable-pip-version-check", "--no-input",
                     "--no-color", "--progress-bar", "off", "--only-binary=:all:", "--no-compile",
                     "--index-url", "https://pypi.org/simple",
                     "--target", str(self.packages), "--report", str(self.stage / "pip-report.json")]
        for index, (mod, content) in enumerate(sorted(self.requirements.items())):
            safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", mod)[:64]
            path = self.inputs / f"{index:03d}-{safe_name}.txt"
            path.write_text(content, encoding="utf-8")
            arguments.extend(["--requirement", str(path)])
        return arguments

    def commit(self):
        if self.cached:
            return
        from pip._vendor.packaging.tags import sys_tags, parse_tag
        supported = set(sys_tags())
        packages, owners = [], {}
        for distribution in importlib.metadata.distributions(path=[str(self.packages)]):
            name, version = distribution.metadata["Name"], distribution.version
            if name.lower() == "pip":
                raise RuntimeError("A dependency attempted to replace PyFish-managed pip")
            tags = distribution.read_text("WHEEL") or ""
            declared_tags = [line[5:].strip() for line in tags.splitlines() if line.startswith("Tag:")]
            if not any(parse_tag(tag) & supported for tag in declared_tags):
                raise RuntimeError(f"Incompatible wheel ABI for {name} {version}")
            native = []
            for relative in distribution.files or []:
                path = Path(os.path.abspath(distribution.locate_file(relative)))
                if not path.is_relative_to(self.packages):
                    continue  # Console-script RECORD entries can point outside --target.
                file_name = path.relative_to(self.packages).as_posix()
                digest = None if relative.hash is None else (relative.hash.mode, relative.hash.value)
                owner = owners.get(file_name)
                if owner and owner[0] != name and (digest is None or digest != owner[1]):
                    raise RuntimeError(f"Import-file conflict: {owner[0]} and {name} both provide {file_name}")
                owners[file_name] = (name, digest)
                if _NATIVE_FILE.search(file_name):
                    if _CPYTHON_FILE.search(file_name):
                        raise RuntimeError(f"CPython extension in {name}: rebuild it for GraalPy")
                    native.append(file_name)
            packages.append({"name": name, "version": version, "native_files": native, "wheel_tags": declared_tags})
        report = json.loads((self.stage / "pip-report.json").read_text(encoding="utf-8"))
        for installed in report.get("install", []):
            url = installed.get("download_info", {}).get("url", "")
            if not unquote(urlsplit(url).path).lower().endswith(".whl"):
                raise RuntimeError("Only prebuilt wheels may be installed in the embedded runtime")
        files = {}
        for path in self.packages.rglob("*"):
            if path.is_symlink():
                raise RuntimeError("Symbolic links are not supported in managed package environments")
            if path.is_file() and "__pycache__" not in path.parts and path.suffix not in (".pyc", ".pyo"):
                files[path.relative_to(self.packages).as_posix()] = path.stat().st_size
        self.report = {"key": self.key, "runtime": self.runtime, "python": list(sys.version_info[:3]),
                       "platform": sysconfig.get_platform(), "mods": sorted(self.requirements),
                       "packages": sorted(packages, key=lambda package: package["name"].lower()), "files": files}
        (self.stage / "manifest.json").write_text(json.dumps(self.report, indent=2), encoding="utf-8")
        shutil.rmtree(self.inputs)
        (self.stage / "pip-report.json").unlink()
        self.stage.rename(self.target)
        self.stage = None

    def activate(self):
        site.addsitedir(str(self.target / "packages"))
        self.compatibility_notice = _configure_websocket(self.report["packages"], self.target / "packages", self.runtime)
        return json.dumps({"cached": self.cached, "path": str(self.target), "compatibility": self.compatibility_notice,
                           "packages": [
            {"name": package["name"], "version": package["version"], "native": bool(package["native_files"])}
            for package in self.report["packages"]]})

    def abort(self):
        if self.stage is not None and self.stage.exists():
            _inside(self.stage, self.base)
            shutil.rmtree(self.stage)
            self.stage = None


def create_installation(root, requirements, runtime, pip_directory, request_key):
    return Installation(root, requirements, runtime, pip_directory, request_key)


create_installation
