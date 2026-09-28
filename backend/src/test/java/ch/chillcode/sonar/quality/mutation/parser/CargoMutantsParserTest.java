package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CargoMutantsParserTest {

  private final MutationReportParser parser = new MutationReportParser();

  /** A baseline and one mutant per outcome, in the shape cargo-mutants writes. */
  private static final String OUTCOMES =
      """
      {
        "outcomes": [
          {"scenario": "Baseline", "summary": "Success"},
          %s,
          %s,
          %s,
          %s
        ],
        "total_mutants": 4, "missed": 1, "caught": 1, "timeout": 1, "unviable": 1,
        "success": 0, "cargo_mutants_version": "25.3.1"
      }
      """
          .formatted(
              outcome("src/indexer.rs", 48, "FnValue", "None", "CaughtMutant"),
              outcome("src/indexer.rs", 61, "BinaryOperator", "!=", "MissedMutant"),
              outcome("src/search.rs", 12, "FnValue", "0", "Timeout"),
              outcome("src/search.rs", 30, "FnValue", "Default::default()", "Unviable"));

  private static String outcome(
      String file, int line, String genre, String replacement, String summary) {
    return """
        {"scenario": {"Mutant": {
          "name": "%1$s:%2$d:5: replace with %4$s",
          "package": "app", "file": "%1$s",
          "span": {"start": {"line": %2$d, "column": 5}, "end": {"line": %2$d, "column": 20}},
          "replacement": "%4$s", "genre": "%3$s"}},
         "summary": "%5$s"}
        """
        .formatted(file, line, genre, replacement, summary);
  }

  private NormalizedMutationReport parse() throws IOException {
    return parser.parse(OUTCOMES.getBytes(StandardCharsets.UTF_8), "outcomes.json");
  }

  @Test
  void recognizesAnOutcomesFileAsCargoMutants() throws IOException {
    NormalizedMutationReport report = parse();

    assertEquals("cargo-mutants", report.getTool());
    assertEquals("rust", report.getLanguage());
  }

  @Test
  void skipsTheBaselineAndCountsEachOutcome() throws IOException {
    NormalizedMutationReport.MutationSummary summary = parse().getSummary();

    assertEquals(4, summary.getTotal());
    assertEquals(1, summary.getKilled());
    assertEquals(1, summary.getSurvived());
    assertEquals(1, summary.getTimeout());
    assertEquals(1, summary.getIgnored());
  }

  @Test
  void leavesAnUnviableMutantOutOfTheScore() throws IOException {
    assertEquals(100.0 / 3, parse().getSummary().getScore(), 1e-9);
  }

  @Test
  void keepsTheFileSpanGenreAndReplacement() throws IOException {
    MutationMutant missed = parse().getMutants().get(1);

    assertEquals("src/indexer.rs", missed.getFilePath());
    assertEquals(61, missed.getLocation().getStart().getLine());
    assertEquals(20, missed.getLocation().getEnd().getColumn());
    assertEquals("BinaryOperator", missed.getMutatorName());
    assertEquals("!=", missed.getReplacement());
    assertEquals("SURVIVED", missed.getStatus());
  }

  @Test
  void groupsMutantsByFileWithTheirOwnScore() throws IOException {
    NormalizedMutationReport report = parse();

    assertEquals(2, report.getFiles().size());
    Map<String, Object> indexer = report.getFiles().get(0).getMetrics();
    assertEquals(50.0, (double) indexer.get("score"), 1e-9);
    assertEquals(2, indexer.get("total"));
  }

  @Test
  void readsAnUnknownOutcomeAsUnknown() {
    assertEquals("UNKNOWN", CargoMutantsParser.statusOf("Success"));
  }
}
