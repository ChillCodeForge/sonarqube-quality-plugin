"""Tests for the mutmut-to-Stryker report converter."""

from __future__ import annotations

import contextlib
import io
import json
import runpy
import sys
import tempfile
import unittest
from importlib import import_module
from pathlib import Path
from unittest.mock import patch


REPOSITORY = Path(__file__).resolve().parents[2]
CONVERTER = REPOSITORY / "scripts" / "mutmut_to_stryker.py"
sys.path.insert(0, str(REPOSITORY / "scripts"))
converter = import_module("mutmut_to_stryker")


class MutmutToStrykerTest(unittest.TestCase):
    def test_maps_nested_sync_and_async_functions_to_source_ranges(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source = Path(temporary_directory) / "module.py"
            source.write_text(
                "def outer():\n"
                "    def inner():\n"
                "        return True\n"
                "    return inner()\n"
                "\n"
                "async def fetch():\n"
                "    return None\n",
                encoding="utf-8",
            )

            self.assertEqual(
                {"outer": (1, 4), "inner": (2, 3), "fetch": (6, 7)},
                converter.function_line_ranges(source),
            )

    def test_removes_mutmut_suffix_and_optional_x_prefix(self) -> None:
        self.assertEqual("is_positive", converter.mutant_base_name("x_is_positive__mutmut_12"))
        self.assertEqual("is_positive", converter.mutant_base_name("is_positive__mutmut_2"))

    def test_converts_metadata_and_omits_missing_sources_and_empty_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            mutants = root / "mutants"
            sources = root / "sources"
            mutant_file = mutants / "package" / "example.py"
            mutant_file.parent.mkdir(parents=True)
            source_file = sources / "package" / "example.py"
            source_file.parent.mkdir(parents=True)
            source_file.write_text("def is_positive(value):\n    return value > 0\n", encoding="utf-8")
            mutant_file.with_suffix(".py.meta").write_text(
                json.dumps(
                    {
                        "exit_code_by_key": {
                            "package.x_is_positive__mutmut_1": 1,
                            "package.x_is_positive__mutmut_2": 0,
                            "package.x_is_positive__mutmut_3": None,
                            "package.x_missing__mutmut_4": 42,
                        }
                    }
                ),
                encoding="utf-8",
            )

            missing_source = mutants / "package" / "missing.py"
            missing_source.write_text("artifact", encoding="utf-8")
            missing_source.with_suffix(".py.meta").write_text(
                json.dumps({"exit_code_by_key": {"x_missing__mutmut_1": 1}}),
                encoding="utf-8",
            )

            empty_source = mutants / "package" / "empty.py"
            empty_source.write_text("artifact", encoding="utf-8")
            (sources / "package" / "empty.py").write_text("pass\n", encoding="utf-8")
            empty_source.with_suffix(".py.meta").write_text(
                json.dumps({"exit_code_by_key": {}}), encoding="utf-8"
            )

            report = converter.convert(mutants, sources, "sample-project")

        self.assertEqual(1, report["schemaVersion"])
        self.assertEqual("sample-project", report["projectName"])
        self.assertEqual({"package/example.py"}, set(report["files"]))
        converted = report["files"]["package/example.py"]["mutants"]
        self.assertEqual(
            ["KILLED", "SURVIVED", "IGNORED", "UNKNOWN"],
            [mutant["status"] for mutant in converted],
        )
        self.assertEqual([1, 1, 1, 1], [mutant["location"]["start"]["line"] for mutant in converted])
        self.assertTrue(all(mutant["mutatorName"] == "mutmut" for mutant in converted))

    def test_main_prints_usage_and_returns_failure_for_wrong_arguments(self) -> None:
        stderr = io.StringIO()
        with patch.object(sys, "argv", [str(CONVERTER)]), contextlib.redirect_stderr(stderr):
            result = converter.main()

        self.assertEqual(2, result)
        self.assertIn("Usage:", stderr.getvalue())

    def test_cli_entrypoint_writes_converted_json_to_stdout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            mutants = root / "mutants"
            sources = root / "sources"
            mutant_file = mutants / "example.py"
            mutant_file.parent.mkdir(parents=True)
            source_file = sources / "example.py"
            source_file.parent.mkdir(parents=True)
            source_file.write_text("def run():\n    return True\n", encoding="utf-8")
            mutant_file.with_suffix(".py.meta").write_text(
                json.dumps({"exit_code_by_key": {"x_run__mutmut_1": 1}}),
                encoding="utf-8",
            )

            stdout = io.StringIO()
            with patch.object(
                sys,
                "argv",
                [str(CONVERTER), str(mutants), str(sources), "cli-project"],
            ), contextlib.redirect_stdout(stdout):
                with self.assertRaises(SystemExit) as exit_result:
                    runpy.run_path(str(CONVERTER), run_name="__main__")

        self.assertEqual(0, exit_result.exception.code)
        self.assertEqual("cli-project", json.loads(stdout.getvalue())["projectName"])


if __name__ == "__main__":
    unittest.main()
