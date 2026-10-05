#!/usr/bin/env python3
"""Writes placeholder data for the Crawl explorer so it can run before the real
looks and player cards are bundled from the Mac (tools/build_data.py does that).

Look timestamps for Carl, Princess Donut and Mordecai in Book 1 are the real ones
from the look tracker (storyboards/book1/look-resolution.json); every other
look, and every stat, is sample data and is flagged as such.

Usage: python3 tools/make_sample_data.py   (from web/crawl)
"""
import json
import os
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA = os.path.join(ROOT, "data")


def hms(t):
    h, m, s = (int(x) for x in t.split(":"))
    return (h * 3600 + m * 60 + s) * 1000


def look(seq, book, t, description, img):
    return {
        "seq": seq,
        "book": book,
        "ms": hms(t),
        "t": t,
        "description": description,
        "images": {"standing": img, "action": img},
    }


def ph(slug, pose):
    return f"looks/{slug}/placeholder-{pose}.png"


def e(field, book, t, value, label=None):
    d = {"field": field, "book": book, "ms": hms(t), "value": value}
    if label:
        d["label"] = label
    return d


CHARACTERS = [
    {"slug": "carl", "name": "Carl", "kind": "human", "accent": "#FF6A1F", "accent2": "#FFB184"},
    {"slug": "princess-donut", "name": "Princess Donut", "kind": "cat", "accent": "#FF4FA3", "accent2": "#FFB3D9"},
    {"slug": "katya-grim", "name": "Katya Grim", "kind": "human", "accent": "#5CC8FF", "accent2": "#BFE9FF"},
    {"slug": "mongo", "name": "Mongo", "kind": "raptor", "accent": "#7CFF5A", "accent2": "#CFFFC2"},
    {"slug": "samantha", "name": "Samantha", "kind": "head", "accent": "#B48CFF", "accent2": "#E0D0FF"},
    {"slug": "mordecai", "name": "Mordecai", "kind": "ratkin", "accent": "#FFC43D", "accent2": "#FFE4A3"},
]

# Real Book 1 look times (look tracker, 2026-10-02). Descriptions are shortened.
CARL_LOOKS = [
    ("00:01:33", "Open black leather jacket, grey boxer shorts, bare legs, pink rubber crocs.", "stand"),
    ("00:55:30", "Leather jacket, boxer shorts, now barefoot. The crocs are gone.", "walk"),
    ("01:47:16", "Hooded cloak with pointed ear shapes, toe ring, goblin-pass tattoo on the forearm.", "action"),
    ("02:55:25", "Adds a troll-skin shirt. Broken left hand, scraped right hand, cut scalp.", "crouch"),
    ("03:48:19", "Jacket's right arm melted away, spiked knee pads of skin and fur, right arm red and bubbling.", "armsUp"),
    ("06:23:00", "Charcoal bracer on the wrist, one hand now a black angular gauntlet with gleaming spikes.", "action"),
    ("11:59:32", "White heart-print boxers, tactical boots, a dagger tattoo on the neck.", "stand"),
]
DONUT_LOOKS = [
    ("00:02:01", "Tortoiseshell Persian, warm brown coat, orange eyes. No collar or crown.", "sit"),
    ("02:46:05", "A small jeweled crown with smoky gems and a deep purple stone, a bell and a silver butterfly collar charm.", "pounce"),
    ("04:38:12", "Tiara, bell, butterfly charm, silver-scale armour over her back half, a gold mark over the right shoulder blade.", "loaf"),
]
MORDECAI_LOOKS = [
    ("00:37:17", "Bearded ratkin hooligan, a head shorter than a tall man. Black vest, blue pants, worn sandals.", "stand"),
    ("12:54:42", "Much larger obsidian bugaboo form: a neckless bear with enormous owlish eyes and comically skinny legs.", "bugaboo"),
]
SAMPLE_LOOKS = {
    "katya-grim": [("00:00:00", "Placeholder silhouette until the Mac bundles the real looks.", "stand"),
                   ("05:00:00", "Placeholder silhouette (second look).", "action")],
    "mongo": [("00:00:00", "Placeholder silhouette until the Mac bundles the real looks.", "stand"),
              ("06:00:00", "Placeholder silhouette (second look).", "run")],
    "samantha": [("00:00:00", "Placeholder silhouette until the Mac bundles the real looks.", "front"),
                 ("07:00:00", "Placeholder silhouette (second look).", "tilt")],
}


