#!/usr/bin/env python3
"""Convert a mutmut 3.x run into a Stryker-schema JSON mutation report.

The plugin accepts Stryker-schema JSON and PITest XML directly. mutmut's on-disk
artifacts need conversion first, so this script translates ``mutants/<path>.meta``
and the corresponding source file into the Stryker schema consumed by the plugin.

Location accuracy is at function granularity: mutmut stores function-level mutant
artifacts rather than a source location for each individual mutation.

Usage:
    mutmut_to_stryker.py <mutants_dir> <source_root> <project_name> > report.json
"""

from __future__ import annotations

import ast
import json
import re
import sys
from pathlib import Path


STATUS_BY_EXIT_CODE = {
    1: "KILLED",
    3: "KILLED",
    0: "SURVIVED",
    5: "NO_COVERAGE",
    33: "NO_COVERAGE",
    2: "ERROR",
    34: "IGNORED",
    35: "UNKNOWN",
    36: "TIMEOUT",
    -24: "TIMEOUT",
    24: "TIMEOUT",
    152: "TIMEOUT",
    255: "TIMEOUT",
    37: "IGNORED",
    -11: "ERROR",
    -9: "ERROR",
}


def function_line_ranges(source_path: Path) -> dict[str, tuple[int, int]]:
    """Map every nested function or method name to its source line range."""
    tree = ast.parse(source_path.read_text(encoding="utf-8"))
    ranges: dict[str, tuple[int, int]] = {}
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            ranges[node.name] = (node.lineno, node.end_lineno or node.lineno)
    return ranges


def mutant_base_name(mutant_key: str) -> str:
    """Return the source function name for a mutmut artifact key."""
    name = re.sub(r"__mutmut_\d+$", "", mutant_key)
    return name[2:] if name.startswith("x_") else name


def convert(mutants_dir: Path, source_root: Path, project_name: str) -> dict:
    """Return a Stryker-schema report for mutmut's on-disk mutant metadata."""
    files: dict[str, dict] = {}

    for meta_path in mutants_dir.rglob("*.meta"):
        mutant_file = meta_path.with_suffix("")
        relative_path = mutant_file.relative_to(mutants_dir)
        source_file = source_root / relative_path
        if not source_file.exists():
            continue

        metadata = json.loads(meta_path.read_text(encoding="utf-8"))
        ranges = function_line_ranges(source_file)
        mutants = []
        for qualified_key, exit_code in metadata.get("exit_code_by_key", {}).items():
            mutant_key = qualified_key.rsplit(".", 1)[-1]
            function_name = mutant_base_name(mutant_key)
            start_line, _end_line = ranges.get(function_name, (1, 1))
            status = STATUS_BY_EXIT_CODE.get(exit_code, "UNKNOWN") if exit_code is not None else "IGNORED"
            mutants.append(
                {
                    "id": qualified_key,
                    "mutatorName": "mutmut",
                    "replacement": "",
                    "status": status,
                    "statusReason": "",
                    "location": {
                        "start": {"line": start_line, "column": 0},
                        "end": {"line": start_line, "column": 0},
                    },
                    "static": False,
                }
            )

        if mutants:
            files[str(relative_path)] = {"mutants": mutants}

    return {"schemaVersion": 1, "projectName": project_name, "files": files}


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    mutants_dir, source_root, project_name = sys.argv[1:4]
    json.dump(convert(Path(mutants_dir), Path(source_root), project_name), sys.stdout, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
