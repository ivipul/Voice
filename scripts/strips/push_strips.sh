#!/usr/bin/env bash
set -euo pipefail

PKG="${PKG:-de.ph1b.audiobook}"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SRC="$ROOT/audiobook-copilot/storyboards/book1"
DEST="/sdcard/Android/data/$PKG/files/strips/book1"

pushed=0
for dir in "$SRC"/ch*-comic; do
  [ -f "$dir/strip.json" ] || continue
  chapter="$(basename "$dir" -comic)"
  frames="$dir"
  [ -d "$dir/final" ] && frames="$dir/final"
  images="$(python3 -c 'import json,sys; print("\n".join(f["image"] for f in json.load(open(sys.argv[1]))["frames"]))' "$dir/strip.json")"
  strip_image="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("strip_image") or "")' "$dir/strip.json")"
  complete=1
  while IFS= read -r image; do
    [ -f "$frames/$image" ] || { echo "skip $chapter: missing $image"; complete=0; }
  done <<< "$images"
  [ "$complete" -eq 1 ] || continue

  adb shell mkdir -p "$DEST/$chapter"
  adb push "$dir/strip.json" "$DEST/$chapter/strip.json" >/dev/null
  while IFS= read -r image; do
    adb push "$frames/$image" "$DEST/$chapter/$image" >/dev/null
  done <<< "$images"
  if [ -n "$strip_image" ]; then
    if [ -f "$frames/$strip_image" ]; then
      adb push "$frames/$strip_image" "$DEST/$chapter/$strip_image" >/dev/null
    elif [ -f "$dir/$strip_image" ]; then
      adb push "$dir/$strip_image" "$DEST/$chapter/$strip_image" >/dev/null
    else
      echo "note $chapter: strip image $strip_image not found, the app will ignore it"
    fi
  fi
  echo "pushed $chapter"
  pushed=$((pushed + 1))
done
echo "done: $pushed chapter(s) pushed to $DEST"
