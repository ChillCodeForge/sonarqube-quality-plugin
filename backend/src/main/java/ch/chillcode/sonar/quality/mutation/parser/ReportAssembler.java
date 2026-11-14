package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationFile;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationSummary;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a normalized report from a flat list of mutants whose statuses are already normalized: the
 * summary, the per-file breakdown the UI's file table reads, and the score.
 *
 * <p>The score follows Stryker's mutation score (mutation-testing-elements): detected mutants
 * (KILLED and TIMEOUT) over valid ones (detected, SURVIVED and NO_COVERAGE), in percent, and 0 when
 * nothing is valid. Any other status (ignored, compile or runtime error, non-viable, unknown)
 * counts as ignored and leaves both sides of the ratio. This is the only place a score is computed;
 * parsers translate their tool's statuses so that this ratio equals the tool's own score.
 */
final class ReportAssembler {

  private ReportAssembler() {}

  static NormalizedMutationReport assemble(
      String tool, String language, List<MutationMutant> mutants) {
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setSchemaVersion(1);
    report.setTool(tool);
    report.setLanguage(language);
    report.setProject("unknown");
    report.setBranch("main");
    report.setCommit("");
    report.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

    Map<String, List<MutationMutant>> byFile = new LinkedHashMap<>();
    for (MutationMutant mutant : mutants) {
      byFile.computeIfAbsent(mutant.getFilePath(), path -> new ArrayList<>()).add(mutant);
    }

    List<MutationFile> files = new ArrayList<>();
    for (Map.Entry<String, List<MutationMutant>> entry : byFile.entrySet()) {
      MutationFile file = new MutationFile();
      file.setPath(entry.getKey());
      file.setLanguage(language);
      file.setMutants(entry.getValue());
      file.setMetrics(metricsOf(entry.getValue()));
      files.add(file);
    }

    report.setSummary(summaryOf(mutants));
    report.setFiles(files);
    report.setMutants(mutants);
    return report;
  }

  /** The counts and score of {@code mutants}, as a report summary. */
  static MutationSummary summaryOf(List<MutationMutant> mutants) {
    Counts counts = countsOf(mutants);
    MutationSummary summary = new MutationSummary();
    summary.setTotal(counts.total);
    summary.setKilled(counts.killed);
    summary.setSurvived(counts.survived);
    summary.setNoCoverage(counts.noCoverage);
    summary.setTimeout(counts.timeout);
    summary.setIgnored(counts.ignored);
    summary.setScore(counts.score());
    return summary;
  }

  /** The counts and score of one file's {@code mutants}, as the UI's file-table metrics. */
  static Map<String, Object> metricsOf(List<MutationMutant> mutants) {
    return countsOf(mutants).asMetrics();
  }

  private static Counts countsOf(List<MutationMutant> mutants) {
    Counts counts = new Counts();
    for (MutationMutant mutant : mutants) {
      counts.total++;
      // A missing status counts as ignored here; the upload guard rejects it.
      switch (Objects.requireNonNullElse(mutant.getStatus(), "")) {
        case "KILLED" -> counts.killed++;
        case "SURVIVED" -> counts.survived++;
        case "NO_COVERAGE" -> counts.noCoverage++;
        case "TIMEOUT" -> counts.timeout++;
        default -> counts.ignored++;
      }
    }
    return counts;
  }

  private static final class Counts {
    int total;
    int killed;
    int survived;
    int noCoverage;
    int timeout;
    int ignored;

    double score() {
      int detected = killed + timeout;
      int valid = detected + survived + noCoverage;
      return valid > 0 ? (double) detected / valid * 100 : 0.0;
    }

    Map<String, Object> asMetrics() {
      Map<String, Object> metrics = new HashMap<>();
      metrics.put("score", score());
      metrics.put("total", total);
      metrics.put("killed", killed);
      metrics.put("survived", survived);
      metrics.put("noCoverage", noCoverage);
      metrics.put("timeout", timeout);
      metrics.put("ignored", ignored);
      return metrics;
    }
  }
}
