#!/usr/bin/env python3
"""Build the Snip comic-frame pack and (optionally) push it to the phone.

The app draws a frame for every Snip from local data, like the X-Ray assets: the 3 style reference
images, plus each character's look table and look images. This reads them from the audiobook
co-pilot working folder (the one holding `audiobook-copilot/looks/combined/*.json` and
`reference-images/`) and writes the layout `FramePack` expects:

    style/style-N.png|jpg
    looks.json                     {book_title, characters: [{name, slug, looks: [...]}]}
    images/<slug>/look-NNN-standing.png and look-NNN-action.png

Usage:
    scripts/frame_pack/build_frame_pack.py --source "/path/to/Audiobook co-pilot" --push

The pack applies to one book: its looks are keyed by that book's own timeline, and the app only uses
them when the playing book's title starts with --book-title. Images are resized to at most --max-px
(macOS `sips`; copied unchanged where it is missing) because the pack goes to the phone over adb.
"""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}


def resize(src: Path, dst: Path, max_px: int) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if shutil.which("sips"):
        subprocess.run(["sips", "-Z", str(max_px), str(src), "--out", str(dst)], check=True, capture_output=True)
    else:
        shutil.copyfile(src, dst)


def build_styles(source: Path, out: Path, max_px: int) -> int:
    styles = sorted(p for p in (source / "reference-images" / "style").iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)
    for i, p in enumerate(styles[:3], start=1):
        resize(p, out / "style" / f"style-{i}{p.suffix.lower()}", max_px)
    return min(len(styles), 3)


def build_looks(source: Path, out: Path, book: int, max_px: int) -> list:
    characters = []
    combined = source / "audiobook-copilot" / "looks" / "combined"
    for path in sorted(combined.glob("*.json")):
        if path.name == "_index.json":
            continue
        data = json.loads(path.read_text())
        looks = []
        for look in data.get("looks", []):
            if look.get("book") != book:
                continue
            entry = {
                "seq": look["seq"],
                "first_ms": look["first_ms"],
                "description": look.get("description"),
                "standing": None,
                "action": None,
            }
            for kind in ("standing", "action"):
                ref = (look.get("reference_images") or {}).get(kind)
                image = (path.parent / ref).resolve() if ref else None
                if image and image.is_file():
                    target = Path("images") / data["slug"] / image.name
                    resize(image, out / target, max_px)
                    entry[kind] = target.as_posix()
            looks.append(entry)
        if looks:
            characters.append({"name": data["entity"], "slug": data["slug"], "looks": looks})
    return characters


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", required=True, type=Path, help="the 'Audiobook co-pilot' working folder")
    parser.add_argument("--book", type=int, default=1, help="book number in the looks files (default 1)")
    parser.add_argument("--book-title", default="Dungeon Crawler Carl", help="the playing book's title must start with this")
    parser.add_argument("--out", type=Path, default=Path("build/frame-pack"))
    parser.add_argument("--max-px", type=int, default=1024)
    parser.add_argument("--push", action="store_true", help="adb push the pack to the connected phone")
    parser.add_argument("--package", default="de.ph1b.audiobook")
    args = parser.parse_args()

    if args.out.exists():
        shutil.rmtree(args.out)
    args.out.mkdir(parents=True)

    styles = build_styles(args.source, args.out, args.max_px)
    characters = build_looks(args.source, args.out, args.book, args.max_px)
    (args.out / "looks.json").write_text(
        json.dumps({"book_title": args.book_title, "characters": characters}, indent=1, ensure_ascii=False) + "\n"
    )
    with_images = sum(1 for c in characters if any(l["standing"] or l["action"] for l in c["looks"]))
    print(f"{styles} style images, {len(characters)} characters ({with_images} with look images) -> {args.out}")

    if args.push:
        target = f"/sdcard/Android/data/{args.package}/files/frame-pack"
        subprocess.run(["adb", "shell", "rm", "-rf", target], check=True)
        subprocess.run(["adb", "shell", "mkdir", "-p", target], check=True)
        subprocess.run(["adb", "push", f"{args.out}/.", target], check=True)
        print(f"pushed to {target}")
    else:
        print(f"push with: adb push '{args.out}/.' /sdcard/Android/data/{args.package}/files/frame-pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
