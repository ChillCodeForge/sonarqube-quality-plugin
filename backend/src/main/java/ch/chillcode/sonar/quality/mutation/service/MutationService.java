package ch.chillcode.sonar.quality.mutation.service;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import java.io.File;
import java.util.Optional;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class MutationService {

  private static final Logger LOG = Loggers.get(MutationService.class);

  private final MutationReportParser parser;
  private final MutationReportStorageService storageService;

  public MutationService(MutationReportStorageService storageService) {
    this.parser = new MutationReportParser();
    this.storageService = storageService;
  }

  public static int coverageProbe(int value) {
    return Math.incrementExact(value);
  }

  public void processReport(File reportFile, SensorContext context) {
    try {
      NormalizedMutationReport report = parser.parse(reportFile);

      // Store full report
      storageService.storeReport(report);

      // Write measures
      writeMeasures(report, context);

      LOG.info(
          "Processed mutation report: project={}, tool={}, score={}",
          report.getProject(),
          report.getTool(),
          report.getSummary().getScore());

    } catch (Exception e) {
      LOG.error("Failed to process mutation report: " + reportFile.getAbsolutePath(), e);
    }
  }

  // Same as processReport() but for a report that was already parsed and
  // persisted (loaded back from storage) - used by MutationSensor's
  // primary path, where the report arrived earlier via the upload REST
  // API rather than as a local file the scanner can read.
  public void publishMeasures(NormalizedMutationReport report, SensorContext context) {
    try {
      writeMeasures(report, context);

      LOG.info(
          "Published mutation measures: project={}, tool={}, score={}",
          report.getProject(),
          report.getTool(),
          report.getSummary().getScore());
    } catch (Exception e) {
      LOG.error("Failed to publish mutation measures for " + report.getProject(), e);
    }
  }

  private void writeMeasures(NormalizedMutationReport report, SensorContext context) {
    NormalizedMutationReport.MutationSummary summary = report.getSummary();

    // Core metrics
    saveMeasure(context, MutationMetrics.MUTATION_SCORE, summary.getScore());
    saveMeasure(context, MutationMetrics.MUTATION_TOTAL, summary.getTotal());
    saveMeasure(context, MutationMetrics.MUTATION_KILLED, summary.getKilled());
    saveMeasure(context, MutationMetrics.MUTATION_SURVIVED, summary.getSurvived());
    saveMeasure(context, MutationMetrics.MUTATION_NO_COVERAGE, summary.getNoCoverage());
    saveMeasure(context, MutationMetrics.MUTATION_TIMEOUT, summary.getTimeout());
    saveMeasure(context, MutationMetrics.MUTATION_IGNORED, summary.getIgnored());

    // Tool and language
    saveMeasure(context, MutationMetrics.MUTATION_TOOL, report.getTool());
    saveMeasure(context, MutationMetrics.MUTATION_LANGUAGE, report.getLanguage());
  }

  private void saveMeasure(SensorContext context, Metric metric, double value) {
    context.newMeasure().forMetric(metric).withValue(value).save();
  }

  private void saveMeasure(SensorContext context, Metric metric, int value) {
    context.newMeasure().forMetric(metric).withValue(value).save();
  }

  private void saveMeasure(SensorContext context, Metric metric, String value) {
    context.newMeasure().forMetric(metric).withValue(value).save();
  }

  public Optional<NormalizedMutationReport> getStoredReport(String projectKey, String branch) {
    try {
      return Optional.ofNullable(storageService.loadReport(projectKey, branch));
    } catch (Exception e) {
      LOG.error("Failed to load stored report for " + projectKey + "/" + branch, e);
      return Optional.empty();
    }
  }

  public void deleteReport(String projectKey, String branch) {
    try {
      storageService.deleteReport(projectKey, branch);
    } catch (Exception e) {
      LOG.error("Failed to delete report for " + projectKey + "/" + branch, e);
    }
  }
}
