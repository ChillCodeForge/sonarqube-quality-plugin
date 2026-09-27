package ch.chillcode.sonar.quality.sensor;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.service.MutationService;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
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
  private MutationReportStorageService storageService;
  private MutationService mutationService;

  // SonarQube's Spring-based DI only injects dependencies declared as
  // constructor parameters for Sensor extensions - a no-arg constructor
  // plus a manual setConfiguration()/etc. is never called automatically,
  // so config/storageService stayed null and every analysis run that
  // reached this sensor crashed with a NullPointerException (confirmed:
  // it aborted sonarqube-analysis for every project running our plugin,
  // not just ones using mutation testing).
  public MutationSensor(Configuration config) {
    this.config = config;
    this.storageService = new MutationReportStorageService();
    this.mutationService = new MutationService(this.storageService);
  }

  public void setMutationReportStorageService(MutationReportStorageService storageService) {
    this.storageService = storageService;
    this.mutationService = new MutationService(storageService);
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("ChillCode Mutation Testing Sensor");
    // Only run on projects that have mutation testing configured
    descriptor.onlyOnLanguages("java", "ts", "tsx", "js", "jsx", "py");
  }

  @Override
  public void execute(SensorContext context) {
    String projectKey = context.project().key();
    String branch = config.get("sonar.branch.name").orElse("main");

    // Primary path: a report already uploaded via
    // POST /api/chillcode_mutation/upload (typically by a separate,
    // weekly mutation-testing CI job) and sitting in our own storage.
    // This is how mutation data actually reaches SonarQube in
    // practice - the scanner container running THIS sensor rarely has
    // a local mutation report file, since mutation testing is its own
    // slow job that runs on a different schedule (see nutrition's
    // .drone.yml mutation-testing pipeline, 1-2x/week).
    try {
      NormalizedMutationReport stored = storageService.loadReport(projectKey, branch);
      if (stored != null) {
        LOG.info(
            "Publishing stored mutation report as measures: project={}, branch={}, score={}",
            projectKey,
            branch,
            stored.getSummary().getScore());
        mutationService.publishMeasures(stored, context);
        return;
      }
    } catch (Exception e) {
      LOG.error("Failed to load stored mutation report for measures: " + projectKey, e);
    }

    // Fallback path: sonar.chillcode.mutationReport points at a local
    // file (e.g. the scanner runs in the same job that produced the
    // report, no separate upload step).
    String reportPath = config.get("sonar.chillcode.mutationReport").orElse(null);
    if (reportPath == null || reportPath.isEmpty()) {
      LOG.info(
          "No stored mutation report and no sonar.chillcode.mutationReport configured - skipping");
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
}
