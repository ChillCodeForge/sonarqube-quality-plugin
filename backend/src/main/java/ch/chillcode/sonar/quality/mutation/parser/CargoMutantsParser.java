package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationLocation;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads cargo-mutants' {@code mutants.out/outcomes.json}.
 *
 * <p>Each outcome carries a {@code scenario}, either the string {@code "Baseline"} (the unmutated
 * build, skipped) or {@code {"Mutant": {...}}} with the file, span, genre and replacement, and a
 * {@code summary} saying how it ended. An unviable mutant failed to build, so it is counted as
 * ignored, as PIT's non-viable mutants are.
 */
final class CargoMutantsParser {

  private CargoMutantsParser() {}

  /** Whether {@code root} is a cargo-mutants outcomes file. */
  static boolean accepts(JsonNode root) {
    return root.has("outcomes") && root.has("cargo_mutants_version");
  }

  static NormalizedMutationReport parse(JsonNode root) {
    List<MutationMutant> mutants = new ArrayList<>();
    for (JsonNode outcome : root.path("outcomes")) {
      JsonNode mutant = outcome.path("scenario").path("Mutant");
      if (mutant.isObject()) {
        mutants.add(toMutant(mutant, outcome.path("summary").asText()));
      }
    }
    return ReportAssembler.assemble("cargo-mutants", "rust", mutants);
  }

  private static MutationMutant toMutant(JsonNode node, String summary) {
    MutationMutant mutant = new MutationMutant();
    mutant.setId(node.path("name").asText());
    mutant.setMutatorName(node.path("genre").asText());
    mutant.setReplacement(node.path("replacement").asText());
    mutant.setStatus(statusOf(summary));
    mutant.setStatusReason(summary);
    mutant.setFilePath(node.path("file").asText("unknown"));
    mutant.setLocation(locationOf(node.path("span")));
    mutant.setCoveredBy(List.of());
    mutant.setStaticMutant(false);
    return mutant;
  }

  /** cargo-mutants' outcome summary as a normalized status. */
  static String statusOf(String summary) {
    return switch (summary) {
      case "CaughtMutant" -> "KILLED";
      case "MissedMutant" -> "SURVIVED";
      case "Timeout" -> "TIMEOUT";
      case "Unviable" -> "NON_VIABLE";
      default -> "UNKNOWN";
    };
  }

  private static MutationLocation locationOf(JsonNode span) {
    MutationLocation location = new MutationLocation();
    location.setStart(positionOf(span.path("start")));
    location.setEnd(positionOf(span.path("end")));
    return location;
  }

  private static MutationLocation.Position positionOf(JsonNode node) {
    MutationLocation.Position position = new MutationLocation.Position();
    position.setLine(node.path("line").asInt(0));
    position.setColumn(node.path("column").asInt(0));
    return position;
  }
}
