"""Small read-only NBT decoder for auditing Minecraft structure resources."""
import gzip
import io
import struct


def read(data):
    stream = io.BytesIO(gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data)

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number("H")).decode("utf-8")

    def payload(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return stream.read(number("i"))
        if kind == 8:
            return string()
        if kind == 9:
            child, count = number("B"), number("i")
            return [payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := number("B")):
                name = string()
                result[name] = payload(child)
            return result
        if kind in (11, 12):
            return [number("i" if kind == 11 else "q") for _ in range(number("i"))]
        raise ValueError(f"Unsupported NBT tag: {kind}")

    root_kind = number("B")
    string()
    return payload(root_kind)
