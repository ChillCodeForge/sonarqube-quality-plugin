package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.chillcode.sonar.quality.mutation.StoredReports;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MutationReportParserTest {

  private final MutationReportParser parser = new MutationReportParser();

  @Test
  void parsesCompressedAndUncompressedReportFiles(@TempDir Path directory) throws IOException {
    String json = "{\"schemaVersion\":1,\"files\":{}}";
    Path plain = directory.resolve("report.json");
    java.nio.file.Files.writeString(plain, json);
    assertEquals("stryker", parser.parse(plain.toFile()).getTool());

    Path compressed = directory.resolve("report.json.gz");
    try (java.util.zip.GZIPOutputStream output =
        new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(compressed))) {
      output.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    assertEquals("stryker", parser.parse(compressed.toFile()).getTool());
  }

  @Test
  void rejectsAReportFileThatDoesNotExist() {
    java.io.File missing = new java.io.File("target/no-such-mutation-report.json");

    assertThrows(IllegalArgumentException.class, () -> parser.parse(missing));
  }

  // --- Pitest XML ---

  @Test
  void parsesPitestXmlWithMixedStatuses() throws IOException {
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations partial="false">
                    <mutation detected="true" status="KILLED" numberOfTestsRun="3">
                        <sourceFile>DocService.java</sourceFile>
                        <mutatedClass>de.doccomplete.DocService</mutatedClass>
                        <mutatedMethod>validate</mutatedMethod>
                        <methodDescription>(Ljava/lang/String;)Z</methodDescription>
                        <lineNumber>42</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator</mutator>
                        <killingTest>de.doccomplete.DocServiceTest.testValidate</killingTest>
                        <description>negated conditional</description>
                    </mutation>
                    <mutation detected="false" status="SURVIVED" numberOfTestsRun="2">
                        <sourceFile>DocService.java</sourceFile>
                        <mutatedClass>de.doccomplete.DocService</mutatedClass>
                        <mutatedMethod>compute</mutatedMethod>
                        <methodDescription>(I)I</methodDescription>
                        <lineNumber>77</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
                        <description>replaced operator</description>
                    </mutation>
                    <mutation detected="false" status="NO_COVERAGE" numberOfTestsRun="0">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>format</mutatedMethod>
                        <methodDescription>()Ljava/lang/String;</methodDescription>
                        <lineNumber>10</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
                        <description>replaced return value</description>
                    </mutation>
                    <mutation detected="false" status="TIMED_OUT" numberOfTestsRun="1">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>loop</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>20</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator</mutator>
                        <description>incremented</description>
                    </mutation>
                    <mutation detected="false" status="NON_VIABLE" numberOfTestsRun="0">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>build</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>30</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator>
                        <description>removed call, does not compile</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals("pitest", report.getTool());
    assertEquals("java", report.getLanguage());
    assertEquals(5, report.getSummary().getTotal());
    // PIT counts a NON_VIABLE mutant as detected (DetectionStatus.isDetected),
    // so it is reported as killed with PIT's own status as the reason.
    assertEquals(2, report.getSummary().getKilled());
    assertEquals(1, report.getSummary().getSurvived());
    assertEquals(1, report.getSummary().getNoCoverage());
    assertEquals(1, report.getSummary().getTimeout());
    assertEquals(0, report.getSummary().getIgnored());
    assertEquals("NON_VIABLE", report.getMutants().get(4).getStatusReason());

    // PIT's own mutation score: detected (KILLED, TIMED_OUT, NON_VIABLE)
    // over all generated mutations = 3 / 5, what PIT prints as
    // ">> Generated 5 mutations Killed 3 (60%)".
    assertEquals(60.0, report.getSummary().getScore(), 0.001);

    assertEquals(2, report.getFiles().size());
    NormalizedMutationReport.MutationFile helper =
        report.getFiles().stream()
            .filter(f -> f.getPath().equals("de/doccomplete/Helper.java"))
            .findFirst()
            .orElseThrow();
    // NO_COVERAGE, TIMED_OUT, NON_VIABLE: 2 of 3 detected.
    assertEquals(200.0 / 3, ((Number) helper.getMetrics().get("score")).doubleValue(), 0.001);
    assertEquals(1, helper.getMetrics().get("timeout"));
  }

  @Test
  void mapsPitestFilePathsUsingMutatedClassPackage() throws IOException {
    String xml =
        """
                <mutations>
                    <mutation detected="true" status="KILLED" numberOfTestsRun="1">
                        <sourceFile>Widget.java</sourceFile>
                        <mutatedClass>de.doccomplete.backend.Widget</mutatedClass>
                        <mutatedMethod>render</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>5</lineNumber>
                        <mutator>SomeMutator</mutator>
                        <description>x</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(1, report.getFiles().size());
    assertEquals("de/doccomplete/backend/Widget.java", report.getFiles().get(0).getPath());
  }

  @Test
  void pitestReportWithZeroMutationsHasZeroScoreNotNaN() throws IOException {
    String xml = "<mutations></mutations>";

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(0, report.getSummary().getTotal());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
    assertFalse(Double.isNaN(report.getSummary().getScore()));
  }

  @Test
  void allNonViableMutationsAreDetectedAsPitReportsThem() throws IOException {
    String xml =
        """
                <mutations>
                    <mutation detected="false" status="NON_VIABLE" numberOfTestsRun="0">
                        <sourceFile>A.java</sourceFile>
                        <mutatedClass>a.A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>1</lineNumber>
                        <mutator>X</mutator>
                        <description>x</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(1, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getKilled());
    assertEquals(0, report.getSummary().getIgnored());
    assertEquals(100.0, report.getSummary().getScore(), 0.001);
  }

  // --- Format detection ---

  @Test
  void detectsPitestFormatFromMutationsRootElement() throws IOException {
    String xml = "<mutations></mutations>";
    NormalizedMutationReport report = parser.parse(xml.getBytes(), "report.xml");
    assertEquals("pitest", report.getTool());
  }

  @Test
  void rejectsUnknownXmlRootElement() {
    String xml = "<somethingElse></somethingElse>";
    assertThrows(IOException.class, () -> parser.parse(xml.getBytes(), "report.xml"));
  }

  @Test
  void mutmutStillUnsupportedAndFailsLoudlyNotSilently() {
    String json = "{\"mutants\": []}";
    IOException ex = assertThrows(IOException.class, () -> parser.parse(json));
    assertTrue(
        ex.getMessage().contains("mutmut")
            || ex.getCause() instanceof UnsupportedOperationException);
  }

  // --- Stryker JSON ---

  @Test
  void parsesStrykerReportWithMixedStatusesAndMetrics() throws IOException {
    String json =
        """
        {
          "schemaVersion": 1,
          "projectName": "sample-project",
          "branch": "feature/test",
          "files": {
            "src/calc.ts": {
              "mutants": [
                {
                  "id": "1",
                  "mutatorName": "BinaryExpression",
                  "replacement": "-",
                  "status": "Killed",
                  "statusReason": "Failed test",
                  "location": {
                    "start": {"line": 10, "column": 5},
                    "end": {"line": 10, "column": 6}
                  }
                },
                {
                  "id": "2",
                  "mutatorName": "EqualityOperator",
                  "replacement": "===",
                  "status": "Survived",
                  "location": {
                    "start": {"line": 20, "column": 1},
                    "end": {"line": 20, "column": 3}
                  }
                },
                {
                  "id": "3",
                  "mutatorName": "BlockStatement",
                  "replacement": "{}",
                  "status": "NoCoverage"
                },
                {
                  "id": "4",
                  "mutatorName": "Timeout",
                  "replacement": "",
                  "status": "Timeout"
                },
                {
                  "id": "5",
                  "mutatorName": "Ignored",
                  "replacement": "",
                  "status": "Ignored"
                },
                {
                  "id": "6",
                  "mutatorName": "CompileError",
                  "replacement": "",
                  "status": "CompileError"
                },
                {
                  "id": "7",
                  "mutatorName": "RuntimeError",
                  "replacement": "",
                  "status": "RuntimeError"
                }
              ]
            },
            "lib/helper.js": {
              "mutants": []
            },
            "script.py": {
              "mutants": []
            }
          }
        }
        """;

    NormalizedMutationReport report = parser.parse(json);
    assertEquals("stryker", report.getTool());
    assertEquals("sample-project", report.getProject());
    assertEquals("feature/test", report.getBranch());
    assertEquals("typescript", report.getLanguage());

    assertEquals(7, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getKilled());
    assertEquals(1, report.getSummary().getSurvived());
    assertEquals(1, report.getSummary().getNoCoverage());
    assertEquals(1, report.getSummary().getTimeout());
    // Ignored, CompileError and RuntimeError are all outside the score.
    assertEquals(3, report.getSummary().getIgnored());
    // Stryker's mutation score: detected (killed 1 + timeout 1) over valid
    // (detected 2 + survived 1 + no coverage 1) = 50%.
    assertEquals(50.0, report.getSummary().getScore(), 0.001);

    assertEquals(3, report.getFiles().size());
    NormalizedMutationReport.MutationFile calcFile =
        report.getFiles().stream()
            .filter(f -> f.getPath().equals("src/calc.ts"))
            .findFirst()
            .orElseThrow();
    assertEquals("typescript", calcFile.getLanguage());
    assertEquals(7, calcFile.getMutants().size());
    assertEquals(50.0, ((Number) calcFile.getMetrics().get("score")).doubleValue(), 0.001);
    assertEquals(7, calcFile.getMetrics().get("total"));
    assertEquals(1, calcFile.getMetrics().get("killed"));
    assertEquals(1, calcFile.getMetrics().get("survived"));
    assertEquals(1, calcFile.getMetrics().get("noCoverage"));
    assertEquals(1, calcFile.getMetrics().get("timeout"));
    assertEquals(3, calcFile.getMetrics().get("ignored"));
    // Stryker's two error kinds stay apart: the upload guard rejects RuntimeErrors only.
    assertEquals("COMPILE_ERROR", calcFile.getMutants().get(5).getStatus());
    assertEquals("RUNTIME_ERROR", calcFile.getMutants().get(6).getStatus());

    NormalizedMutationReport.MutationMutant m1 = calcFile.getMutants().get(0);
    assertEquals("1", m1.getId());
    assertEquals("BinaryExpression", m1.getMutatorName());
    assertEquals("-", m1.getReplacement());
    assertEquals("KILLED", m1.getStatus());
    assertEquals("Failed test", m1.getStatusReason());
    org.junit.jupiter.api.Assertions.assertNotNull(m1.getLocation());
    assertEquals(10, m1.getLocation().getStart().getLine());
    assertEquals(5, m1.getLocation().getStart().getColumn());
    assertEquals(10, m1.getLocation().getEnd().getLine());
    assertEquals(6, m1.getLocation().getEnd().getColumn());
  }

  @Test
  void strykerDetectsPythonProject() throws IOException {
    String json =
        """
        {
          "schemaVersion": 1,
          "projectName": "my-python-app",
          "files": {}
        }
        """;
    NormalizedMutationReport report = parser.parse(json);
    assertEquals("stryker", report.getTool());
    assertEquals("python", report.getLanguage());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
  }

  // --- Entry points and format detection edge cases ---

  @Test
  void wrapsAFailureThatIsNotAnIoExceptionIntoOne() {
    byte[] mutmut = "{\"mutants\": []}".getBytes(StandardCharsets.UTF_8);

    IOException ex = assertThrows(IOException.class, () -> parser.parse(mutmut, "mutmut.json"));

    assertEquals("Failed to parse mutation report", ex.getMessage());
    assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
  }

  @Test
  void treatsAnXmlFileNameAsXmlEvenWhenTheBodyIsNot() {
    byte[] empty = new byte[0];

    IOException ex = assertThrows(IOException.class, () -> parser.parse(empty, "mutations.xml"));

    assertEquals("Failed to parse XML mutation report", ex.getMessage());
  }

  @Test
  void sniffsJsonWithoutAFileName() throws IOException {
    byte[] json = "{\"schemaVersion\":1,\"files\":{}}".getBytes(StandardCharsets.UTF_8);

    assertEquals("stryker", parser.parse(json, null).getTool());
  }

  @Test
  void parsesAReportFromAStream() throws IOException {
    InputStream input =
        new ByteArrayInputStream(
            "{\"schemaVersion\":1,\"files\":{}}".getBytes(StandardCharsets.UTF_8));

    assertEquals("stryker", parser.parseFromStream(input).getTool());
  }

  @Test
  void rejectsAnUnreadableStream() {
    InputStream input = new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8));

    IOException ex = assertThrows(IOException.class, () -> parser.parseFromStream(input));

    assertEquals("Failed to parse mutation report from stream", ex.getMessage());
  }

  @Test
  void readsAnAlreadyNormalizedReport() throws IOException {
    String json =
        """
        {"schemaVersion": 1, "tool": "pitest", "language": "java", "project": "p",
         "branch": "feature", "commit": "abc", "summary": {"total": 1, "killed": 1, "score": 100.0},
         "mutants": [{"id": "1", "status": "KILLED", "filePath": "a/A.java"}]}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals("pitest", report.getTool());
    assertEquals("java", report.getLanguage());
    assertEquals("p", report.getProject());
    assertEquals("feature", report.getBranch());
    assertEquals("abc", report.getCommit());
    assertEquals(1, report.getSummary().getTotal());
    assertEquals(100.0, report.getSummary().getScore(), 0.001);
  }

  @Test
  void rebuildsTheSummaryAndFileMetricsOfANormalizedReportFromItsMutants() throws IOException {
    // The uploaded summary and file metrics claim 50 timeouts and a perfect score; the one
    // listed mutant survived.
    String json =
        """
        {"tool": "stryker", "summary": {"total": 51, "killed": 1, "timeout": 50, "score": 100},
         "files": [{"path": "src/a.ts", "metrics": {"score": 100, "timeout": 50},
                    "mutants": [{"id": "1", "status": "SURVIVED"}]}]}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals(1, report.getSummary().getTotal());
    assertEquals(0, report.getSummary().getTimeout());
    assertEquals(1, report.getSummary().getSurvived());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
    assertEquals(1, report.getFiles().size());
    NormalizedMutationReport.MutationFile file = report.getFiles().get(0);
    assertEquals("src/a.ts", file.getPath());
    assertEquals(0, file.getMetrics().get("timeout"));
    assertEquals(0.0, (double) file.getMetrics().get("score"), 0.001);
    // Per-file mutants also become the top-level list, each with its file's path.
    assertEquals(1, report.getMutants().size());
    assertEquals("src/a.ts", report.getMutants().get(0).getFilePath());
  }

  @Test
  void rebuildsANormalizedReportFromItsTopLevelMutantsWhenItHasThem() throws IOException {
    String json =
        """
        {"tool": "stryker", "summary": {"total": 2, "killed": 2, "score": 100},
         "mutants": [{"status": "KILLED", "filePath": "src/a.ts"},
                     {"status": "TIMEOUT", "filePath": "src/b.ts"}],
         "files": [{"path": "src/a.ts", "mutants": [{"status": "KILLED"}]}]}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals(1, report.getSummary().getKilled());
    assertEquals(1, report.getSummary().getTimeout());
    assertEquals(
        List.of("src/a.ts", "src/b.ts"),
        report.getFiles().stream().map(NormalizedMutationReport.MutationFile::getPath).toList());
  }

  @Test
  void readsADownloadedNormalizedReportAsNormalizedNotAsStryker() throws IOException {
    // What the download action returns: schemaVersion and a files list (Stryker's files is an
    // object keyed by path).
    String json =
        """
        {"schemaVersion": 1, "tool": "stryker", "summary": {"total": 1, "killed": 1, "score": 100},
         "files": [{"path": "a.ts", "mutants": [{"status": "TIMEOUT", "filePath": "a.ts"}]}],
         "mutants": [{"status": "TIMEOUT", "filePath": "a.ts"}]}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals(1, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getTimeout());
    assertEquals(0, report.getSummary().getKilled());
  }

  @Test
  void rescoresARealNormalizedReportWithTheSharedScorer() throws IOException {
    // Walletchart's stored summary says 96.79 (663 killed of 685), scored before the timeout
    // counted as detected; the rebuilt score is PIT's own, (663 + 1) of 685.
    NormalizedMutationReport report =
        parser.parse(StoredReports.normalized("Walletchart"), "mutation.json");

    assertEquals(685, report.getSummary().getTotal());
    assertEquals(663, report.getSummary().getKilled());
    assertEquals(1, report.getSummary().getTimeout());
    assertEquals(664 * 100.0 / 685, report.getSummary().getScore(), 0.001);
    assertEquals(59, report.getFiles().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"tool\": \"stryker\", \"summary\": {\"total\": 7606, \"timeout\": 6733, \"score\": 91.9}}",
        "{\"tool\": \"stryker\", \"summary\": {}, \"mutants\": [], \"files\": [{\"path\": \"a.ts\"}]}",
        "{\"schemaVersion\": 1, \"tool\": \"stryker\", \"summary\": {\"total\": 5}, \"files\": []}"
      })
  void rejectsANormalizedReportWithoutMutants(String json) {
    MutationReportRejectedException ex =
        assertThrows(MutationReportRejectedException.class, () -> parser.parse(json));

    assertEquals(
        "the report lists no mutants: an upload in the normalized format must carry them,"
            + " its summary is rebuilt from them",
        ex.getMessage());
  }

  @Test
  void rejectsANormalizedUploadWithoutMutantsAsSuch() {
    byte[] content =
        "{\"tool\": \"stryker\", \"summary\": {\"total\": 3}}".getBytes(StandardCharsets.UTF_8);

    assertThrows(MutationReportRejectedException.class, () -> parser.parse(content, "report"));
  }

  @Test
  void countsAMutantWithoutStatusAsIgnoredInsteadOfFailing() throws IOException {
    NormalizedMutationReport report =
        parser.parse(
            "{\"tool\": \"stryker\", \"summary\": {}, \"mutants\": [{\"filePath\": \"a.ts\"}]}");

    assertEquals(1, report.getSummary().getIgnored());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"{}", "{\"tool\": \"pitest\"}", "{\"summary\": {}}", "{\"schemaVersion\": 1}"})
  void rejectsJsonOfNoKnownFormat(String json) {
    IOException ex = assertThrows(IOException.class, () -> parser.parse(json));

    assertEquals("Unknown mutation report format", ex.getCause().getMessage());
  }

  // --- Stryker edge cases ---

  @Test
  void strykerFilesThatAreNotAnObjectYieldAnEmptyReport() throws IOException {
    NormalizedMutationReport report = parser.parse("{\"schemaVersion\":1,\"files\":[]}");

    assertTrue(report.getFiles().isEmpty());
    assertEquals(0, report.getSummary().getTotal());
  }

  @Test
  void strykerFileWithoutMutantsArrayHasZeroScore() throws IOException {
    NormalizedMutationReport report = parser.parse("{\"schemaVersion\":1,\"files\":{\"a.ts\":{}}}");

    NormalizedMutationReport.MutationFile file = report.getFiles().get(0);
    assertTrue(file.getMutants().isEmpty());
    assertEquals(0.0, ((Number) file.getMetrics().get("score")).doubleValue(), 0.001);
    assertEquals(0, file.getMetrics().get("total"));
  }

  @Test
  void strykerUnknownStatusIsLeftOutOfTheScore() throws IOException {
    String json =
        """
        {"schemaVersion": 1, "files": {"a.ts": {"mutants": [
          {"id": "1", "mutatorName": "M", "replacement": "", "status": "Pending"}
        ]}}}
        """;

    NormalizedMutationReport report = parser.parse(json);

    NormalizedMutationReport.MutationMutant mutant = report.getMutants().get(0);
    assertEquals("UNKNOWN", mutant.getStatus());
    assertNull(mutant.getLocation());
    assertEquals(1, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getIgnored());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
  }

  @Test
  void strykerShapedGenericErrorStaysError() throws IOException {
    // development-standards' mutmut_to_stryker.py writes mutmut's crash exit codes as "ERROR":
    // neither a CompileError nor a RuntimeError, left out of the score as before.
    String json =
        """
        {"schemaVersion": 1, "files": {"a.py": {"mutants": [
          {"id": "1", "mutatorName": "M", "replacement": "", "status": "ERROR"}
        ]}}}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals("ERROR", report.getMutants().get(0).getStatus());
    assertEquals(1, report.getSummary().getIgnored());
  }

  @Test
  void strykerCountsTimeoutsAsDetected() throws IOException {
    // Shape of a report dominated by timeouts (development-standards:
    // 257 killed, 6733 timeout, 616 survived): timeouts must raise the
    // score, not sink it.
    String json =
        """
        {"schemaVersion": 1, "files": {"a.ts": {"mutants": [
          {"id": "1", "status": "Killed"},
          {"id": "2", "status": "Timeout"},
          {"id": "3", "status": "Timeout"},
          {"id": "4", "status": "Survived"}
        ]}}}
        """;

    NormalizedMutationReport report = parser.parse(json);

    assertEquals(75.0, report.getSummary().getScore(), 0.001);
    assertEquals(
        75.0, ((Number) report.getFiles().get(0).getMetrics().get("score")).doubleValue(), 0.001);
  }

  @ParameterizedTest
  @CsvSource({
    "src/view.tsx, typescript",
    "src/view.jsx, javascript",
    "src/Main.java, java",
    "lib/tool.rb, unknown"
  })
  void strykerDerivesEachFileLanguageFromItsExtension(String path, String language)
      throws IOException {
    String json = "{\"schemaVersion\":1,\"files\":{\"" + path + "\":{\"mutants\":[]}}}";

    assertEquals(language, parser.parse(json).getFiles().get(0).getLanguage());
  }

  @Test
  void strykerDetectsPythonFromAPyProjectName() throws IOException {
    NormalizedMutationReport report =
        parser.parse("{\"schemaVersion\":1,\"projectName\":\"pyapp\",\"files\":{}}");

    assertEquals("python", report.getLanguage());
  }

  // --- Pitest edge cases ---

  @Test
  void pitestMissingStatusIsIgnoredNotSurvived() throws IOException {
    String xml =
        """
        <mutations>
            <mutation status="NOT_STARTED"><sourceFile>A.java</sourceFile>
                <mutatedClass>a.A</mutatedClass><lineNumber>1</lineNumber>
                <mutator>X</mutator></mutation>
            <mutation><sourceFile>A.java</sourceFile>
                <mutatedClass>a.A</mutatedClass><lineNumber>2</lineNumber>
                <mutator>X</mutator></mutation>
        </mutations>
        """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(StandardCharsets.UTF_8), "x");

    assertEquals("UNKNOWN", report.getMutants().get(0).getStatus());
    assertEquals("UNKNOWN", report.getMutants().get(1).getStatus());
    assertEquals(2, report.getSummary().getIgnored());
    assertEquals(0, report.getSummary().getSurvived());
    assertEquals(2, report.getFiles().get(0).getMetrics().get("ignored"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MEMORY_ERROR", "RUN_ERROR", "NON_VIABLE"})
  void pitestCountsItsDetectedErrorStatusesAsKilled(String status) throws IOException {
    String xml =
        "<mutations><mutation status=\""
            + status
            + "\"><sourceFile>A.java</sourceFile><mutatedClass>a.A</mutatedClass>"
            + "</mutation><mutation status=\"SURVIVED\"><sourceFile>A.java</sourceFile>"
            + "<mutatedClass>a.A</mutatedClass></mutation></mutations>";

    NormalizedMutationReport report = parser.parse(xml.getBytes(StandardCharsets.UTF_8), "x");

    NormalizedMutationReport.MutationMutant mutant = report.getMutants().get(0);
    assertEquals("KILLED", mutant.getStatus());
    assertEquals(status, mutant.getStatusReason());
    assertEquals(50.0, report.getSummary().getScore(), 0.001);
  }

  @Test
  void pitestMapsSparseMutationElementsToSafeDefaults() throws IOException {
    String xml =
        """
        <mutations>
            <mutation status="SURVIVED"><sourceFile>Top.java</sourceFile>
                <mutatedClass>Top</mutatedClass><lineNumber>x</lineNumber>
                <mutator>ShortMutator</mutator></mutation>
            <mutation status="KILLED"><sourceFile>Orphan.java</sourceFile>
                <killingTest>a.OrphanTest.t</killingTest></mutation>
            <mutation status="KILLED"><mutatedClass>a.b.Gen</mutatedClass></mutation>
        </mutations>
        """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(StandardCharsets.UTF_8), "x");

    NormalizedMutationReport.MutationMutant top = report.getMutants().get(0);
    assertEquals("Top.java", top.getFilePath());
    assertEquals("ShortMutator", top.getMutatorName());
    assertEquals(0, top.getLocation().getStart().getLine());
    assertTrue(top.getCoveredBy().isEmpty());

    NormalizedMutationReport.MutationMutant orphan = report.getMutants().get(1);
    assertEquals("Orphan.java", orphan.getFilePath());
    assertEquals("", orphan.getMutatorName());
    assertEquals(List.of("a.OrphanTest.t"), orphan.getCoveredBy());

    assertEquals("a/b/Gen.java", report.getMutants().get(2).getFilePath());
  }
}
