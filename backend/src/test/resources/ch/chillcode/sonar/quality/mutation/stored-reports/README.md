# Stored reports as fixtures

Each `<project>.mutation.json.gz` is a Stryker `mutation.json` rebuilt from that
project's report as stored in SonarQube on 2026-11-14
(`api/chillcode_mutation/download?branch=main`): the same files, mutant ids,
mutators, locations and statuses, without sources and status reasons. Stored
statuses map back to Stryker's names; the stored `ERROR` mutants were
TypeScript checker errors (`CompileError`), and stored `UNKNOWN` mutants with
Stryker's "Test runner crashed. Tried twice to restart it" reason are its
`RuntimeError`.

| project | mutants | timeouts + RuntimeErrors, share of scored | role |
|---|---|---|---|
| development-standards | 7606 | 6733 + 0 = 88.5 % | mass-timeout nightly (tap runner fix pending): rejected |
| nutrition | 3676 | 109 + 6 = 4.1 % | 883 CompileErrors; accepted |
| doccomplete | 2801 | 0 + 1 = 0.04 % | accepted |
| AssetAllocator | 1037 | 3 + 0 = 0.3 % | accepted |
| real-life-rpg | 411 | 7 + 0 = 1.7 % | accepted |
| chillcode-website | 34 | 1 + 0 = 2.9 % | accepted |

The stored PITest report (Walletchart, 1 timeout in 685 mutants, 0.15 %) is not
rebuilt as PIT XML, which needs fields the normalized report drops.
`Walletchart.normalized.json.gz` is that stored report in the normalized
format instead, with its top-level mutants (without `coveredBy`) and its stored
summary, but without the per-file list the plugin rebuilds from the mutants.
