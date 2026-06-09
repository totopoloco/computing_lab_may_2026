#!/usr/bin/env bash
# toggle-calculator.sh
#
# Swaps Calculator.java between the healthy version and the demo version
# that burns CPU (recursive Fibonacci) and leaks memory (unbounded static list).
#
# Usage:
#   ./toggle-calculator.sh             # flip to the other mode
#   ./toggle-calculator.sh good        # switch to healthy
#   ./toggle-calculator.sh bad         # switch to degraded
#   ./toggle-calculator.sh status      # show current mode
#
# Optional flag (any position):
#   --build / -b   compile the project after switching (skips tests for speed)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/src/main/java/at/mavila/computing_lab_may_2026/domain/arithmetic/Calculator.java"
GOOD="$SCRIPT_DIR/demo/Calculator.good.java"
BAD="$SCRIPT_DIR/demo/Calculator.bad.java"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

current_mode() {
  if grep -q "DEMO-ONLY" "$TARGET" 2>/dev/null; then
    echo "bad"
  else
    echo "good"
  fi
}

show_status() {
  local mode
  mode=$(current_mode)
  if [ "$mode" = "good" ]; then
    echo -e "${GREEN}${BOLD}● GOOD${RESET}  Calculator is healthy — no synthetic load."
  else
    echo -e "${RED}${BOLD}● BAD${RESET}   Calculator is degraded — CPU burn + memory leak active."
  fi
}

do_switch() {
  local target_mode="$1"
  local source_file
  if [ "$target_mode" = "good" ]; then
    source_file="$GOOD"
  else
    source_file="$BAD"
  fi

  if [ ! -f "$source_file" ]; then
    echo -e "${RED}Error:${RESET} $source_file not found." >&2
    exit 1
  fi

  cp "$source_file" "$TARGET"
}

do_build() {
  echo ""
  echo -e "${YELLOW}Building (skipping tests)...${RESET}"
  cd "$SCRIPT_DIR"
  if ./gradlew build -x test --quiet 2>&1; then
    echo -e "${GREEN}Build successful.${RESET}"
  else
    echo -e "${RED}Build failed — check Gradle output above.${RESET}" >&2
    exit 1
  fi
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

cmd="toggle"
build=false

for arg in "$@"; do
  case "$arg" in
    toggle|good|bad|status) cmd="$arg" ;;
    --build|-b) build=true ;;
    *)
      echo "Usage: $0 [toggle|good|bad|status] [--build|-b]" >&2
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Execute
# ---------------------------------------------------------------------------

case "$cmd" in
  status)
    show_status
    ;;

  good)
    if [ "$(current_mode)" = "good" ]; then
      echo -e "${YELLOW}Already on GOOD — nothing to do.${RESET}"
    else
      do_switch good
      echo -e "${GREEN}${BOLD}Switched → GOOD${RESET}  Calculator is now healthy and performant."
      $build && do_build || true
    fi
    ;;

  bad)
    if [ "$(current_mode)" = "bad" ]; then
      echo -e "${YELLOW}Already on BAD — nothing to do.${RESET}"
    else
      do_switch bad
      echo -e "${RED}${BOLD}Switched → BAD${RESET}   CPU burn + memory leak are now active."
      $build && do_build || true
    fi
    ;;

  toggle)
    current=$(current_mode)
    if [ "$current" = "good" ]; then
      do_switch bad
      echo -e "${RED}${BOLD}Switched → BAD${RESET}   CPU burn + memory leak are now active."
    else
      do_switch good
      echo -e "${GREEN}${BOLD}Switched → GOOD${RESET}  Calculator is now healthy and performant."
    fi
    $build && do_build || true
    ;;
esac
