package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportAssemblerTest {

  private static MutationMutant mutant(String file, String status) {
    MutationMutant mutant = new MutationMutant();
    mutant.setFilePath(file);
    mutant.setStatus(status);
    return mutant;
  }

  @Test
  void countsEveryNormalizedStatusAndIgnoresTheRest() {
    NormalizedMutationReport report =
        ReportAssembler.assemble(
            "tool",
            "lang",
            List.of(
                mutant("a", "KILLED"),
                mutant("a", "SURVIVED"),
                mutant("b", "NO_COVERAGE"),
                mutant("b", "TIMEOUT"),
                mutant("b", "NON_VIABLE")));

    NormalizedMutationReport.MutationSummary summary = report.getSummary();
    assertEquals(5, summary.getTotal());
    assertEquals(1, summary.getKilled());
    assertEquals(1, summary.getSurvived());
    assertEquals(1, summary.getNoCoverage());
    assertEquals(1, summary.getTimeout());
    assertEquals(1, summary.getIgnored());
    assertEquals(25.0, summary.getScore(), 1e-9);

    Map<String, Object> fileB = report.getFiles().get(1).getMetrics();
    assertEquals("b", report.getFiles().get(1).getPath());
    assertEquals(1, fileB.get("noCoverage"));
    assertEquals(0.0, (double) fileB.get("score"), 1e-9);
  }

  @Test
  void scoresZeroWhenEveryMutantIsIgnored() {
    NormalizedMutationReport report =
        ReportAssembler.assemble("tool", "lang", List.of(mutant("a", "NON_VIABLE")));

    assertEquals(0.0, report.getSummary().getScore(), 1e-9);
    assertEquals(0.0, (double) report.getFiles().get(0).getMetrics().get("score"), 1e-9);
  }

  @Test
  void anEmptyMutantListYieldsAnEmptyReport() {
    NormalizedMutationReport report = ReportAssembler.assemble("tool", "lang", List.of());

    assertTrue(report.getFiles().isEmpty());
    assertEquals(0, report.getSummary().getTotal());
    assertEquals(0.0, report.getSummary().getScore(), 1e-9);
  }
}
