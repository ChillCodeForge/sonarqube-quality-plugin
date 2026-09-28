#!/usr/bin/env bash
set -Eeuo pipefail

# Prepare a report accepted by the ChillCode SonarQube Quality Plugin.
#
# Usage:
#   normalize-mutation-report.sh <tool> <input-path> <output-file>
#     [project-key] [branch] [commit] [source-root]
#
# Inputs:
#   stryker  path to Stryker mutation.json
#   pitest   path to PITest mutations.xml
#   cargo-mutants  path to cargo-mutants' mutants.out/outcomes.json
#   mutmut   path to mutmut's mutants/ directory; source-root is required
#
# Stryker JSON, PITest XML and cargo-mutants' outcomes.json are already
# accepted directly by the plugin's upload endpoint, so this wrapper only
# compresses them. mutmut needs the
# bundled Python converter because its raw artifacts are not a stable upload
# format for the plugin.

TOOL="${1:-}"
INPUT_PATH="${2:-}"
OUTPUT_FILE="${3:-}"
PROJECT_KEY="${4:-}"
BRANCH="${5:-main}"
COMMIT="${6:-$(git rev-parse HEAD)}"
SOURCE_ROOT="${7:-}"
SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$TOOL" || -z "$INPUT_PATH" || -z "$OUTPUT_FILE" ]]; then
  echo "Usage: $0 <tool> <input-path> <output-file> [project-key] [branch] [commit] [source-root]" >&2
  exit 2
fi

PROJECT_KEY="${PROJECT_KEY:-$(basename "$(git rev-parse --show-toplevel)")}"

case "$TOOL" in
  stryker|pitest|cargo-mutants)
    if [[ ! -f "$INPUT_PATH" ]]; then
      echo "Input file not found: $INPUT_PATH" >&2
      exit 1
    fi
    gzip -c -- "$INPUT_PATH" > "${OUTPUT_FILE}.gz"
    ;;
  mutmut)
    if [[ ! -d "$INPUT_PATH" ]]; then
      echo "mutmut input directory not found: $INPUT_PATH" >&2
      exit 1
    fi
    if [[ -z "$SOURCE_ROOT" || ! -d "$SOURCE_ROOT" ]]; then
      echo "mutmut requires an existing source-root as argument 7" >&2
      exit 2
    fi
    python3 "$SCRIPT_DIRECTORY/mutmut_to_stryker.py" \
      "$INPUT_PATH" "$SOURCE_ROOT" "$PROJECT_KEY" \
      | gzip -c > "${OUTPUT_FILE}.gz"
    ;;
  *)
    echo "Unknown tool: $TOOL (supported: stryker, pitest, cargo-mutants, mutmut)" >&2
    exit 2
    ;;
esac

printf 'Prepared report: %s\n' "${OUTPUT_FILE}.gz"
