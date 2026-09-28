package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationLocation;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a session file Ruby's mutant (0.16 and later) writes to {@code .mutant/results/}, as
 * documented in mutant's {@code docs/session-json-schema.yml}.
 *
 * <p>Only {@code evil} mutations are mutants. {@code neutral} and {@code noop} are mutant's own
 * control runs, which re-insert the original code and must pass, so they say nothing about the
 * tests and are skipped.
 *
 * <p>A mutant's line is its subject's first line, taken from the subject identification ({@code
 * Foo#bar:lib/foo.rb:42}). mutant diffs unparsed source, whose lines need not match the file, so a
 * finer line would be a guess: locations are accurate at method granularity.
 */
final class RubyMutantParser {

  private static final Pattern TRAILING_LINE = Pattern.compile(":(\\d+)$");

  private RubyMutantParser() {}

  /** Whether {@code root} is a mutant session file. */
  static boolean accepts(JsonNode root) {
    return root.has("subject_results") && root.has("mutant_version");
  }

  static NormalizedMutationReport parse(JsonNode root) {
    List<MutationMutant> mutants = new ArrayList<>();
    for (JsonNode subject : root.path("subject_results")) {
      for (JsonNode coverage : subject.path("coverage_results")) {
        JsonNode mutation = coverage.path("mutation_result");
        if ("evil".equals(mutation.path("mutation_type").asText())) {
          mutants.add(toMutant(subject, mutation, coverage.path("criteria_result")));
        }
      }
    }
    return ReportAssembler.assemble("mutant", "ruby", mutants);
  }

  private static MutationMutant toMutant(JsonNode subject, JsonNode mutation, JsonNode criteria) {
    MutationMutant mutant = new MutationMutant();
    mutant.setId(mutation.path("mutation_identification").asText());
    mutant.setMutatorName(subject.path("expression_syntax").asText());
    mutant.setReplacement(addedLines(mutation.path("mutation_diff").asText("")));
    mutant.setStatus(statusOf(criteria));
    mutant.setStatusReason("");
    mutant.setFilePath(subject.path("source_path").asText("unknown"));
    mutant.setLocation(lineOf(subject.path("identification").asText()));
    List<String> tests = new ArrayList<>();
    subject.path("tests").forEach(test -> tests.add(test.asText()));
    mutant.setCoveredBy(tests);
    mutant.setStaticMutant(false);
    return mutant;
  }

  /**
   * A test failure or an aborted process killed the mutant; a timeout alone is reported as one, the
   * way the other tools' timeouts are; anything else survived.
   */
  static String statusOf(JsonNode criteria) {
    if (criteria.path("test_result").asBoolean() || criteria.path("process_abort").asBoolean()) {
      return "KILLED";
    }
    return criteria.path("timeout").asBoolean() ? "TIMEOUT" : "SURVIVED";
  }

  /** The lines a unified diff adds, which is the mutated code a reader wants to see. */
  static String addedLines(String diff) {
    List<String> added = new ArrayList<>();
    for (String line : diff.split("\\R")) {
      if (line.startsWith("+") && !line.startsWith("+++")) {
        added.add(line.substring(1).strip());
      }
    }
    return String.join("\n", added);
  }

  private static MutationLocation lineOf(String subjectIdentification) {
    Matcher matcher = TRAILING_LINE.matcher(subjectIdentification);
    int line = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    MutationLocation location = new MutationLocation();
    location.setStart(positionAt(line));
    location.setEnd(positionAt(line));
    return location;
  }

  private static MutationLocation.Position positionAt(int line) {
    MutationLocation.Position position = new MutationLocation.Position();
    position.setLine(line);
    return position;
  }
}
