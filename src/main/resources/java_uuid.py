try:
    import java as _java
except Exception:
    _java = globals().get("java")
    if _java is None:
        _java = __import__("java")

JavaUUID = _java.type("java.util.UUID")
ByteBuffer = _java.type("java.nio.ByteBuffer")


class UUID:
    def __init__(self, hex=None, bytes=None, version=None, _uuid=None):
        if _uuid is not None:
            self._uuid = _uuid
            return

        if hex is not None:
            clean_hex = str(hex).replace("-", "").replace("urn:", "").replace("uuid:", "").strip("{}")
            if len(clean_hex) != 32:
                raise ValueError("Badly formed hexadecimal UUID string")

            formatted = (
                f"{clean_hex[:8]}-"
                f"{clean_hex[8:12]}-"
                f"{clean_hex[12:16]}-"
                f"{clean_hex[16:20]}-"
                f"{clean_hex[20:]}"
            )
            self._uuid = JavaUUID.fromString(formatted)
            return

        if bytes is not None:
            if len(bytes) != 16:
                raise ValueError("bytes is not a 16-byte sequence")

            buffer = ByteBuffer.wrap(bytearray(bytes))
            self._uuid = JavaUUID(buffer.getLong(), buffer.getLong())
            return

        raise TypeError("UUID() requires hex, bytes, or _uuid")

    @property
    def hex(self):
        return str(self._uuid).replace("-", "")

    @property
    def bytes(self):
        buffer = ByteBuffer.allocate(16)
        buffer.putLong(self._uuid.getMostSignificantBits())
        buffer.putLong(self._uuid.getLeastSignificantBits())
        return bytes(bytearray(buffer.array()))

    @property
    def urn(self):
        return f"urn:uuid:{self._uuid}"

    def __str__(self):
        return str(self._uuid)

    def __repr__(self):
        return f"UUID('{self}')"

    def __eq__(self, other):
        return isinstance(other, UUID) and self._uuid.equals(other._uuid)

    def __hash__(self):
        return self._uuid.hashCode()


def uuid4():
    return UUID(_uuid=JavaUUID.randomUUID())


def uuid3(namespace, name):
    if not isinstance(namespace, UUID):
        raise TypeError("namespace must be a UUID")

    name_bytes = str(name).encode("utf-8")
    return UUID(_uuid=JavaUUID.nameUUIDFromBytes(bytearray(namespace.bytes + name_bytes)))


__all__ = ["UUID", "uuid3", "uuid4"]
