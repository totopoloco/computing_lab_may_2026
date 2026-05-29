#!/usr/bin/env bash
# compare-pitest.sh
# Runs pitest and JaCoCo twice — once scoped to CalculatorTest, once to
# CalculatorWeakTest — then prints a combined table showing that line coverage
# stays high while mutation score collapses with weak assertions.

set -euo pipefail

STRONG_CLASS="at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorTest"
WEAK_CLASS="at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorWeakTest"
REPORT_BASE="$(pwd)/build/reports"
STRONG_DIR="$REPORT_BASE/pitest-strong"
WEAK_DIR="$REPORT_BASE/pitest-weak"

mkdir -p "$REPORT_BASE"

# ── score helpers ─────────────────────────────────────────────────────────────

# Count occurrences of a literal token across all lines of a file.
count_token() { grep -o "$2" "$1" 2>/dev/null | wc -l | tr -d ' '; }

pitest_score() {
    local xml="$1/mutations.xml"
    local total killed
    total=$(count_token "$xml" '<mutation ')
    killed=$(count_token "$xml" "detected='true'")
    [ "$total" -eq 0 ] && echo "N/A" || echo $(( killed * 100 / total ))
}

pitest_summary() {
    local xml="$1/mutations.xml"
    local total killed survived
    total=$(count_token "$xml" '<mutation ')
    killed=$(count_token "$xml" "detected='true'")
    survived=$(( total - killed ))
    echo "Generated $total  |  Killed $killed  |  Survived $survived"
}

# The root-level LINE counter is always the LAST type="LINE" in the JaCoCo XML.
jacoco_line_score() {
    local xml="$1/jacoco/jacocoTestReport.xml"
    [ -f "$xml" ] || { echo "N/A"; return; }
    # Extract last occurrence: type="LINE" missed="N" covered="N"
    local last
    last=$(grep -o 'type="LINE" missed="[0-9]*" covered="[0-9]*"' "$xml" | tail -1)
    local missed covered
    missed=$(echo "$last"  | grep -o 'missed="[0-9]*"'  | grep -o '[0-9]*')
    covered=$(echo "$last" | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')
    local total=$(( missed + covered ))
    [ "$total" -eq 0 ] && echo "N/A" || echo $(( covered * 100 / total ))
}

# ── task runners ──────────────────────────────────────────────────────────────

run_pitest() {
    local label="$1" target="$2" threshold="$3" dest="$4"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  [pitest] $label"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ./gradlew pitest \
        --rerun \
        "-PpitestTargetTests=$target" \
        "-PpitestMutationThreshold=$threshold" \
        "-PpitestCoverageThreshold=$threshold"
    rm -rf "$dest"
    mkdir -p "$dest"
    cp -r build/reports/pitest/. "$dest/"
    echo "  Pitest  → $dest/index.html"
}

run_jacoco() {
    local label="$1" target="$2" dest="$3"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  [jacoco] $label"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    # cleanTest forces re-execution; finalizedBy in build.gradle runs jacocoTestReport automatically.
    ./gradlew cleanTest test --tests "${target}*"
    mkdir -p "$dest/jacoco"
    cp -r build/reports/jacoco/test/html/. "$dest/jacoco/"
    cp build/reports/jacoco/test/jacocoTestReport.xml "$dest/jacoco/"
    echo "  JaCoCo  → $dest/jacoco/index.html"
}

# ── runs ──────────────────────────────────────────────────────────────────────

run_pitest "CalculatorTest (strong)"   "$STRONG_CLASS" 79 "$STRONG_DIR"
run_jacoco "CalculatorTest (strong)"   "$STRONG_CLASS"    "$STRONG_DIR"

run_pitest "CalculatorWeakTest (weak)" "$WEAK_CLASS"    0 "$WEAK_DIR"
run_jacoco "CalculatorWeakTest (weak)" "$WEAK_CLASS"       "$WEAK_DIR"

# ── comparison table ──────────────────────────────────────────────────────────

S_PITEST=$(pitest_score   "$STRONG_DIR");  S_JACOCO=$(jacoco_line_score "$STRONG_DIR")
W_PITEST=$(pitest_score   "$WEAK_DIR");    W_JACOCO=$(jacoco_line_score "$WEAK_DIR")
S_SUMMARY=$(pitest_summary "$STRONG_DIR"); W_SUMMARY=$(pitest_summary  "$WEAK_DIR")

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║         JaCoCo Line Coverage  vs  Pitest Mutation Score           ║"
echo "╠════════════════════════════════════════════════════════════════════╣"
printf "║  %-34s  %13s  %14s  ║\n" "Test suite" "JaCoCo lines" "Pitest mutants"
echo "╠════════════════════════════════════════════════════════════════════╣"
printf "║  %-34s  %12s%%  %13s%%  ║\n" "CalculatorTest      (strong)" "$S_JACOCO" "$S_PITEST"
printf "║  %-34s  %12s%%  %13s%%  ║\n" "CalculatorWeakTest  (weak)"   "$W_JACOCO" "$W_PITEST"
echo "╠════════════════════════════════════════════════════════════════════╣"
printf "║  Mutations (strong): %-47s  ║\n" "$S_SUMMARY"
printf "║  Mutations (weak):   %-47s  ║\n" "$W_SUMMARY"
echo "╠════════════════════════════════════════════════════════════════════╣"
echo "║  Reports (open index.html or jacoco/index.html):                  ║"
printf "║    %-63s  ║\n" "build/reports/pitest-strong/{index.html, jacoco/index.html}"
printf "║    %-63s  ║\n" "build/reports/pitest-weak/{index.html,   jacoco/index.html}"
echo "╚════════════════════════════════════════════════════════════════════╝"
