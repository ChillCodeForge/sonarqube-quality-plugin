#!/usr/bin/env python3
"""Regression tests for the supported mutation-report normalization path."""

from __future__ import annotations

import gzip
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
NORMALIZER = REPOSITORY / "scripts" / "normalize-mutation-report.sh"


class NormalizeMutationReportTest(unittest.TestCase):
    def test_mutmut_artifacts_are_converted_and_gzipped(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            source_root = temporary_root / "source"
            source_file = source_root / "package" / "example.py"
            source_file.parent.mkdir(parents=True)
            source_file.write_text(
                "def is_positive(value):\n"
                "    return value > 0\n",
                encoding="utf-8",
            )

            mutants_directory = temporary_root / "mutants"
            mutant_file = mutants_directory / "package" / "example.py"
            mutant_file.parent.mkdir(parents=True)
            mutant_file.write_text("# mutmut artifact placeholder\n", encoding="utf-8")
            mutant_file.with_suffix(".py.meta").write_text(
                json.dumps({"exit_code_by_key": {"x_is_positive__mutmut_1": 1}}),
                encoding="utf-8",
            )

            output_file = temporary_root / "mutation-report.json"
            subprocess.run(
                [
                    "bash",
                    str(NORMALIZER),
                    "mutmut",
                    str(mutants_directory),
                    str(output_file),
                    "example-python",
                    "main",
                    "deadbeef",
                    str(source_root),
                ],
                check=True,
                capture_output=True,
                text=True,
            )

            with gzip.open(f"{output_file}.gz", "rt", encoding="utf-8") as report_file:
                report = json.load(report_file)

        self.assertEqual("example-python", report["projectName"])
        mutant = report["files"]["package/example.py"]["mutants"][0]
        self.assertEqual("KILLED", mutant["status"])
        self.assertEqual(1, mutant["location"]["start"]["line"])

    def test_upload_helper_sends_the_supplied_bearer_token(self) -> None:
        upload_helper = REPOSITORY / "scripts" / "upload-mutation-report.sh"
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            report_file = temporary_root / "report.json.gz"
            with gzip.open(report_file, "wt", encoding="utf-8") as output:
                output.write('{"summary": {"score": 100}}')

            arguments_file = temporary_root / "curl-arguments.txt"
            stdin_file = temporary_root / "curl-stdin.txt"
            fake_curl = temporary_root / "curl"
            fake_curl.write_text(
                "#!/usr/bin/env sh\n"
                "printf '%s\\n' \"$@\" > \"$CAPTURED_CURL_ARGUMENTS\"\n"
                "cat > \"$CAPTURED_CURL_STDIN\"\n"
                "printf '{\\\"status\\\":\\\"ok\\\"}\\n200\\n'\n",
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)

            environment = os.environ | {
                "PATH": f"{temporary_root}:{os.environ['PATH']}",
                "CAPTURED_CURL_ARGUMENTS": str(arguments_file),
                "CAPTURED_CURL_STDIN": str(stdin_file),
                "MUTATION_UPLOAD_TOKEN": "test-mutation-upload-token",
            }
            subprocess.run(
                [
                    "bash",
                    str(upload_helper),
                    "example-project",
                    "main",
                    "deadbeef",
                    str(report_file),
                    "https://sonar.example.test",
                ],
                check=True,
                capture_output=True,
                text=True,
                env=environment,
            )

            arguments = arguments_file.read_text(encoding="utf-8").splitlines()
            headers = stdin_file.read_text(encoding="utf-8").splitlines()

        # The header reaches curl on stdin, never as an argument, because any
        # local user can read a process's arguments.
        self.assertIn("Authorization: Bearer test-mutation-upload-token", headers)
        self.assertIn("@-", arguments)
        self.assertFalse(any("test-mutation-upload-token" in argument for argument in arguments))
        self.assertIn("report=@%s;type=application/gzip" % report_file, arguments)


if __name__ == "__main__":
    unittest.main()
