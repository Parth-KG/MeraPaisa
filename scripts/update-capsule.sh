#!/usr/bin/env bash
#
# Refreshes the auto-generated block inside the Mera Paisa context capsule.
#
#   ~/Desktop/Capsules/MeraPaisa_context_capsule.md
#
# Run from a git post-commit hook. Only the text between CAPSULE:AUTO:BEGIN and CAPSULE:AUTO:END is
# rewritten; the hand-written narrative around it is never touched.
#
# Installing the hook (needed once per clone, since .git/hooks is not version-controlled, which is why
# this script lives in the repo rather than inside .git):
#
#   cat > .git/hooks/post-commit <<'SH'
#   #!/usr/bin/env bash
#   s="$(git rev-parse --show-toplevel 2>/dev/null)/scripts/update-capsule.sh"
#   [ -x "$s" ] && exec "$s"
#   exit 0
#   SH
#   chmod +x .git/hooks/post-commit
#
# The -x guard matters: without it, a non-executable script makes git print two "Permission denied"
# lines after every single commit. Harmless, but you would come to hate it.
#
# Two rules this script must never break:
#
#   1. It cannot fail a commit. post-commit cannot abort one, but a noisy error on every commit is
#      its own kind of breakage, so every failure path here is a quiet no-op that still exits 0.
#   2. It never runs Gradle. Test counts are read from whatever the last build left behind and are
#      stamped with that build's time. A commit that took four minutes would not survive contact
#      with actual use.

set -uo pipefail

CAPSULE="${HOME}/Desktop/Capsules/MeraPaisa_context_capsule.md"
BEGIN='<!-- CAPSULE:AUTO:BEGIN -->'
END='<!-- CAPSULE:AUTO:END -->'

REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$REPO" 2>/dev/null || exit 0

# ---------------------------------------------------------------------------------------------
# Facts. Every one of these degrades to a placeholder rather than an empty section.
# ---------------------------------------------------------------------------------------------

now() { date "+%Y-%m-%d %H:%M"; }

version_name="$(grep -m1 'versionName = ' app/build.gradle.kts 2>/dev/null | sed 's/.*"\(.*\)".*/\1/')"
version_code="$(grep -m1 'versionCode = ' app/build.gradle.kts 2>/dev/null | sed 's/[^0-9]//g')"
schema="$(grep -m1 -A1 'version = ' app/src/main/java/com/kg/merapaisa/data/AppDatabase.kt 2>/dev/null | grep -o 'version = [0-9]*' | grep -o '[0-9]*')"
head_line="$(git log -1 --format='%h %s' 2>/dev/null)"
head_date="$(git log -1 --format='%ad' --date=format:'%Y-%m-%d %H:%M' 2>/dev/null)"
branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
dirty="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
tags="$(git tag -l 'v*' 2>/dev/null | sort -V | tr '\n' ' ')"
commits="$(git log -10 --format='- `%h` %s' 2>/dev/null)"
kt_count="$(find app/src/main/java -name '*.kt' 2>/dev/null | wc -l | tr -d ' ')"

# Test counts, read from the last build rather than produced by one.
read -r unit_line < <(
  python3 - <<'PY' 2>/dev/null || echo "unavailable"
import glob, re, io, os, time
files = glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
if not files:
    print("not run since the last clean"); raise SystemExit
t = f = 0
for p in files:
    s = io.open(p, encoding='utf-8', errors='replace').read()
    m = re.search(r'tests="(\d+)"', s); n = re.search(r'failures="(\d+)"', s)
    if m: t += int(m.group(1))
    if n: f += int(n.group(1))
when = time.strftime('%Y-%m-%d %H:%M', time.localtime(max(os.path.getmtime(p) for p in files)))
print(f"{t} tests, {f} failures (as of the build at {when})")
PY
)

instr_count="$(grep -rho '@Test' app/src/androidTest 2>/dev/null | wc -l | tr -d ' ')"

# ---------------------------------------------------------------------------------------------
# Render
# ---------------------------------------------------------------------------------------------

block="$(cat <<EOF
*Regenerated automatically on commit. Do not edit by hand. Last run $(now).*

| | |
|---|---|
| **Version** | ${version_name:-?} (versionCode ${version_code:-?}) |
| **Room schema** | ${schema:-?} |
| **Branch** | ${branch:-?}${dirty:+ · ${dirty} uncommitted file(s)} |
| **HEAD** | \`${head_line:-?}\` (${head_date:-?}) |
| **Kotlin sources** | ${kt_count:-?} files in \`app/src/main/java\` |
| **Unit tests** | ${unit_line:-unavailable} |
| **Instrumentation** | ${instr_count:-?} \`@Test\` methods (run on a device, not by this hook) |

**Tags:** ${tags:-none}

**Last 10 commits**

${commits:-unavailable}
EOF
)"

# ---------------------------------------------------------------------------------------------
# Splice. Recreates the capsule if it has been deleted, so losing it is recoverable.
# ---------------------------------------------------------------------------------------------

if [ ! -f "$CAPSULE" ]; then
  mkdir -p "$(dirname "$CAPSULE")" 2>/dev/null || exit 0
  {
    echo "# Context Capsule: Mera Paisa (Android app)"
    echo
    echo "**How to use this:** Paste this whole file at the start of a new chat and say"
    echo "\"continue from this.\""
    echo
    echo "> This file was regenerated as a stub because it was missing. The hand-written narrative"
    echo "> The narrative half, meaning what the app is, the design decisions and their reasons,"
    echo "> the shipped history and the release plan, is **not** recoverable from git."
    echo
    echo "$BEGIN"
    echo "$END"
  } > "$CAPSULE" 2>/dev/null || exit 0
fi

grep -q "$BEGIN" "$CAPSULE" 2>/dev/null || exit 0
grep -q "$END" "$CAPSULE" 2>/dev/null || exit 0

tmp="$(mktemp 2>/dev/null)" || exit 0
BLOCK="$block" BEGIN="$BEGIN" END="$END" python3 - "$CAPSULE" > "$tmp" <<'PY' 2>/dev/null || { rm -f "$tmp"; exit 0; }
import io, os, sys
path = sys.argv[1]
begin, end, block = os.environ['BEGIN'], os.environ['END'], os.environ['BLOCK']
s = io.open(path, encoding='utf-8').read()

# Match the LAST pair, not the first. The capsule's own prose describes these markers, and an
# earlier version of this script spliced the generated block into the middle of that sentence,
# destroying the explanation of how the file works. The narrative is not recoverable from git, so
# this is the one bug here that would genuinely cost something.
i = s.rfind(begin)
j = s.rfind(end)
if i < 0 or j < 0 or j < i:
    raise SystemExit(1)

sys.stdout.write(s[:i + len(begin)] + "\n" + block + "\n" + s[j:])
PY

# Only replace if something was actually produced. Never truncate the capsule on a failure.
if [ -s "$tmp" ]; then
  cat "$tmp" > "$CAPSULE" 2>/dev/null
  echo "capsule updated: ${CAPSULE/#$HOME/~}"
fi
rm -f "$tmp"
exit 0
