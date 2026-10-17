package ch.chillcode.sonar.quality.mutation.service;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import java.io.File;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * Scanner-side: turns a mutation report into measures on the analysed project. It never touches the
 * report storage, which lives on the SonarQube server and is filled only by the upload API.
 */
public class MutationService {

  private static final Logger LOG = Loggers.get(MutationService.class);

  private final MutationReportParser parser = new MutationReportParser();

  public void processReport(File reportFile, SensorContext context) {
    try {
      NormalizedMutationReport report = parser.parse(reportFile);
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
}
