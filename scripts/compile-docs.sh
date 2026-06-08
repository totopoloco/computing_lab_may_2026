#!/usr/bin/env bash
# Compiles every Markdown file in the repository to PDF via pandoc + XeLaTeX.
# Output files land alongside their sources with a .pdf extension.
# Usage: ./scripts/compile-docs.sh [--output-dir <dir>]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Optional --output-dir flag; default: same directory as each source file
OUTPUT_DIR=""
if [[ "${1:-}" == "--output-dir" && -n "${2:-}" ]]; then
  OUTPUT_DIR="$2"
  mkdir -p "$OUTPUT_DIR"
fi

# Files that exist only to drive tooling, not human documentation
EXCLUDE_PATTERNS=(
  "CLAUDE.md"
)

is_excluded() {
  local file="$1"
  local base
  base="$(basename "$file")"
  for pattern in "${EXCLUDE_PATTERNS[@]}"; do
    [[ "$base" == "$pattern" ]] && return 0
  done
  return 1
}

mapfile -t MD_FILES < <(
  find "$REPO_ROOT" -name "*.md" \
    ! -path "*/.git/*" \
    ! -path "*/node_modules/*" \
    | sort
)

compiled=0
failed=0

for src in "${MD_FILES[@]}"; do
  if is_excluded "$src"; then
    echo "  skip  $(realpath --relative-to="$REPO_ROOT" "$src")"
    continue
  fi

  if [[ -n "$OUTPUT_DIR" ]]; then
    dest="$OUTPUT_DIR/$(basename "${src%.md}").pdf"
  else
    dest="${src%.md}.pdf"
  fi

  rel="$(realpath --relative-to="$REPO_ROOT" "$src")"
  src_dir="$(dirname "$src")"
  src_file="$(basename "$src")"
  # Run from the source file's directory so relative image paths (images/*.jpg) resolve correctly.
  # DejaVu fonts cover a wide Unicode range (≥ ≤ × → etc.) without warnings.
  if (cd "$src_dir" && pandoc "$src_file" \
       --pdf-engine=xelatex \
       --variable geometry:margin=2.5cm \
       --variable fontsize=11pt \
       --variable colorlinks=true \
       --variable mainfont="DejaVu Serif" \
       --variable monofont="DejaVu Sans Mono" \
       -o "$dest" 2>/tmp/pandoc-err); then
    echo "    ok  $rel → $(realpath --relative-to="$REPO_ROOT" "$dest")"
    (( compiled++ )) || true
  else
    echo "  FAIL  $rel"
    cat /tmp/pandoc-err
    (( failed++ )) || true
  fi
done

echo ""
echo "Done: $compiled compiled, $failed failed."
[[ $failed -eq 0 ]]
