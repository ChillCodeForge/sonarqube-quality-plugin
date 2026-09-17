# ChillCode SonarQube Quality Plugin

## Overview

This plugin extends SonarQube Community Edition with:
- **Quality Dashboard** - Unified view of Reliability, Security, Maintainability, Coverage, Duplications, Mutation Score
- **Mutation Testing UI** - Detailed mutation testing results with mutant exploration
- **Mutation Report Storage API** - Upload, download, and query mutation reports
- **Sensor** - Processes mutation reports during SonarQube analysis
- **Custom Metrics** - Mutation Score, Total/Killed/Survived/No Coverage/Timeout/Ignored counts

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   CI Pipeline   │────▶│  SonarScanner    │────▶│   SonarQube      │
│  (Normalize +   │     │  (Measures +     │     │  (Plugin:        │
│   Upload)       │     │   Issues)        │     │   Metrics,       │
└─────────────────┘     └──────────────────┘     │   Issues,        │
                                                 │   Storage API,   │
                          ┌──────────────────┐   │   Web Pages)     │
                          │  Artifact Keeper │   └────────┬─────────┘
                          │  (Full Reports)  │            │
                          └──────────────────┘            │
                                                          ▼
                                                 ┌──────────────────┐
                                                 │  Browser UI      │
                                                 │  (Quality Dash,  │
                                                 │   Mutation UI)   │
                                                 └──────────────────┘
```

## Installation

### Prerequisites
- SonarQube 26.8+ (Community/Developer/Enterprise)
- Java 21 runtime

### Deploy via Portainer/Swarm

1. **Build the plugin:**
```bash
cd backend
mvn clean package
# Output: target/sonarqube-quality-plugin-1.0.0-SNAPSHOT.jar
```

2. **Add volumes to SonarQube stack** (already done in homelab-docker-compose):
```yaml
volumes:
  - /mnt/docker/volume/sonarqube/plugins:/opt/sonarqube/extensions/plugins
  - /mnt/docker/volume/sonarqube/mutation-reports:/opt/sonarqube/mutation-reports
```

3. **Deploy plugin JAR** to `/mnt/docker/volume/sonarqube/plugins/` on the SonarQube host

4. **Restart SonarQube** service

### Configuration

Set in SonarQube Administration → Configuration → ChillCode Quality:
```
chillcode.mutation.storage=/opt/sonarqube/mutation-reports
chillcode.mutation.maxReportSize=52428800
chillcode.mutation.retention.prDays=14
chillcode.mutation.apiToken=<optional-token-for-CI-upload>
```

## CI Pipeline Integration

### 1. Normalize Mutation Reports

Each tool produces different output formats. Use the normalizer script:

```bash
# Stryker (TypeScript/JavaScript)
./normalize-mutation-report.sh stryker \
  frontend/reports/mutation/mutation.json \
  /tmp/mutation-report.json \
  nutrition main $(git rev-parse HEAD)

# mutmut (Python)
./normalize-mutation-report.sh mutmut \
  .mutmut-results \
  /tmp/mutation-report.json \
  nutrition main $(git rev-parse HEAD)

# Output: /tmp/mutation-report.json.gz
```

### 2. Upload to SonarQube

```bash
./upload-mutation-report.sh \
  nutrition main $(git rev-parse HEAD) \
  /tmp/mutation-report.json.gz \
  https://sonar.chillcode.de \
  $SONAR_TOKEN
```

### 3. Complete `.drone.yml` Example (nutrition)

```yaml
- name: frontend-mutation-tests
  image: registry.chillcode.de/dockerhub-cache/node:22
  environment:
    NPM_CONFIG_REGISTRY: https://artifacts.chillcode.de/npm/npm-virtual/
    NPM_CONFIG_AUDIT: "false"
    SONAR_TOKEN:
      from_secret: sonar_token
    SONAR_HOST_URL:
      from_secret: sonar_host_url
  commands:
    - |
      MUTATION_DAYS="2 5"
      TODAY=$(date -u +%u)
      if [ "${DRONE_BUILD_EVENT}" = "cron" ] && ! echo "$MUTATION_DAYS" | grep -qw "$TODAY"; then
        echo "Mutation tests run on UTC days $MUTATION_DAYS, today is $TODAY - skipped."
        exit 0
      fi
    - cd frontend
    - npm ci
    - npm run test:mutate
    - |
      # Normalize and upload
      PROJECT_KEY="nutrition"
      BRANCH="${DRONE_BRANCH:-main}"
      COMMIT="${DRONE_COMMIT_SHA}"
      REPORT_JSON="reports/mutation/mutation.json"
      REPORT_GZ="/tmp/mutation-report.json.gz"
      
      # Normalize
      /path/to/normalize-mutation-report.sh stryker \
        "$REPORT_JSON" "$REPORT_GZ" \
        "$PROJECT_KEY" "$BRANCH" "$COMMIT"
      
      # Upload to SonarQube plugin storage
      /path/to/upload-mutation-report.sh \
        "$PROJECT_KEY" "$BRANCH" "$COMMIT" \
        "$REPORT_GZ" \
        "$$SONAR_HOST_URL" "$$SONAR_TOKEN"
