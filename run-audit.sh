#!/usr/bin/env bash
# MeraPaisa review loop. 10 runs, fresh session each time.
# Usage:  ./run-audit.sh        runs 1-10
#         ./run-audit.sh 2      runs 2-10 (after doing run 1 by hand)

set -u

START="${1:-1}"
TOTAL=10

if [ ! -f loop-audit/PROTOCOL.md ]; then
  echo "loop-audit/PROTOCOL.md not found. Run this from the project root."
  exit 1
fi

if [ "$(adb devices | grep -cw device)" -ne 1 ]; then
  echo "Need exactly one connected device. Check 'adb devices'."
  exit 1
fi

for N in $(seq "$START" "$TOTAL"); do
  echo ""
  echo "=== run $N of $TOTAL  ($(date '+%H:%M:%S')) ==="

  claude -p "Read loop-audit/PROTOCOL.md and loop-audit/ledger.md, then do run $N. Stop when run $N is complete." \
    --permission-mode acceptEdits \
    --allowedTools "Bash(./gradlew *),Bash(adb *),Bash(cp *),Bash(mkdir *),Bash(git status *),Bash(git diff *),Read,Edit,Write,Glob,Grep" \
    > "loop-audit/run-$N.log" 2>&1

  if [ $? -ne 0 ]; then
    echo "run $N failed — stopping. See loop-audit/run-$N.log"
    exit 1
  fi

  tail -5 "loop-audit/run-$N.log"
done

echo ""
echo "all runs done. Read loop-audit/ledger.md"
