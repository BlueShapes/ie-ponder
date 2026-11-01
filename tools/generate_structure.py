"""Generate our stone/air Ponder stage; no third-party assets are copied."""
import gzip
import struct
from pathlib import Path


def text(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, data):
    return bytes([kind]) + text(name) + data


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def ints(name, values):
    return tag(9, name, b"\x03" + struct.pack(">i", len(values))
               + b"".join(struct.pack(">i", value) for value in values))


def compounds(name, values):
    return tag(9, name, b"\x0a" + struct.pack(">i", len(values)) + b"".join(values))


def generate():
    palette = [tag(8, "Name", text(block)) + b"\0"
               for block in ("minecraft:smooth_stone", "minecraft:air")]
    blocks = [integer("state", int(y != 0)) + ints("pos", [x, y, z]) + b"\0"
              for x in range(9) for y in range(7) for z in range(9)]
    root = (integer("DataVersion", 3955) + ints("size", [9, 7, 9])
            + compounds("palette", palette) + compounds("blocks", blocks)
            + compounds("entities", []) + b"\0")
    path = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ieponder/ponder/stage.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(tag(10, "", root), mtime=0))
    print(f"Generated 9 x 7 x 9 stage ({len(blocks)} explicit blocks)")


if __name__ == "__main__":
    generate()
