package ch.chillcode.sonar.quality.mutation.service;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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

  public void processReport(File reportFile, SensorContext context) throws IOException {
    NormalizedMutationReport report = parser.parse(reportFile);
    writeMeasures(report, context);

    LOG.info(
        "Processed mutation report: project={}, tool={}, score={}",
        report.getProject(),
        report.getTool(),
        report.getSummary().getScore());
  }

  private void writeMeasures(NormalizedMutationReport report, SensorContext context) {
    NormalizedMutationReport.MutationSummary summary = report.getSummary();

    // Core metrics
    saveProjectMeasure(context, MutationMetrics.MUTATION_SCORE, summary.getScore());
    saveProjectMeasure(context, MutationMetrics.MUTATION_TOTAL, summary.getTotal());
    saveProjectMeasure(context, MutationMetrics.MUTATION_KILLED, summary.getKilled());
    saveProjectMeasure(context, MutationMetrics.MUTATION_SURVIVED, summary.getSurvived());
    saveProjectMeasure(context, MutationMetrics.MUTATION_NO_COVERAGE, summary.getNoCoverage());
    saveProjectMeasure(context, MutationMetrics.MUTATION_TIMEOUT, summary.getTimeout());
    saveProjectMeasure(context, MutationMetrics.MUTATION_IGNORED, summary.getIgnored());

    // Tool and language
    saveProjectMeasure(context, MutationMetrics.MUTATION_TOOL, report.getTool());
    saveProjectMeasure(context, MutationMetrics.MUTATION_LANGUAGE, report.getLanguage());
  }

  /**
   * The one way this plugin saves a scanner-side measure. It goes on the project: the scanner has
   * no default component, and a measure without one fails the whole analysis.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void saveProjectMeasure(SensorContext context, Metric metric, Serializable value) {
    context.newMeasure().on(context.project()).forMetric(metric).withValue(value).save();
  }
}
