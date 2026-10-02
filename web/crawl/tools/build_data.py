#!/usr/bin/env python3
"""Builds the Crawl explorer's data bundle from the audiobook-copilot pipeline
outputs on Vipul's Mac: look tracker (looks/combined), player cards
(player-cards/book-N) and the generated look images (reference-images/core).

Runs on the Mac. It never edits the pipeline folders; it only reads them and
writes into the site folder (web/crawl/data and web/crawl/looks).

Usage (from the Voice repo):
  python3 web/crawl/tools/build_data.py \
      --copilot "/Users/ivipul/AI Experiments - New/Audiobook co-pilot/audiobook-copilot" \
      --images  "/Users/ivipul/AI Experiments - New/Audiobook co-pilot/reference-images/core" \
      --out     web/crawl [--max-px 1200] [--dry-run]

Player-card files: the layout under player-cards/book-N/ was produced by a
separate pipeline and is not pinned here. `adapt_player_cards` reads the most
likely shapes (a list of field-level entries with a field name, a book-relative
time and a value) and prints what it could not understand; finish that
function against the real files before trusting the stats.
"""
import argparse
import json
import os
import re
import shutil
import sys
from datetime import datetime, timezone

CHARACTERS = [
    # slug used by the site, display name, look-tracker aliases (lowercased), palette
    ("carl", "Carl", ["carl"], "#FF6A1F", "#FFB184", "human"),
    ("princess-donut", "Princess Donut", ["princess donut", "donut"], "#FF4FA3", "#FFB3D9", "cat"),
    ("katya-grim", "Katya Grim", ["katya grim", "katia grim", "katya", "katia"], "#5CC8FF", "#BFE9FF", "human"),
    ("mongo", "Mongo", ["mongo"], "#7CFF5A", "#CFFFC2", "raptor"),
    ("samantha", "Samantha", ["samantha", "samantha sex doll head"], "#B48CFF", "#E0D0FF", "head"),
    ("mordecai", "Mordecai", ["mordecai"], "#FFC43D", "#FFE4A3", "ratkin"),
]
BOOK_TITLES = {
    1: "Dungeon Crawler Carl",
    2: "Carl's Doomsday Scenario",
    3: "The Dungeon Anarchist's Cookbook",
    4: "The Gate of the Feral Gods",
    5: "The Butcher's Masquerade",
    6: "The Eye of the Bedlam Bride",
    7: "This Inevitable Ruin",
    # 8: fill from the audio file name
}
STAT_FIELDS = {"str": "str", "strength": "str", "dex": "dex", "dexterity": "dex", "con": "con", "constitution": "con",
               "int": "int", "intelligence": "int", "cha": "cha", "charisma": "cha",
               "level": "level", "floor": "floor", "race": "race", "class": "class"}


def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", (s or "").lower()).strip()


def hms_to_ms(t):
    parts = [int(float(x)) for x in str(t).split(":")]
    while len(parts) < 3:
        parts.insert(0, 0)
    h, m, s = parts[-3:]
    return (h * 3600 + m * 60 + s) * 1000


