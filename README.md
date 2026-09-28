# ChillCode SonarQube Quality Plugin

SonarQube plugin for mutation-testing data. It adds mutation metrics, a quality dashboard, a mutation-results page, report storage, an SVG badge endpoint, and an upload API for CI.

## Supported report inputs

| Report source | Upload input | Conversion needed |
|---|---|---|
| Stryker | Raw `mutation.json` | No. The plugin parses Stryker JSON directly. |
| PITest | Raw `mutations.xml` | No. The plugin parses PITest XML directly. |
| cargo-mutants | Raw `mutants.out/outcomes.json` | No. The plugin parses it directly; unviable mutants are left out of the score, as PITest's non-viable ones are. |
| mutant (Ruby) 0.16+ | A session file from `.mutant/results/` | No. The plugin parses it directly; only `evil` mutations count, and locations are accurate at method granularity. |
| mutmut 3.x | `mutants/` artifacts from `mutmut run` | Yes. Use the bundled `scripts/mutmut_to_stryker.py` converter or wrapper. |
| Stryker-compatible JSON | JSON with `schemaVersion` and `files` | No. The plugin parses it directly. |

Raw mutmut JSON is **not** accepted. The bundled converter maps mutmut's on-disk metadata to the Stryker-compatible shape already handled by the plugin. mutmut does not retain one source location per mutation, so reported locations are accurate at function granularity.

## Compatibility

The Maven build targets Java 21 and SonarQube plugin API `13.5.0.4319`; its SonarQube dependency is `26.8.0.126808`. The deployed ChillCode stack currently uses SonarQube Community `26.9.0.129388`.

## Build

Build the frontend before packaging the plugin. The frontend build writes the two self-contained SonarQube page-extension bundles into `backend/src/main/resources/static/`. Those bundles are committed, so a backend-only change packages without rebuilding them.

The frontend needs Node 22 (CI uses `node:22.23.1`) and the ChillCode npm registry: the `@chillcode/*` packages and about half the lockfile resolve from `artifacts.chillcode.de`, so `npm ci` fails without `NPM_CONFIG_REGISTRY=https://artifacts.chillcode.de/npm/npm-virtual/`.

```bash
cd frontend
npm ci
npm run build

cd ../backend
mvn clean verify package
```

The artifact is `backend/target/sonarqube-quality-plugin-1.0.0-SNAPSHOT.jar`.

## Install or upgrade

Replacing a SonarQube plugin JAR requires a SonarQube restart or stack redeployment. Plan a maintenance window: SonarQube is unavailable while it starts and loads the plugin.

1. Build the frontend and JAR as above.
2. Copy exactly one version of the plugin JAR into SonarQube's plugin directory. The ChillCode Swarm deployment uses `/mnt/docker/volume/sonarqube/plugins/`, mounted at `/opt/sonarqube/extensions/plugins/`.
3. Ensure the persistent report directory exists and is writable by the SonarQube container user. The ChillCode deployment uses `/mnt/docker/volume/sonarqube/mutation-reports/`, mounted at `/opt/sonarqube/mutation-reports/`.
4. Redeploy or restart SonarQube through the configured `homelab-docker-compose/sonarqube/docker-compose.yaml` stack.
5. Verify the startup log reports the plugin loading, then open the project Quality Dashboard and Mutation Testing pages.

The runtime storage default is `/opt/sonarqube/mutation-reports`. Reports are stored under the `projectKey` and `branch` submitted with the upload request; those request parameters override report metadata.

The upload token is read once at startup from exactly `/run/secrets/chillcode_mutation_upload_token`. Outside Docker Swarm, write the token to that path, readable by the SonarQube user, before starting SonarQube; `/run` is usually cleared on reboot, so recreate it at boot (for example with a `systemd-tmpfiles` rule). Without it, upload and delete answer 503.

### Configuration

Set these in `conf/sonar.properties`:

| Key | Default | Effect |
|---|---|---|
| `chillcode.mutation.storage` | `/opt/sonarqube/mutation-reports` | Where the upload API stores reports. The measure computer always reads the default path, so moving it stops measures being published. |
| `chillcode.mutation.maxReportSize` | `52428800` (50 MB) | Largest decompressed upload, in bytes. |
| `chillcode.mutation.retention.prDays` | `14` | Read, but nothing runs the pull-request report cleanup yet, so it has no effect. |

## CI upload

The upload API expects a gzip-compressed report in a multipart form field named `report` and a `MUTATION_UPLOAD_TOKEN` bearer credential. Production reads that token from the Docker Swarm secret mounted at `/run/secrets/chillcode_mutation_upload_token`; inject the same value into CI as the `MUTATION_UPLOAD_TOKEN` secret. It is distinct from an optional `SONAR_TOKEN`, which is used only when the helper also runs SonarScanner. Never pass either token on a command line or commit it: the examples below hand the header to curl on stdin (`-H @-`), because any local user can read a command line from the process list.

The Swarm secret is immutable. Rotate it by creating a new secret name, updating the SonarQube service with `--secret-rm` and `--secret-add` targeting `chillcode_mutation_upload_token`, verifying the rollout and authenticated upload, then deleting the old secret. Update the Drone `mutation_upload_token` secret before that controlled rotation; the normal deploy deliberately refuses an empty value and never overwrites an existing Swarm secret.

```bash
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@report.json.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

Use the target SonarQube server's configured authentication policy and keep credentials out of source code and logs.

A successful upload stores the report. A subsequent SonarQube analysis of that project publishes the stored mutation data as custom measures: the server's Compute Engine reads the report while processing the analysis. Only the `main` report becomes measures; reports uploaded for other branches are stored and shown on the Mutation Testing page while that branch is selected, but no analysis publishes them.

When the scanner runs in the same job that produced the report, `sonar.chillcode.mutationReport` can instead point the scanner-side sensor at the local report file. That sensor runs only for Java, JavaScript, TypeScript and Python projects.

### Stryker

```bash
gzip -c reports/mutation/mutation.json > /tmp/stryker-mutation.json.gz
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@/tmp/stryker-mutation.json.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