```

## API Endpoints

### Upload Report
```
POST /api/chillcode_mutation/upload?projectKey={key}&branch={branch}&commit={sha}
Content-Type: application/json
Authorization: Bearer <token>

Body: Normalized Mutation Report JSON (gzipped)
```

### Download Report
```
GET /api/chillcode_mutation/download?projectKey={key}&branch={branch}
Authorization: Bearer <token> (optional for public projects)
```

### Get Summary
```
GET /api/chillcode_mutation/summary?projectKey={key}&branch={branch}
```

### Get Status
```
GET /api/chillcode_mutation/status?projectKey={key}&branch={branch}
```

### Delete Report
```
POST /api/chillcode_mutation/delete?projectKey={key}&branch={branch}
Authorization: Bearer <token>
```

## Mutation Report Schema (v1)

```json
{
  "schemaVersion": 1,
  "tool": "stryker|pitest|mutmut",
  "language": "typescript|java|python",
  "project": "sonarqube-project-key",
  "branch": "main",
  "commit": "abc123def",
  "timestamp": "2026-09-17T12:00:00Z",
  "summary": {
    "total": 1284,
    "killed": 1091,
    "survived": 83,
    "noCoverage": 52,
    "timeout": 0,
    "ignored": 58,
    "score": 84.97
  },
  "files": [...],
  "mutants": [...]
}
```

## Quality Gates

Add Mutation Score to Quality Gate:
1. Administration → Quality Gates → Create/Edit
2. Add condition: `mutation_score < 60` → Error
3. Assign to projects

## Gitea Integration

The upload script can also post commit status:

```bash
# In upload-mutation-report.sh, add:
curl -X POST "https://git.chillcode.de/api/v1/repos/{owner}/{repo}/statuses/${COMMIT}" \
  -H "Authorization: token ${GITEA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "state": "success",
    "context": "mutation-testing",
    "description": "Mutation score: 84.97%",
    "target_url": "https://sonar.chillcode.de/project/'${PROJECT_KEY}'/mutation-testing"
  }'
```

## Development

### Backend
```bash
cd backend
mvn compile
mvn test
mvn package
```

### Frontend
```bash
cd frontend
npm install
npm run dev      # Development server
npm run build    # Build to ../backend/src/main/resources/static
```

### Frontend Entry Points
- `quality-dashboard.tsx` → Quality Dashboard page
- `mutation-testing.tsx` → Mutation Testing page

Built assets go to `backend/src/main/resources/static/` and are served by the plugin.

## Plugin Structure

```
sonarqube-quality-plugin/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/ch/chillcode/sonar/quality/
│       │   ├── QualityPlugin.java           # Entry point
│       │   ├── metrics/MutationMetrics.java # Custom metrics
│       │   ├── sensor/MutationSensor.java   # Analysis sensor
│       │   ├── mutation/
│       │   │   ├── model/                   # Report POJOs
│       │   │   ├── parser/                  # JSON parser
│       │   │   ├── storage/                 # File storage service
│       │   │   ├── service/                 # Business logic
│       │   │   └── api/                     # REST API
│       │   ├── config/QualityConfiguration.java
│       │   └── ui/                          # Web page definitions
│       └── resources/static/                # Built React apps
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── quality-dashboard.tsx
│       ├── mutation-testing.tsx
│       ├── components/
│       └── api.ts
├── scripts/
│   ├── normalize-mutation-report.sh
│   └── upload-mutation-report.sh
├── schemas/
│   └── mutation-report-v1.schema.json
└── docs/
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Plugin not loading | Check SonarQube logs: `docker logs sonarqube-app`; verify JAR in `/opt/sonarqube/extensions/plugins/` |
| Storage permission denied | Ensure `/mnt/docker/volume/sonarqube/mutation-reports` is writable by SonarQube container user (UID 1000) |
| Custom metrics not showing | Restart SonarQube after plugin install; metrics are registered at startup |
| API 401 Unauthorized | Set `chillcode.mutation.apiToken` or use SonarQube user token in Authorization header |
| Frontend not loading | Verify `mvn package` copied built assets to `src/main/resources/static/` |

## Version Compatibility

| Plugin Version | SonarQube Version | Java Version |
|----------------|-------------------|--------------|
| 1.0.x          | 26.8+ (LTS)       | 21           |

## License

MIT License - see LICENSE file.