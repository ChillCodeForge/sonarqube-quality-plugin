#!/usr/bin/env bash
set -Eeuo pipefail

# Upload mutation testing report to SonarQube ChillCode Quality Plugin
# Usage: upload-mutation-report.sh <project-key> <branch> <commit> <normalized-report.json.gz> [sonar-url] [sonar-token]

PROJECT_KEY="${1:-}"
BRANCH="${2:-main}"
COMMIT="${3:-$(git rev-parse HEAD)}"
REPORT_FILE="${4:-}"
SONAR_URL="${5:-https://sonar.chillcode.de}"
SONAR_TOKEN="${6:-}"

if [ -z "$PROJECT_KEY" ] || [ -z "$REPORT_FILE" ]; then
  echo "Usage: $0 <project-key> <branch> <commit> <normalized-report.json.gz> [sonar-url] [sonar-token]"
  exit 1
fi

if [ ! -f "$REPORT_FILE" ]; then
  echo "Report file not found: $REPORT_FILE"
  exit 1
fi

if [ -z "$SONAR_TOKEN" ]; then
  echo "SONAR_TOKEN not provided"
  exit 1
fi

echo "Uploading mutation report to SonarQube..."
echo "  Project: $PROJECT_KEY"
echo "  Branch: $BRANCH"
echo "  Commit: $COMMIT"
echo "  Report: $REPORT_FILE"

# Upload via Storage API - the "report" param must be a named multipart
# form field (SonarQube's request.paramAsInputStream("report") reads a
# multipart part by name, not the raw request body); sending the gzip as
# --data-binary silently leaves the "report" param unset and crashes the
# server with a NullPointerException.
UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Authorization: Bearer $SONAR_TOKEN" \
  -F "report=@${REPORT_FILE};type=application/gzip" \
  "$SONAR_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=$BRANCH&commit=$COMMIT")

HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$UPLOAD_RESPONSE" | head -n -1)

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 201 ]; then
  echo "✓ Report uploaded successfully"
  echo "$RESPONSE_BODY"
else
  echo "✗ Upload failed (HTTP $HTTP_CODE)"
  echo "$RESPONSE_BODY"
  exit 1
fi

# Also push measures via SonarScanner if running in CI
if [ -n "${CI:-}" ] && command -v sonar-scanner >/dev/null 2>&1; then
  echo "Running SonarScanner to push measures..."
  # Extract key metrics from report for SonarScanner
  REPORT_JSON=$(zcat "$REPORT_FILE")
  SCORE=$(echo "$REPORT_JSON" | jq -r '.summary.score')
  TOTAL=$(echo "$REPORT_JSON" | jq -r '.summary.total')
  KILLED=$(echo "$REPORT_JSON" | jq -r '.summary.killed')
  SURVIVED=$(echo "$REPORT_JSON" | jq -r '.summary.survived')
  NO_COVERAGE=$(echo "$REPORT_JSON" | jq -r '.summary.noCoverage')
  TIMEOUT=$(echo "$REPORT_JSON" | jq -r '.summary.timeout')
  IGNORED=$(echo "$REPORT_JSON" | jq -r '.summary.ignored')
  TOOL=$(echo "$REPORT_JSON" | jq -r '.tool')
  LANGUAGE=$(echo "$REPORT_JSON" | jq -r '.language')

  sonar-scanner \
    -Dsonar.host.url="$SONAR_URL" \
    -Dsonar.token="$SONAR_TOKEN" \
    -Dsonar.projectKey="$PROJECT_KEY" \
    -Dsonar.chillcode.mutationScore="$SCORE" \
    -Dsonar.chillcode.mutationTotal="$TOTAL" \
    -Dsonar.chillcode.mutationKilled="$KILLED" \
    -Dsonar.chillcode.mutationSurvived="$SURVIVED" \
    -Dsonar.chillcode.mutationNoCoverage="$NO_COVERAGE" \
    -Dsonar.chillcode.mutationTimeout="$TIMEOUT" \
    -Dsonar.chillcode.mutationIgnored="$IGNORED" \
    -Dsonar.chillcode.mutationTool="$TOOL" \
    -Dsonar.chillcode.mutationLanguage="$LANGUAGE" \
    || echo "Warning: SonarScanner measures push failed (measures already stored via API)"
fi

echo "Done."