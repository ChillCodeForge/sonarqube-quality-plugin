package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationFile;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationSummary;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a normalized report from a flat list of mutants whose statuses are already normalized: the
 * summary, the per-file breakdown the UI's file table reads, and the score.
 *
 * <p>Any status other than KILLED, SURVIVED, NO_COVERAGE and TIMEOUT counts as ignored and leaves
 * the score's denominator, which is how PIT treats a mutant that did not compile. The score is
 * killed over the rest, in percent, and 0 when nothing is left.
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
    report.setTimestamp(LocalDateTime.now());

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
      file.setMetrics(countsOf(entry.getValue()).asMetrics());
      files.add(file);
    }

    Counts counts = countsOf(mutants);
    MutationSummary summary = new MutationSummary();
    summary.setTotal(counts.total);
    summary.setKilled(counts.killed);
    summary.setSurvived(counts.survived);
    summary.setNoCoverage(counts.noCoverage);
    summary.setTimeout(counts.timeout);
    summary.setIgnored(counts.ignored);
    summary.setScore(counts.score());

    report.setSummary(summary);
    report.setFiles(files);
    report.setMutants(mutants);
    return report;
  }

  private static Counts countsOf(List<MutationMutant> mutants) {
    Counts counts = new Counts();
    for (MutationMutant mutant : mutants) {
      counts.total++;
      switch (mutant.getStatus()) {
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
      int relevant = total - ignored;
      return relevant > 0 ? (double) killed / relevant * 100 : 0.0;
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
