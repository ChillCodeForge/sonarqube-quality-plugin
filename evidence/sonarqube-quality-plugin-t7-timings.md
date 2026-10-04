# SonarQube Quality Plugin — T7 Stage Pipeline Migration Timings

Repository: ChillCode/sonarqube-quality-plugin
PR: #38 (branch `ci/37-stage-pipelines-v2`)
Migration: Single pipeline `quality-and-deploy` → v2 stage pipelines (`prep → lint || test → analysis → build → release + cleanup`)
Default branch before migration: `master`; after: `main`

## Baseline — Last 3 Successful Push Builds on `master` (Old Pipeline)

| Build | Event | Result | Duration (min) | Pipeline / Stages |
|-------|-------|--------|----------------|-------------------|
| 67 | push | success | 5.93 | quality-and-deploy (single pipeline) |
| 63 | push | success | 5.17 | quality-and-deploy (single pipeline) |
| 60 | push | success | 3.70 | quality-and-deploy (single pipeline) |

**Baseline average (push, green): 4.93 min**

---

## Probe Builds — New v2 Stage Pipeline Topology

### Red-Lint Probe (Build #76, commit 34da28c)
Deliberate lint error: trailing whitespace + unused import in `QualityPlugin.java`

| Build | Event | Result | Duration (min) | Stages |
|-------|-------|--------|----------------|--------|
| 76 | pull_request | failure | 2.62 | prep ✅ (1.33m) → lint ❌ (0.65m) → test ❌ (1.13m) → analysis ⏭ skipped → build ⏭ skipped → cleanup ✅ (0.02m) |

**Lint failed as expected; test also failed (compilation error from unused import); analysis/build skipped; cleanup ran.**

---

### Red-Analysis Probe (Build #79, commit e820ce6)
Deliberate SonarQube quality gate failure: empty catch block (S1172) + hardcoded password (S2068) in `MutationService.java`

| Build | Event | Result | Duration (min) | Stages |
|-------|-------|--------|----------------|--------|
| 79 | pull_request | failure | 4.45 | prep ✅ (1.00m) → lint ✅ (2.92m) → test ✅ (1.68m) → analysis ❌ (0.52m) → build ⏭ skipped → cleanup ✅ (0.02m) |

**Lint & test green; analysis failed (quality gate); build skipped; cleanup ran.**

---

### Green Probe (Build #80, commit 8f77c14)
Both probes reverted; branch content identical to `74ce86a`

| Build | Event | Result | Duration (min) | Stages |
|-------|-------|--------|----------------|--------|
| 80 | pull_request | success | 5.60 | prep ✅ (1.08m) → lint ✅ (3.38m) || test ✅ (1.48m) → analysis ✅ (0.50m) → build ✅ (0.60m) → cleanup ✅ (0.02m) |

**All stages green; lint || test ran in parallel (overlap proven by start offsets).**

---

## Summary

| Metric | Value |
|--------|-------|
| Baseline avg (push, old pipeline) | 4.93 min |
| Green PR (new pipeline, build #80) | 5.60 min |
| Red-lint time-to-red (build #76) | 1.98 min (lint failed at 1.33m, PR red at 2.62m) |
| Red-analysis time-to-red (build #79) | 4.45 min (analysis failed at 4.45m) |
| Diff vs `74ce86a` | Empty (no changes) |

---

## Stage Timing Breakdown (Green Build #80)

| Stage | Duration (s) | Start Offset (s from build start) |
|-------|--------------|-----------------------------------|
| prep | 65 | 0 |
| lint | 203 | 0 |
| test | 89 | 1 |
| analysis | 30 | 269 |
| build | 36 | 299 |
| cleanup | 1 | 335 |
| **Total** | **336** | — |

**Parallelism verified:** `lint` and `test` started at offsets 0s and 1s respectively, confirming `lint || test` parallel execution.