def ms_to_hms(ms):
    s = int(ms // 1000)
    return "%02d:%02d:%02d" % (s // 3600, s % 3600 // 60, s % 60)


# ---------------------------------------------------------------- looks

def find_combined(copilot, aliases):
    """Finds looks/combined/<slug>.json for a character by alias."""
    combined = os.path.join(copilot, "looks", "combined")
    index_path = os.path.join(combined, "_index.json")
    if os.path.exists(index_path):
        try:
            idx = json.load(open(index_path))
            items = idx if isinstance(idx, list) else idx.get("entities") or idx.get("items") or list(idx.values())
            for it in items:
                if not isinstance(it, dict):
                    continue
                names = [it.get("entity"), it.get("name"), it.get("slug")] + list(it.get("aliases") or [])
                if any(norm(n) in aliases for n in names if n):
                    slug = it.get("slug") or re.sub(r"[^a-z0-9]+", "-", norm(it.get("entity") or it.get("name")))
                    p = os.path.join(combined, slug + ".json")
                    if os.path.exists(p):
                        return p
        except Exception as ex:  # fall through to filename scan
            print("  (could not use _index.json: %s)" % ex)
    for fn in sorted(os.listdir(combined)):
        if not fn.endswith(".json") or fn.startswith("_"):
            continue
        stem = norm(fn[:-5])
        if stem in aliases or stem.replace(" ", "") in [a.replace(" ", "") for a in aliases]:
            return os.path.join(combined, fn)
    return None


def image_info(path):
    """(width, height, has_alpha) using Pillow when present; otherwise unknown."""
    try:
        from PIL import Image  # noqa
        im = Image.open(path)
        has_alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
        if has_alpha and im.mode == "RGBA":
            # genuinely transparent only if some pixels are
            alpha = im.getchannel("A")
            lo, hi = alpha.getextrema()
            has_alpha = lo < 250
        return im.size[0], im.size[1], has_alpha
    except ImportError:
        return None, None, None


def export_image(src, dst_base, max_px, dry):
    """Copies/resizes a look image into the site. Returns (relative path, note)."""
    w, h, alpha = image_info(src)
    note = "unknown-transparency" if alpha is None else ("transparent" if alpha else "OPAQUE")
    try:
        from PIL import Image
        im = Image.open(src).convert("RGBA")
        if max(im.size) > max_px:
            im.thumbnail((max_px, max_px))
        dst = dst_base + ".webp"
        if not dry:
            try:
                im.save(dst, "WEBP", quality=88, method=6)
            except Exception:
                dst = dst_base + ".png"
                im.save(dst, "PNG", optimize=True)
        return dst, note
    except ImportError:
        dst = dst_base + os.path.splitext(src)[1].lower()
        if not dry:
            shutil.copyfile(src, dst)
        return dst, note + " (copied as-is; install Pillow to resize/convert)"


def build_looks(copilot, images_root, slug, name, aliases, out_root, max_px, dry, report):
    path = find_combined(copilot, aliases)
    if not path:
        report.append("%s: no looks/combined file found (aliases %s)" % (name, aliases))
        return []
    doc = json.load(open(path))
    entries = doc.get("looks") or doc.get("entries") or []
    folder_candidates = [doc.get("folder"), doc.get("entity"), name]
    img_dir = None
    for cand in folder_candidates:
        if cand and os.path.isdir(os.path.join(images_root, cand, "looks")):
            img_dir = os.path.join(images_root, cand, "looks")
            break
    looks = []
    out_dir = os.path.join(out_root, "looks", slug)
    if not dry:
        os.makedirs(out_dir, exist_ok=True)
    per_book_seq = {}
    for i, e in enumerate(entries):
        book = int(e.get("book") or 1)
        if "first_ms" in e and e["first_ms"] is not None:
            ms = int(e["first_ms"])
        else:
            ms = hms_to_ms(e.get("timestamp") or e.get("from") or "0:00:00")
        seq = int(e.get("seq") or (i + 1))
        per_book_seq[book] = per_book_seq.get(book, 0) + 1
        images = {}
        refs = e.get("reference_images") or {}
        for kind in ("standing", "action"):
            src = refs.get(kind)
            if not src and img_dir:
                guess = os.path.join(img_dir, "look-%03d-%s.png" % (seq, kind))
                src = guess if os.path.exists(guess) else None
            elif src and not os.path.isabs(src):
                # paths in the tracker are relative to the Mac project folder
                for base in (os.path.dirname(path), os.path.dirname(copilot), copilot):
                    cand = os.path.join(base, src)
                    if os.path.exists(cand):
                        src = cand
                        break
            if src and os.path.exists(src):
                dst, note = export_image(src, os.path.join(out_dir, "look-%03d-%s" % (seq, kind)), max_px, dry)
                images[kind] = os.path.relpath(dst, out_root).replace(os.sep, "/")
                if note.startswith("OPAQUE"):
                    report.append("%s look %03d %s: image is OPAQUE (needs background removal): %s" % (name, seq, kind, src))
            else:
                images[kind] = None
        looks.append({
            "seq": seq, "book": book, "ms": ms, "t": ms_to_hms(ms),
            "description": (e.get("description") or e.get("drawn") or e.get("outfit") or "").strip(),
            "change": (e.get("change_note") or "").strip() or None,
            "images": images,
        })
    missing = sum(1 for l in looks if not l["images"].get("action") and not l["images"].get("standing"))
    report.append("%s: %d looks from %s, %d without any image" % (name, len(looks), os.path.basename(path), missing))
    return looks


# ---------------------------------------------------------------- player cards

def adapt_player_cards(copilot, aliases, report, name):
    """Returns field-level entries [{field, book, ms, value, label?}] from
    player-cards/book-N/entries.json (rows: entity, field, value,
    timestamp_start (book-relative ms), quote, optional key / modifier_only).

    LEVEL/FLOOR 'Level 5' -> 5; STAT_* numeric ('base / +mod' keeps the base; modifier-only
    '+3' rows are skipped); SKILL 'Name (Rank 6)' -> skill:Name = 6; GEAR 'Item (slot)' ->
    gear:<slot> = Item. CALL_SIGN has no field on the site and is skipped.
    """
    root = os.path.join(copilot, "player-cards")
    out = []
    skipped = {}
    if not os.path.isdir(root):
        report.append("player-cards folder not found at %s" % root)
        return out
    for bd in sorted(os.listdir(root)):
        m = re.match(r"book-(\d+)$", bd)
        if not m:
            continue
        book = int(m.group(1))
        path = os.path.join(root, bd, "entries.json")
        if not os.path.exists(path):
            report.append("%s: no entries.json in %s" % (name, bd))
            continue
        for e in json.load(open(path)):
            if norm(e.get("entity")) not in aliases:
                continue
            rec, why = to_entry(e, book)
            if rec:
                out.append(rec)
            else:
                skipped[why] = skipped.get(why, 0) + 1
    out.sort(key=lambda r: (r["book"], r["ms"]))
    report.append("%s: %d card entries (skipped: %s)" % (name, len(out), ", ".join("%s x%d" % kv for kv in sorted(skipped.items())) or "none"))
    return out


def to_entry(e, book):
    """(entry, None) or (None, reason)."""
    field = (e.get("field") or "").upper()
    value = (e.get("value") or "").strip()
    t = e.get("timestamp_start")
    if t is None:
        return None, "no timestamp"
    t = int(t)
    if field in ("LEVEL", "FLOOR"):
        m = re.search(r"(\d+)", value)
        if not m:
            return None, "unparsed " + field.lower()
        return {"field": field.lower(), "book": book, "ms": t, "value": int(m.group(1))}, None
    if field in ("RACE", "CLASS"):
        return {"field": field.lower(), "book": book, "ms": t, "value": value}, None
    if field.startswith("STAT_"):
        if e.get("modifier_only"):
            return None, "stat modifier"
        m = re.fullmatch(r"(\d+)(?:\s*/\s*\+\d+)?", value)
        if not m:
            return None, "unparsed stat"
        return {"field": field[5:].lower(), "book": book, "ms": t, "value": int(m.group(1))}, None
    if field == "SKILL":
        m = re.fullmatch(r"(.*?)\s*\(Rank\s*(\d+)\)", value, re.I)
        if not m:
            return None, "unparsed skill"
        return {"field": "skill:" + m.group(1).strip(), "book": book, "ms": t, "value": int(m.group(2))}, None
    if field == "GEAR":
        m = re.fullmatch(r"(.*?)\s*\(([^()]*)\)", value)
        slot = e.get("key") or (m.group(2) if m else None)
        item = m.group(1).strip() if m else value
        if not slot:
            return None, "gear without slot"
        return {"field": "gear:" + norm(slot), "book": book, "ms": t, "value": item}, None
    return None, field.lower() or "no field"


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--copilot", required=True, help="audiobook-copilot folder on the Mac")
    ap.add_argument("--images", required=True, help="reference-images/core folder")
    ap.add_argument("--out", default="web/crawl", help="site root (web/crawl)")
    ap.add_argument("--audiobooks", help="folder holding the 8 audio files, to read book 8's title")
    ap.add_argument("--max-px", type=int, default=1200)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    out_root = os.path.abspath(a.out)
    data_dir = os.path.join(out_root, "data")
    books = json.load(open(os.path.join(data_dir, "books.json")))["books"]
    for b in books:
        b["title"] = b.get("title") or BOOK_TITLES.get(b["n"])
    if a.audiobooks and os.path.isdir(a.audiobooks):
        for fn in os.listdir(a.audiobooks):
            m = re.match(r"(.+?) - Dungeon Crawler Carl, Book (\d+)\.(?:m4b|mp3|m4a)$", fn)
            if m:
                for b in books:
                    if b["n"] == int(m.group(2)) and not b.get("title"):
                        b["title"] = m.group(1).strip()
    report = []
    chars_out = []
    for slug, name, aliases, accent, accent2, kind in CHARACTERS:
        print("==", name)
        looks = build_looks(a.copilot, a.images, slug, name, aliases, out_root, a.max_px, a.dry_run, report)
        card = adapt_player_cards(a.copilot, aliases, report, name)
        doc = {"slug": slug, "name": name, "epithet": None, "sample": False, "looks": looks, "card": card}
        if not a.dry_run:
            with open(os.path.join(data_dir, slug + ".json"), "w") as f:
                json.dump(doc, f, indent=1, ensure_ascii=False)
        chars_out.append({"slug": slug, "name": name, "kind": kind, "accent": accent, "accent2": accent2, "file": "data/%s.json" % slug})
    index = {"generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "sample": False, "books": books, "characters": chars_out}
    if not a.dry_run:
        with open(os.path.join(data_dir, "index.json"), "w") as f:
            json.dump(index, f, indent=1, ensure_ascii=False)
        with open(os.path.join(data_dir, "books.json"), "w") as f:
            json.dump({"_source": "see tools/build_data.py", "books": books}, f, indent=1, ensure_ascii=False)
    print("\n".join("- " + r for r in report))
    print("done" + (" (dry run)" if a.dry_run else ""))


if __name__ == "__main__":
    sys.exit(main())