### PITest

```bash
gzip -c target/pit-reports/mutations.xml > /tmp/pitest-mutations.xml.gz
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@/tmp/pitest-mutations.xml.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

### cargo-mutants

```bash
gzip -c mutants.out/outcomes.json > /tmp/cargo-mutants-outcomes.json.gz
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@/tmp/cargo-mutants-outcomes.json.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

### mutant (Ruby)

mutant writes one session file per run; upload the newest.

```bash
gzip -c "$(ls -t .mutant/results/*.json | head -n 1)" > /tmp/mutant-session.json.gz
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@/tmp/mutant-session.json.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

### mutmut

```bash
python3 scripts/mutmut_to_stryker.py mutants . "$PROJECT_KEY" > /tmp/mutmut-stryker.json
gzip -c /tmp/mutmut-stryker.json > /tmp/mutmut-stryker.json.gz
printf 'Authorization: Bearer %s\n' "$MUTATION_UPLOAD_TOKEN" | curl --fail --silent --show-error -X POST \
  -H @- \
  -F "report=@/tmp/mutmut-stryker.json.gz;type=application/gzip" \
  "$SONAR_HOST_URL/api/chillcode_mutation/upload?projectKey=$PROJECT_KEY&branch=${DRONE_BRANCH:-main}"
```

`scripts/normalize-mutation-report.sh` is a convenience wrapper. For Stryker, PITest, cargo-mutants and mutant it only gzip-compresses the native report; for mutmut it invokes the bundled Python converter. Its final optional argument is the source root used to map mutmut artifact paths back to real Python files.

```bash
# Stryker, PITest, cargo-mutants or mutant
./scripts/normalize-mutation-report.sh stryker reports/mutation/mutation.json /tmp/stryker
./scripts/normalize-mutation-report.sh pitest target/pit-reports/mutations.xml /tmp/pitest
./scripts/normalize-mutation-report.sh cargo-mutants mutants.out/outcomes.json /tmp/cargo-mutants
./scripts/normalize-mutation-report.sh mutant "$(ls -t .mutant/results/*.json | head -n 1)" /tmp/mutant

# mutmut: argument 7 is the source root
./scripts/normalize-mutation-report.sh mutmut mutants /tmp/mutmut \
  "$PROJECT_KEY" main "$(git rev-parse HEAD)" .
```

Each command writes `<output-file>.gz`. `scripts/upload-mutation-report.sh` accepts that artifact:

```bash
MUTATION_UPLOAD_TOKEN="$MUTATION_UPLOAD_TOKEN" ./scripts/upload-mutation-report.sh \
  "$PROJECT_KEY" "${DRONE_BRANCH:-main}" "$(git rev-parse HEAD)" \
  /tmp/mutmut.gz "$SONAR_HOST_URL"
```

The helper sends the commit as a `commit` query parameter, which the server ignores. When `CI`, `SONAR_TOKEN` and `sonar-scanner` are all present it also runs a SonarScanner analysis of the current directory, passing `sonar.chillcode.mutation*` properties the plugin does not read; the measures come from the stored report either way.

## API

| Endpoint | Purpose |
|---|---|
| `POST /api/chillcode_mutation/upload?projectKey={key}&branch={branch}` | Store a gzip-compressed Stryker JSON, PITest XML, cargo-mutants outcomes, mutant session, or normalized report. `report` is required multipart data. Requires `Authorization: Bearer $MUTATION_UPLOAD_TOKEN`. |
| `GET /api/chillcode_mutation/download?projectKey={key}&branch={branch}` | Return the stored normalized report. |
| `GET /api/chillcode_mutation/summary?projectKey={key}&branch={branch}` | Return mutation score and aggregate counts. |
| `GET /api/chillcode_mutation/status?projectKey={key}&branch={branch}` | Return whether a report exists and its basic score data. |
| `POST /api/chillcode_mutation/delete?projectKey={key}&branch={branch}` | Delete the stored report for a project/branch. Requires `Authorization: Bearer $MUTATION_UPLOAD_TOKEN`. |
| `GET /api/chillcode_mutation/badge?projectKey={key}&branch={branch}` | Return a mutation-score SVG badge. |

`branch` defaults to `main` when omitted. `download`, `summary` and `status` answer 404 unless the caller may browse the project in SonarQube; `badge` stays public so a README can embed it.

## Quality gate

After a report has been uploaded and a later analysis has published the custom measure, add `mutation_score < 60` as an Error condition to the relevant SonarQube Quality Gate.

The analysis publishes `mutation_score` (percent), the counts `mutation_total`, `mutation_killed`, `mutation_survived`, `mutation_no_coverage`, `mutation_timeout` and `mutation_ignored`, and `mutation_tool` and `mutation_language`. `mutation_duration` is defined but never written. The score is killed mutants over all mutants except ignored ones.

## Development checks

```bash
(cd frontend && npm ci && npm run build)
(cd backend && mvn verify)
python3 scripts/tests/test_normalize_mutation_report.py
bash -n scripts/normalize-mutation-report.sh scripts/upload-mutation-report.sh
```

## Repository layout

```text
backend/     SonarQube plugin, API, storage, parser, metrics, and pages
frontend/    React page extensions built into backend resources
scripts/     Upload helper and mutation-report converters
schemas/     Normalized report schema
.ci/         Swarm deploy scripts the Drone pipeline runs
```

## License

MIT License. See `LICENSE`.