def sample_card(slug):
    """Sample stat reveals; shapes mirror the player-card pipeline's field-level entries."""
    if slug == "carl":
        return [
            e("race", 1, "00:01:33", "Human"),
            e("level", 1, "00:01:33", 1), e("floor", 1, "00:01:33", 1),
            e("str", 1, "00:40:00", 7), e("dex", 1, "00:40:00", 6), e("con", 1, "00:40:00", 7),
            e("int", 1, "00:40:00", 5), e("cha", 1, "00:40:00", 4),
            e("gear:feet", 1, "00:01:33", "Pink crocs"), e("gear:body", 1, "00:01:33", "Leather jacket"),
            e("level", 1, "00:52:00", 3), e("str", 1, "00:52:00", 8), e("con", 1, "00:52:00", 9),
            e("gear:feet", 1, "00:55:30", None),
            e("skill:Sample skill A", 1, "00:58:00", 1),
            e("level", 1, "01:45:00", 5), e("str", 1, "01:45:00", 10), e("con", 1, "01:45:00", 11),
            e("gear:head", 1, "01:47:16", "Hooded cloak"),
            e("skill:Sample skill B", 1, "02:10:00", 2),
            e("level", 1, "02:55:00", 7), e("gear:body", 1, "02:55:25", "Troll-skin shirt"),
            e("skill:Sample skill A", 1, "03:20:00", 3),
            e("level", 1, "03:48:00", 9), e("str", 1, "03:48:00", 12), e("dex", 1, "03:48:00", 8),
            e("gear:legs", 1, "03:48:19", "Spiked knee pads"),
            e("floor", 1, "05:30:00", 2),
            e("level", 1, "06:20:00", 11), e("int", 1, "06:20:00", 7),
            e("gear:hand", 1, "06:23:00", "Black spiked gauntlet"),
            e("class", 1, "08:00:00", "Sample class"),
            e("level", 1, "11:50:00", 13), e("con", 1, "11:50:00", 14), e("cha", 1, "11:50:00", 6),
            e("gear:feet", 1, "11:59:32", "Tactical boots"),
            e("floor", 1, "13:00:00", 3),
        ]
    if slug == "princess-donut":
        return [
            e("race", 1, "00:02:01", "Cat"),
            e("level", 1, "00:30:00", 1), e("floor", 1, "00:02:01", 1),
            e("str", 1, "00:45:00", 3), e("dex", 1, "00:45:00", 12), e("con", 1, "00:45:00", 4),
            e("int", 1, "00:45:00", 9), e("cha", 1, "00:45:00", 15),
            e("level", 1, "01:00:00", 4), e("skill:Sample skill C", 1, "01:10:00", 1),
            e("level", 1, "02:40:00", 8), e("cha", 1, "02:40:00", 18),
            e("gear:head", 1, "02:46:05", "Jeweled crown"),
            e("level", 1, "04:30:00", 10), e("dex", 1, "04:30:00", 14),
            e("gear:head", 1, "04:38:12", "Tiara"), e("gear:body", 1, "04:38:12", "Silver-scale armour"),
            e("floor", 1, "05:30:00", 2), e("class", 1, "08:00:00", "Sample class"),
            e("level", 1, "12:00:00", 14), e("floor", 1, "13:00:00", 3),
        ]
    if slug == "mordecai":
        return [e("race", 1, "00:37:17", "Ratkin"), e("floor", 1, "00:37:17", 1), e("floor", 1, "05:30:00", 2),
                e("race", 1, "12:54:42", "Bugaboo"), e("floor", 1, "13:00:00", 3)]
    return [e("floor", 1, "00:00:00", 1)]


def main():
    os.makedirs(DATA, exist_ok=True)
    books = json.load(open(os.path.join(DATA, "books.json")))["books"]
    index = {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sample": True,
        "books": books,
        "characters": [dict(c, file=f"data/{c['slug']}.json") for c in CHARACTERS],
    }
    with open(os.path.join(DATA, "index.json"), "w") as f:
        json.dump(index, f, indent=1)

    for c in CHARACTERS:
        slug = c["slug"]
        if slug == "carl":
            src = CARL_LOOKS
        elif slug == "princess-donut":
            src = DONUT_LOOKS
        elif slug == "mordecai":
            src = MORDECAI_LOOKS
        else:
            src = SAMPLE_LOOKS[slug]
        looks = [look(i + 1, 1, t, d, ph(slug, pose)) for i, (t, d, pose) in enumerate(src)]
        doc = {
            "slug": slug,
            "name": c["name"],
            "epithet": None,
            "sample": True,
            "realLookTimes": slug in ("carl", "princess-donut", "mordecai"),
            "looks": looks,
            "card": sample_card(slug),
        }
        with open(os.path.join(DATA, f"{slug}.json"), "w") as f:
            json.dump(doc, f, indent=1)
        print(slug, len(looks), "looks,", len(doc["card"]), "card entries")


if __name__ == "__main__":
    main()
