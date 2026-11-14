# Stored reports as fixtures

Each `<project>.mutation.json.gz` is a Stryker `mutation.json` rebuilt from that
project's report as stored in SonarQube on 2026-11-14
(`api/chillcode_mutation/download?branch=main`): the same files, mutant ids,
mutators, locations and statuses, without sources and status reasons. Stored
statuses map back to Stryker's names; the stored `ERROR` mutants were
TypeScript checker errors (`CompileError`), and stored `UNKNOWN` mutants with
Stryker's "Test runner crashed. Tried twice to restart it" reason are its
`RuntimeError`.

| project | mutants | timeout share of scored | RuntimeError | role |
|---|---|---|---|---|
| development-standards | 7606 | 88.5 % | 0 | mass-timeout nightly (tap runner fix pending) |
| nutrition | 3676 | 3.9 % | 6 | 883 CompileErrors; rejected for RuntimeErrors |
| doccomplete | 2801 | 0 % | 1 | rejected for its RuntimeError |
| AssetAllocator | 1037 | 0.3 % | 0 | good |
| real-life-rpg | 411 | 1.7 % | 0 | good |
| chillcode-website | 34 | 2.9 % | 0 | good |

The stored PITest report (Walletchart, 0.15 % timeouts) is not rebuilt here:
PIT XML needs fields the normalized report drops.
