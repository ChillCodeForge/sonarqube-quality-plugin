#!/usr/bin/env bash
set -Eeuo pipefail

# Normalize mutation testing reports to ChillCode schema v1
# Usage: normalize-mutation-report.sh <tool> <input-file> <output-file> [project-key] [branch] [commit]

TOOL="${1:-}"
INPUT_FILE="${2:-}"
OUTPUT_FILE="${3:-}"
PROJECT_KEY="${4:-}"
BRANCH="${5:-main}"
COMMIT="${6:-$(git rev-parse HEAD)}"

if [ -z "$TOOL" ] || [ -z "$INPUT_FILE" ] || [ -z "$OUTPUT_FILE" ]; then
  echo "Usage: $0 <tool> <input-file> <output-file> [project-key] [branch] [commit]"
  echo "Tools: stryker, pitest, mutmut"
  exit 1
fi

if [ ! -f "$INPUT_FILE" ]; then
  echo "Input file not found: $INPUT_FILE"
  exit 1
fi

PROJECT_KEY="${PROJECT_KEY:-$(basename "$(git rev-parse --show-toplevel)")}"

normalize_stryker() {
  local input="$1"
  local output="$2"
  local project="$3"
  local branch="$4"
  local commit="$5"

  # Use the official stryker-to-sonar jq filter if available, otherwise basic conversion
  if command -v jq >/dev/null 2>&1; then
    jq -c --arg tool "stryker" \
      --arg language "typescript" \
      --arg project "$project" \
      --arg branch "$branch" \
      --arg commit "$commit" \
      --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
      {
        schemaVersion: 1,
        tool: $tool,
        language: $language,
        project: $project,
        branch: $branch,
        commit: $commit,
        timestamp: $timestamp,
        summary: {
          total: (.totals.mutants | length),
          killed: (.totals.killed | length),
          survived: (.totals.survived | length),
          noCoverage: (.totals.noCoverage | length),
          timeout: (.totals.timeout | length),
          ignored: (.totals.ignored | length),
          score: (if .totals.mutants > 0 then (.totals.killed / .totals.mutants * 100) else 0 end)
        },
        files: (
          .files | to_entries | map({
            path: .key,
            language: "typescript",
            mutants: .value.mutants | map({
              id: .id,
              mutatorName: .mutatorName,
              replacement: .replacement,
              status: .status | upper,
              statusReason: .statusReason,
              location: {
                start: {line: .location.start.line, column: .location.start.column},
                end: {line: .location.end.line, column: .location.end.column}
              },
              coveredBy: .coveredBy,
              static: .static
            })
          })
        ),
        mutants: (
          .files | to_entries | map(.value.mutants[]) | flatten
        )
      }' "$input" > "$output"
  else
    echo "jq not available, cannot normalize Stryker report"
    exit 1
  fi
}

normalize_pitest() {
  local input="$1"
  local output="$2"
  local project="$3"
  local branch="$4"
  local commit="$5"

  # PITest XML to JSON conversion
  # Requires xmlstarlet or similar
  if command -v xmlstarlet >/dev/null 2>&1; then
    # Convert XML to JSON first, then normalize
    echo "PITest normalization not fully implemented - use Mutation Analysis Plugin instead"
    exit 1
  else
    echo "xmlstarlet not available for PITest normalization"
    exit 1
  fi
}

normalize_mutmut() {
  local input="$1"
  local output="$2"
  local project="$3"
  local branch="$4"
  local commit="$5"

  # mutmut results to JSON
  if command -v mutmut >/dev/null 2>&1 && command -v jq >/dev/null 2>&1; then
    mutmut results --json > /tmp/mutmut-results.json 2>/dev/null || true
    if [ -f /tmp/mutmut-results.json ]; then
      jq -c --arg tool "mutmut" \
        --arg language "python" \
        --arg project "$project" \
        --arg branch "$branch" \
        --arg commit "$commit" \
        --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
        {
          schemaVersion: 1,
          tool: $tool,
          language: $language,
          project: $project,
          branch: $branch,
          commit: $commit,
          timestamp: $timestamp,
          summary: {
            total: (. | length),
            killed: (map(select(.status == "killed")) | length),
            survived: (map(select(.status == "survived")) | length),
            noCoverage: (map(select(.status == "no_coverage")) | length),
            timeout: (map(select(.status == "timeout")) | length),
            ignored: 0,
            score: (if length > 0 then (map(select(.status == "killed")) | length / length * 100) else 0 end)
          },
          files: (
            group_by(.filename) | map({
              path: .[0].filename,
              language: "python",
              mutants: map({
                id: (.id | tostring),
                mutatorName: .mutator,
                replacement: .mutated_code,
                status: .status | upper,
                statusReason: .reason,
                location: {
                  start: {line: .line_number, column: 1},
                  end: {line: .line_number, column: 1}
                },
                coveredBy: [],
                static: false
              })
            })
          ),
          mutants: (map({
            id: (.id | tostring),
            mutatorName: .mutator,
            replacement: .mutated_code,
            status: .status | upper,
            statusReason: .reason,
            location: {
              start: {line: .line_number, column: 1},
              end: {line: .line_number, column: 1}
            },
            coveredBy: [],
            static: false
          }))
        }' /tmp/mutmut-results.json > "$output"
    else
      echo "mutmut results not available"
      exit 1
    fi
  else
    echo "mutmut or jq not available"
    exit 1
  fi
}

case "$TOOL" in
  stryker)
    normalize_stryker "$INPUT_FILE" "$OUTPUT_FILE" "$PROJECT_KEY" "$BRANCH" "$COMMIT"
    ;;
  pitest)
    normalize_pitest "$INPUT_FILE" "$OUTPUT_FILE" "$PROJECT_KEY" "$BRANCH" "$COMMIT"
    ;;
  mutmut)
    normalize_mutmut "$INPUT_FILE" "$OUTPUT_FILE" "$PROJECT_KEY" "$BRANCH" "$COMMIT"
    ;;
  *)
    echo "Unknown tool: $TOOL"
    echo "Supported tools: stryker, pitest, mutmut"
    exit 1
    ;;
esac

# Compress output
gzip -f "$OUTPUT_FILE"
echo "Normalized report written to: ${OUTPUT_FILE}.gz"