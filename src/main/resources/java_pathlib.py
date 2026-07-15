from java.nio.file import Paths, Files
from java.io import File

class Path:
    def __init__(self, *args):
        if not args:
            self._jpath = Paths.get(".")
        elif len(args) == 1 and isinstance(args[0], Path):
             self._jpath = args[0]._jpath
        else:
             first = str(args[0])
             more = [str(x) for x in args[1:]]
             self._jpath = Paths.get(first, more)

    def __str__(self):
        return self._jpath.toString()
    
    def __repr__(self):
        return f"Path('{self._jpath.toString()}')"

    def __truediv__(self, other):
        return Path(self._jpath.resolve(str(other)).toString())

    def exists(self):
        return Files.exists(self._jpath)

    def is_dir(self):
        return Files.isDirectory(self._jpath)

    def is_file(self):
        return Files.isRegularFile(self._jpath)

    def mkdir(self, parents=False, exist_ok=False):
        if exist_ok and self.exists():
            return
        if parents:
            Files.createDirectories(self._jpath)
        else:
            Files.createDirectory(self._jpath)
    
    def touch(self, exist_ok=True):
        if self.exists():
            if exist_ok:
                return
            else:
                raise FileExistsError(f"File exists: {self}")
        Files.createFile(self._jpath)

    def read_text(self, encoding='utf-8', errors=None):
        return Files.readString(self._jpath)

    def write_text(self, data, encoding='utf-8', errors=None):
        Files.writeString(self._jpath, data)

    def read_bytes(self):
        return Files.readAllBytes(self._jpath)

    def write_bytes(self, data):
        Files.write(self._jpath, data)

    def iterdir(self):
        stream = Files.list(self._jpath)
        try:
            # Files.list owns a directory handle. Keep iteration inside this try/finally so
            # partially consumed generators do not leak it.
            iterator = stream.iterator()
            while iterator.hasNext():
                yield Path(iterator.next().toString())
        finally:
            stream.close()

    def glob(self, pattern):
        # DirectoryStream patterns match one path segment; recursive ** semantics belong in
        # rglob and should not be approximated here.
        stream = Files.newDirectoryStream(self._jpath, pattern)
        try:
            iterator = stream.iterator()
            while iterator.hasNext():
                yield Path(iterator.next().toString())
        finally:
            stream.close()

    def rglob(self, pattern):
         # Deliberately unsupported until this can use a PathMatcher. Files.walk plus string
         # matching gets pathlib's ** and platform separator rules wrong.
         pass
    
    @property
    def parent(self):
        p = self._jpath.getParent()
        return Path(p.toString()) if p else self

    @property
    def name(self):
        return self._jpath.getFileName().toString()

    @property
    def stem(self):
        name = self.name
        if '.' in name:
             return name.rsplit('.', 1)[0]
        return name

    @property
    def suffix(self):
        name = self.name
        if '.' in name:
             return '.' + name.rsplit('.', 1)[1]
        return ''

    def absolute(self):
        return Path(self._jpath.toAbsolutePath().toString())

    def resolve(self):
        return self.absolute()

    def unlink(self, missing_ok=False):
        try:
            Files.delete(self._jpath)
        except:
            if not missing_ok:
                raise 
