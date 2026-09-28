package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RubyMutantParserTest {

  private final MutationReportParser parser = new MutationReportParser();

  /**
   * One subject with a killed, a survived, an aborted and a timed-out evil mutation, plus the
   * neutral control run mutant always adds, in the shape of docs/session-json-schema.yml.
   */
  private static final String SESSION =
      """
      {
        "killtime": 1.5, "mutant_version": "0.17.0", "pid": 42, "ruby_version": "3.4.1",
        "runtime": 2.0, "session_id": "0192f0a8-0000-7000-8000-000000000000",
        "subject_results": [{
          "amount_mutations": 5,
          "expression_syntax": "Shop::Cart#total",
          "identification": "Shop::Cart#total:lib/shop/cart.rb:12",
          "source": "def total; end",
          "source_path": "lib/shop/cart.rb",
          "tests": ["rspec:0:./spec/cart_spec.rb:4/Shop::Cart#total sums"],
          "coverage_results": [%s, %s, %s, %s, %s]
        }]
      }
      """
          .formatted(
              result("evil", "a1b2c", "+  items.sum(&:price) - 1", true, false, false),
              result("evil", "d4e5f", "+  nil", false, false, false),
              result("evil", "01234", "+  raise", false, true, false),
              result("evil", "56789", "+  loop {}", false, false, true),
              result("neutral", "abcde", "", false, false, false));

  private static String result(
      String type,
      String code,
      String added,
      boolean testResult,
      boolean processAbort,
      boolean timeout) {
    String diff =
        added.isEmpty()
            ? ""
            : "@@ -1,3 +1,3 @@\\n def total\\n-  items.sum(&:price)\\n" + added + "\\n end\\n";
    return """
        {"mutation_result": {
           "mutation_type": "%1$s",
           "mutation_identification": "%1$s:Shop::Cart#total:lib/shop/cart.rb:12:%2$s",
           "mutation_diff": "%3$s", "mutation_source": "def total; end", "runtime": 0.1,
           "isolation_result": {"exception": null, "log": "", "process_status": null,
                                "timeout": null, "value": null}},
         "criteria_result": {"test_result": %4$s, "process_abort": %5$s, "timeout": %6$s}}
        """
        .formatted(type, code, diff, testResult, processAbort, timeout);
  }

  private NormalizedMutationReport parse() throws IOException {
    return parser.parse(SESSION.getBytes(StandardCharsets.UTF_8), "session.json");
  }

  @Test
  void recognizesASessionFileAsMutant() throws IOException {
    NormalizedMutationReport report = parse();

    assertEquals("mutant", report.getTool());
    assertEquals("ruby", report.getLanguage());
  }

  @Test
  void countsEvilMutationsAndSkipsTheControlRun() throws IOException {
    NormalizedMutationReport.MutationSummary summary = parse().getSummary();

    assertEquals(4, summary.getTotal());
    assertEquals(2, summary.getKilled());
    assertEquals(1, summary.getSurvived());
    assertEquals(1, summary.getTimeout());
    assertEquals(50.0, summary.getScore(), 1e-9);
  }

  @Test
  void placesAMutantOnItsSubjectsLineInItsSourceFile() throws IOException {
    MutationMutant survived = parse().getMutants().get(1);

    assertEquals("lib/shop/cart.rb", survived.getFilePath());
    assertEquals(12, survived.getLocation().getStart().getLine());
    assertEquals("SURVIVED", survived.getStatus());
    assertEquals("Shop::Cart#total", survived.getMutatorName());
    assertEquals("evil:Shop::Cart#total:lib/shop/cart.rb:12:d4e5f", survived.getId());
  }

  @Test
  void showsTheCodeTheMutationAdded() throws IOException {
    assertEquals("items.sum(&:price) - 1", parse().getMutants().get(0).getReplacement());
  }

  @Test
  void listsTheSubjectsTestsAsCoveringIt() throws IOException {
    assertEquals(
        List.of("rspec:0:./spec/cart_spec.rb:4/Shop::Cart#total sums"),
        parse().getMutants().get(0).getCoveredBy());
  }

  @Test
  void readsNoAddedLinesFromAnEmptyDiff() {
    assertEquals("", RubyMutantParser.addedLines(""));
  }
}
