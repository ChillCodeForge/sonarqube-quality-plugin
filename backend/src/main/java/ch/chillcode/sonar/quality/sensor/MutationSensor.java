package ch.chillcode.sonar.quality.sensor;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.service.MutationService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class MutationSensor implements Sensor {

  private static final Logger LOG = Loggers.get(MutationSensor.class);

  private final Configuration config;
  private final MutationService mutationService = new MutationService();

  // SonarQube's Spring-based DI only injects dependencies declared as
  // constructor parameters for Sensor extensions - a no-arg constructor
  // plus a manual setConfiguration()/etc. is never called automatically,
  // so config stayed null and every analysis run that
  // reached this sensor crashed with a NullPointerException (confirmed:
  // it aborted sonarqube-analysis for every project running our plugin,
  // not just ones using mutation testing).
  public MutationSensor(Configuration config) {
    this.config = config;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    // Every analysis: the branch hand-off below is needed whatever the
    // project's language, and so is a local report from any supported tool.
    descriptor.name("ChillCode Mutation Testing Sensor");
  }

  @Override
  public void execute(SensorContext context) {
    recordReportBranch(context);

    // Reports uploaded via POST /api/chillcode_mutation/upload live on the
    // server, which this scanner-side sensor cannot see; the
    // MutationMeasureComputer publishes those. This sensor handles only a
    // local file, for a scanner running in the job that produced the report.
    String reportPath = config.get("sonar.chillcode.mutationReport").orElse(null);
    if (reportPath == null || reportPath.isEmpty()) {
      LOG.info("No sonar.chillcode.mutationReport configured - skipping");
      return;
    }

    Path reportFilePath = Paths.get(reportPath);
    if (!Files.exists(reportFilePath)) {
      LOG.warn("Mutation report file not found: {}", reportPath);
      return;
    }

    try {
      File reportFile = reportFilePath.toFile();
      LOG.info("Processing mutation report: {}", reportFile.getAbsolutePath());
      mutationService.processReport(reportFile, context);
    } catch (Exception e) {
      LOG.error("Failed to process mutation report: " + reportPath, e);
    }
  }

  // Tells the MutationMeasureComputer which uploaded report belongs to this
  // analysis. A pull request has none of its own: uploads are per branch, and
  // a PR showing its target branch's score would misattribute it.
  private void recordReportBranch(SensorContext context) {
    if (config.get("sonar.pullrequest.key").isPresent()) {
      return;
    }
    String branch = config.get("sonar.branch.name").orElse("main");
    context
        .<String>newMeasure()
        .forMetric(MutationMetrics.MUTATION_REPORT_BRANCH)
        .withValue(branch)
        .save();
  }
}
