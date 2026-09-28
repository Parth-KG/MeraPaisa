#!/usr/bin/env bash
# Rebuilds the bundled fonts from their variable sources.
#
# Static instances, not variable fonts: API 24 and 25 ignore variation axes, so a variable file
# would render at its default weight on those devices and every amount would stop being bold.
# Subset to the characters the app can actually show, which is what keeps six faces near 210 KB.
#
# Needs: pip install fonttools
# Sources: github.com/google/fonts, both OFL. Licences are committed under third_party/fonts/.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
OUT="$ROOT/app/src/main/res/font"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

BASE=https://raw.githubusercontent.com/google/fonts/main/ofl
# Latin, punctuation, the currency symbols the app formats, and U+2212 for a real minus sign.
UNICODES="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+20B9,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"

echo "fetching sources"
curl -sL -o "$WORK/AnekLatin.ttf" "$BASE/aneklatin/AnekLatin%5Bwdth%2Cwght%5D.ttf"
curl -sL -o "$WORK/Figtree.ttf"   "$BASE/figtree/Figtree%5Bwght%5D.ttf"

mkdir -p "$OUT"

# name                               source        axes
build() {
  local out="$1" src="$2" axes="$3"
  fonttools varLib.instancer "$WORK/$src" $axes --update-name-table -o "$WORK/tmp.ttf" >/dev/null
  pyftsubset "$WORK/tmp.ttf" --layout-features='*' --output-file="$OUT/$out" --unicodes="$UNICODES"
  printf '  %-42s %6s bytes\n' "$out" "$(wc -c < "$OUT/$out" | tr -d ' ')"
}

echo "building"
# Amounts: semicondensed, so a long lakh figure keeps its weight without eating the row.
build anek_latin_semicondensed_semibold.ttf AnekLatin.ttf "wght=600 wdth=87.5"
build anek_latin_semicondensed_bold.ttf     AnekLatin.ttf "wght=700 wdth=87.5"
# Screen titles: normal width, which reads better at large sizes than the condensed cut.
build anek_latin_semibold.ttf               AnekLatin.ttf "wght=600 wdth=100"
# Everything else.
build figtree_regular.ttf  Figtree.ttf "wght=400"
build figtree_medium.ttf   Figtree.ttf "wght=500"
build figtree_semibold.ttf Figtree.ttf "wght=600"

echo "total: $(cat "$OUT"/*.ttf | wc -c | tr -d ' ') bytes"
