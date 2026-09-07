#!/usr/bin/env bash
# MeraPaisa audit loop driver.
# Usage:  ./run-audit.sh        start at loop 1
#         ./run-audit.sh 2      start at loop 2 (after running loop 1 by hand)

set -u

START="${1:-1}"
MAX=25

if [ ! -f loop-audit/PROTOCOL.md ]; then
  echo "loop-audit/PROTOCOL.md not found. Run this from the project root."
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is not installed. Run: brew install jq"
  exit 1
fi

if [ "$(adb devices | grep -cw device)" -ne 1 ]; then
  echo "Need exactly one connected device. Check 'adb devices'."
  exit 1
fi

mkdir -p loop-audit

N=$((START - 1))
while [ "$N" -lt "$MAX" ]; do
  N=$((N + 1))
  echo ""
  echo "=== loop $N  ($(date '+%H:%M:%S')) ==="

  claude -p "Read loop-audit/PROTOCOL.md, loop-audit/ledger.md, and loop-audit/STATE.json. Execute loop $N only, then rewrite STATE.json. Stop when loop $N is complete." \
    --permission-mode acceptEdits \
    --allowedTools "Bash(./gradlew *),Bash(adb *),Bash(cp *),Bash(mkdir *),Bash(git status *),Bash(git diff *),Read,Edit,Write,Glob,Grep" \
    --output-format json > "loop-audit/loop-$N-run.json"

  if [ $? -ne 0 ]; then
    echo "loop $N failed — stopping. See loop-audit/loop-$N-run.json"
    break
  fi

  CONV=$(jq -r '.converged // "error"' loop-audit/STATE.json 2>/dev/null)
  case "$CONV" in
    true)
      echo "converged after $N loops — see loop-audit/summary.md"
      break
      ;;
    false)
      jq -r '"  clean streak: \(.consecutive_clean_loops) | new P0/P1: \(.new_p0_p1_this_loop) | \(.reason)"' \
        loop-audit/STATE.json
      ;;
    *)
      echo "STATE.json is missing or unreadable — stopping so it can be checked by hand."
      break
      ;;
  esac
done

if [ "$N" -ge "$MAX" ]; then
  echo "hit the loop ceiling ($MAX) without converging — see loop-audit/summary.md"
fi
