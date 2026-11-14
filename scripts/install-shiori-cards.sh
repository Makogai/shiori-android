#!/usr/bin/env bash
# Installs the runtime part of a Shiori Cards pack into app/src/main/assets/shiori_cards: manifest.json, every
# file it references, plus fonts/ and LICENSES.md when present. Previews, sources and READMEs stay out.
#
#   scripts/install-shiori-cards.sh <packdir>
#
# Works the same for the placeholder pack and the real one; the app reads only the manifest's paths.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <packdir>" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pack="$(cd "$1" && pwd)"
dest="$repo_root/app/src/main/assets/shiori_cards"

[[ -f "$pack/manifest.json" ]] || { echo "error: $pack/manifest.json not found" >&2; exit 1; }
case "$dest" in
  */app/src/main/assets/shiori_cards) ;;
  *) echo "error: refusing to clear unexpected destination '$dest'" >&2; exit 1 ;;
esac

files="$(python3 -I - "$pack/manifest.json" <<'PY'
import json, sys
seen = set()
def walk(v):
    if isinstance(v, dict):
        for x in v.values(): walk(x)
    elif isinstance(v, list):
        for x in v: walk(x)
    elif isinstance(v, str) and "/" in v and "." in v.rsplit("/", 1)[-1]:
        if v.startswith("/") or ".." in v.split("/"):
            sys.exit(f"error: unsafe path in manifest: {v}")
        seen.add(v)
walk(json.load(open(sys.argv[1])))
print("\n".join(sorted(seen)))
PY
)"

mkdir -p "$dest"
find "$dest" -mindepth 1 -delete

cp "$pack/manifest.json" "$dest/manifest.json"
count=0
missing=0
while IFS= read -r rel; do
  [[ -n "$rel" ]] || continue
  if [[ ! -f "$pack/$rel" ]]; then
    echo "warning: manifest references missing file $rel" >&2
    missing=$((missing + 1))
    continue
  fi
  mkdir -p "$dest/$(dirname "$rel")"
  cp "$pack/$rel" "$dest/$rel"
  count=$((count + 1))
done <<< "$files"

if [[ -d "$pack/fonts" ]]; then
  mkdir -p "$dest/fonts"
  cp -R "$pack/fonts/." "$dest/fonts/"
fi
if [[ -f "$pack/LICENSES.md" ]]; then
  cp "$pack/LICENSES.md" "$dest/LICENSES.md"
fi

echo "installed manifest + $count referenced files into $dest ($(du -sk "$dest" | cut -f1) KB)"
if [[ $missing -gt 0 ]]; then
  echo "error: $missing referenced files were missing" >&2
  exit 1
fi
