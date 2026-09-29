#!/usr/bin/env bash
cd "$(git rev-parse --show-toplevel)" || exit 2
SRC=app/src/main/java/com/kg/merapaisa
FROZEN='^app/src/(main|test|androidTest)/java/com/kg/merapaisa/(data|repository)/'
hits=0
report() { if [ -n "$2" ]; then printf '== %s\n%s\n' "$1" "$2"; hits=1; fi; }
# "oops" carries a hand-written word boundary because it otherwise matches "loops", and \b is
# not supported by git grep -E here: it matches nothing at all, which would switch the check off.
# This script is excluded from its own scan: the em dash check spells out the HTML entities it
# looks for, so it would match itself and could never print nothing.
files=$(git ls-files --cached --others --exclude-standard | grep -Ev "$FROZEN" | grep -Evi '\.(webp|png|jpe?g|jar|ttf)$' | grep -v '^scripts/ui-lint\.sh$')
report "em dash" "$(printf '%s\n' "$files" | tr '\n' '\0' | xargs -0 -r perl -ne 'print "$ARGV:$.: $_" if /\xE2\x80\x94|&#8212;|&mdash;/; close ARGV if eof')"
report "emoji" "$(printf '%s\n' "$files" | grep -E '\.(kt|xml|md|html|svg)$' | tr '\n' '\0' | xargs -0 -r perl -CSD -ne 'print "$ARGV:$.: $_" if /[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]/; close ARGV if eof')"
report "raw fontSize" "$(git grep -nE 'fontSize[[:space:]]*=' -- "$SRC/ui" ':!*/theme/*')"
report "raw corner radius" "$(git grep -n 'RoundedCornerShape(' -- "$SRC" ':!*/theme/*')"
report "raw spacing" "$(git grep -nE '(padding|spacedBy)\([^)]*[0-9]\.dp' -- "$SRC/ui")"
report "Card composable" "$(git grep -nE '(^|[^A-Za-z])(Elevated|Outlined)?Card\(' -- "$SRC")"
report "formatMinor on screen" "$(git grep -n 'formatMinor(' -- "$SRC/ui" "$SRC/widget")"
report "default font or raw colour" "$(git grep -n -e 'FontFamily.Default' -e 'Color(0x' -- "$SRC" ':!*/AppTheme.kt')"
report "vague button" "$(git grep -nE '"(Submit|OK|Ok|Yes|No)"' -- "$SRC/ui" "$SRC/widget")"
report "banned words" "$(git grep -niE 'seamless|effortless|powerful|robust|leverage|empower|(^|[^a-z])oops|something went wrong|please try again|friendly reminder' -- "$SRC" README.md docs ':!*/data/*' ':!*/repository/*')"
exit $hits
