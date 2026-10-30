"""Build enlarged actual/diff pairs for review. Never copies references."""
from pathlib import Path
import hashlib
import json
import struct
import subprocess

root = Path(__file__).resolve().parent
run = root / "api36-record-changed-references-durable"
assert (run / "summary.json").is_file(), "recording must finish before review"
actual = sorted((run / "results").glob("**/screenshots/actual/*.png"))
assert actual, "no changed captures"
out = root / "visual-review"
out.mkdir(exist_ok=True)
font = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
manifest = []
def dimensions(path):
    return struct.unpack(">II", path.read_bytes()[16:24])
def magick(*args):
    subprocess.run(["magick", *map(str, args)], check=True)
for offset in range(0, len(actual), 4):
    entries = actual[offset:offset + 4]
    column = max(dimensions(path)[0] for path in entries) * 2
    rows = []
    filename = f"changed-{offset // 4 + 1:02}.png"
    for index, path in enumerate(entries):
        diff = path.parent.parent / "diff" / path.name
        assert diff.is_file(), f"missing diff for {path.name}"
        label = path.stem.replace("pomodoro_widget_", "P ").replace("timer_widget_", "T ")
        cells = []
        for kind, image_path in [("actual", path), ("diff", diff)]:
            cell = out / f"cell-{index}-{kind}.png"
            magick(image_path, "-resize", "200%", "-background", "#dddddd", "-gravity", "northwest", "-extent", f"{column}x{dimensions(path)[1] * 2}", "-splice", "0x26", "-font", font, "-pointsize", "14", "-fill", "black", "-annotate", "+4+2", label + " " + kind, cell)
            cells.append(cell)
        row = out / f"row-{index}.png"
        magick(*cells, "+append", row)
        rows.append(row)
        manifest.append({"name": path.name, "capture": str(path.relative_to(root)), "capture_sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "diff": str(diff.relative_to(root)), "diff_sha256": hashlib.sha256(diff.read_bytes()).hexdigest(), "sheet": filename, "reviewed": False})
    magick(*rows, "-append", out / filename)
for pattern in ["cell-*.png", "row-*.png"]:
    for path in out.glob(pattern): path.unlink()
(out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps({"changed_captures": len(actual), "sheets": len(list(out.glob("changed-*.png")))}))
