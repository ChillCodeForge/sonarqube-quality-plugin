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
  @ValueSource(strings = {"AssetAllocator", "real-life-rpg", "chillcode-website"})
  void acceptsTheStoredGoodReports(String project) throws IOException {
    assertEquals(List.of(), problemsOf(project));
  }

  @Test
  void rejectsTheMassTimeoutNightlyOfDevelopmentStandards() throws IOException {
    assertEquals(
        List.of(
            "6733 of 7606 scored mutants timed out (88.5 %, limit 10 %): a share this high means"
                + " the test runner overran its timeouts, not that the mutants loop"),
        problemsOf("development-standards"));
  }

  @Test
  void rejectsRuntimeErrorsButNotCompileErrorsOrALowTimeoutShare() throws IOException {
    // nutrition: 883 CompileErrors and 109 of 2787 scored mutants timing out (3.9 %) are fine,
    // its 6 crashed test runs are not.
    assertEquals(
        List.of(
            "6 mutants ended in RuntimeError (the test runner crashed instead of judging them),"
                + " first in src/pages/SettingsPage.tsx"),
        problemsOf("nutrition"));
    assertEquals(
        List.of(
            "1 mutant ended in RuntimeError (the test runner crashed instead of judging it),"
                + " first in src/api/matchingApi.ts"),
        problemsOf("doccomplete"));
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
    assertTrue(problems.get(0).startsWith("11 of 100 scored mutants timed out (11.0 %"));
  }

  @Test
  void ignoredAndCompileErrorMutantsDoNotDiluteTheTimeoutShare() {
    // 2 of 10 scored mutants (20 %) time out; 90 compile errors leave the share unchanged.
    assertEquals(
        1,
        MutationReportGuard.problems(report("TIMEOUT=2", "KILLED=8", "COMPILE_ERROR=90")).size());
  }

  @Test
  void rejectsMutantsWithoutAFinalStatusAsAnIncompleteReport() {
    assertEquals(
        List.of(
            "2 mutants have no final status (pending or unknown), first in src/a.ts:"
                + " the report is incomplete"),
        MutationReportGuard.problems(report("KILLED=5", "UNKNOWN=2")));
  }

  @Test
  void reportsEveryProblemAtOnce() {
    assertEquals(
        3,
        MutationReportGuard.problems(report("TIMEOUT=5", "RUNTIME_ERROR=1", "UNKNOWN=1")).size());
  }

  @Test
  void acceptsAnEmptyReport() throws IOException {
    assertEquals(
        List.of(),
        MutationReportGuard.problems(
            parser.parse("<mutations></mutations>".getBytes(StandardCharsets.UTF_8), "x.xml")));
  }
}
