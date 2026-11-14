package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.chillcode.sonar.quality.mutation.StoredReports;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MutationReportGuardTest {

  private final MutationReportParser parser = new MutationReportParser();

  private List<String> problemsOf(String project) throws IOException {
    return MutationReportGuard.problems(
        parser.parse(StoredReports.stryker(project), "mutation.json"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "AssetAllocator",
        "real-life-rpg",
        "chillcode-website",
        "nutrition",
        "doccomplete"
      })
  void acceptsTheStoredGoodReports(String project) throws IOException {
    // nutrition: 883 CompileErrors, 109 timeouts and 6 RuntimeErrors in 2787 scored mutants
    // (4.1 %); doccomplete: one RuntimeError in 2800.
    assertEquals(List.of(), problemsOf(project));
  }

  @Test
  void acceptsTheStoredPitestReportOfWalletchartInTheNormalizedFormat() throws IOException {
    assertEquals(
        List.of(),
        MutationReportGuard.problems(
            parser.parse(StoredReports.normalized("Walletchart"), "mutation.json")));
  }

  @Test
  void rejectsTheMassTimeoutNightlyOfDevelopmentStandards() throws IOException {
    assertEquals(
        List.of(
            "6733 timed out and 0 ended in RuntimeError, 88.5 % of the 7606 scored mutants"
                + " (limit 10 % together): a share this high means the test runner overran its"
                + " timeouts or crashed, not that the tests decided the mutants"),
        problemsOf("development-standards"));
  }

  private static MutationMutant mutant(String status) {
    MutationMutant mutant = new MutationMutant();
    mutant.setFilePath("src/a.ts");
    mutant.setStatus(status);
    return mutant;
  }

  private static NormalizedMutationReport report(String... statusCounts) {
    List<MutationMutant> mutants = new ArrayList<>();
    for (String statusCount : statusCounts) {
      String[] parts = statusCount.split("=");
      for (int i = 0; i < Integer.parseInt(parts[1]); i++) {
        mutants.add(mutant(parts[0]));
      }
    }
    return ReportAssembler.assemble("stryker", "typescript", mutants);
  }

  @Test
  void allowsExactlyTenPercentTimeoutsAndRejectsMore() {
    assertEquals(List.of(), MutationReportGuard.problems(report("TIMEOUT=10", "KILLED=90")));
    List<String> problems = MutationReportGuard.problems(report("TIMEOUT=11", "KILLED=89"));
    assertEquals(1, problems.size());
    assertTrue(
        problems.get(0).startsWith("11 timed out and 0 ended in RuntimeError, 11.0 % of the 100"));
  }

  @Test
  void countsRuntimeErrorsAgainstTheSameLimitAsTimeouts() {
    // RuntimeErrors are not scored: 10 unsettled of 100 scored mutants is the limit.
    assertEquals(
        List.of(),
        MutationReportGuard.problems(report("TIMEOUT=4", "RUNTIME_ERROR=6", "KILLED=96")));
    assertEquals(
        List.of(
            "4 timed out and 7 ended in RuntimeError, 11.0 % of the 100 scored mutants"
                + " (limit 10 % together): a share this high means the test runner overran its"
                + " timeouts or crashed, not that the tests decided the mutants"),
        MutationReportGuard.problems(report("TIMEOUT=4", "RUNTIME_ERROR=7", "KILLED=96")));
  }

  @Test
  void rejectsRuntimeErrorsWhenNothingWasScored() {
    assertEquals(1, MutationReportGuard.problems(report("RUNTIME_ERROR=1")).size());
  }

  @Test
  void ignoredAndCompileErrorMutantsDoNotDiluteTheShare() {
    // 2 of 10 scored mutants (20 %) time out; 90 compile errors leave the share unchanged.
    assertEquals(
        1,
        MutationReportGuard.problems(report("TIMEOUT=2", "KILLED=8", "COMPILE_ERROR=90")).size());
  }

  @Test
  void acceptsEveryDocumentedStatus() {
    assertEquals(
        List.of(),
        MutationReportGuard.problems(
            report(
                "KILLED=96",
                "SURVIVED=1",
                "NO_COVERAGE=1",
                "TIMEOUT=1",
                "RUNTIME_ERROR=1",
                "COMPILE_ERROR=1",
                "ERROR=1",
                "IGNORED=1")));
  }

  @Test
  void rejectsEveryStatusOutsideTheDocumentedListByName() {
    List<MutationMutant> mutants = new ArrayList<>(report("KILLED=5").getMutants());
    mutants.add(mutant("UNKNOWN"));
    mutants.add(mutant("PENDING"));
    mutants.add(mutant("UNKNOWN"));
    mutants.add(mutant("RuntimeError"));
    mutants.add(mutant(null));
    assertEquals(
        List.of(
            "5 mutants have a status outside the documented list (UNKNOWN 2, PENDING 1,"
                + " RuntimeError 1, missing 1), first in src/a.ts: the run did not finish or the"
                + " report is not normalized"),
        MutationReportGuard.problems(ReportAssembler.assemble("stryker", "typescript", mutants)));
  }

  @Test
  void reportsEveryProblemAtOnce() {
    assertEquals(
        2,
        MutationReportGuard.problems(report("TIMEOUT=5", "RUNTIME_ERROR=1", "UNKNOWN=1")).size());
  }

  @Test
  void acceptsAnEmptyReport() throws IOException {
    assertEquals(
        List.of(),
        MutationReportGuard.problems(
            parser.parse("<mutations></mutations>".getBytes(StandardCharsets.UTF_8), "x.xml")));
  }

  @Test
  void checksTheMutantsOfANormalizedUploadThatListsThemOnlyPerFile() throws IOException {
    String perFile =
        "{\"tool\":\"stryker\",\"summary\":{},\"mutants\":[],\"files\":["
            + "{\"path\":\"src/a.ts\",\"mutants\":[{\"status\":\"KILLED\"}]},"
            + "{\"path\":\"src/b.ts\"},"
            + "{\"path\":\"src/c.ts\",\"mutants\":[{\"status\":\"PENDING\"},"
            + "{\"status\":\"KILLED\",\"filePath\":\"src/c.ts\"}]}]}";
    assertEquals(
        List.of(
            "1 mutant has a status outside the documented list (PENDING 1), first in src/c.ts:"
                + " the run did not finish or the report is not normalized"),
        MutationReportGuard.problems(parser.parse(perFile)));
  }
}
