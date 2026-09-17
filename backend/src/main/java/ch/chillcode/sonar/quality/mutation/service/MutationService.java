package ch.chillcode.sonar.quality.mutation.service;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MutationService {

    private static final Logger LOG = Loggers.get(MutationService.class);

    private final MutationReportParser parser;
    private final MutationReportStorageService storageService;

    public MutationService(MutationReportStorageService storageService) {
        this.parser = new MutationReportParser();
        this.storageService = storageService;
    }

    public void processReport(File reportFile, SensorContext context) {
        try {
            NormalizedMutationReport report = parser.parse(reportFile);

            // Store full report
            storageService.storeReport(report);

            // Write measures
            writeMeasures(report, context);

            // Create issues for survived and no-coverage mutants
            createIssues(report, context);

            LOG.info("Processed mutation report: project={}, tool={}, score={}",
                    report.getProject(), report.getTool(), report.getSummary().getScore());

        } catch (Exception e) {
            LOG.error("Failed to process mutation report: " + reportFile.getAbsolutePath(), e);
        }
    }

    private void writeMeasures(NormalizedMutationReport report, SensorContext context) {
        NormalizedMutationReport.MutationSummary summary = report.getSummary();

        // Core metrics
        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_SCORE)
                .withValue(summary.getScore())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_TOTAL)
                .withValue(summary.getTotal())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_KILLED)
                .withValue(summary.getKilled())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_SURVIVED)
                .withValue(summary.getSurvived())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_NO_COVERAGE)
                .withValue(summary.getNoCoverage())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_TIMEOUT)
                .withValue(summary.getTimeout())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_IGNORED)
                .withValue(summary.getIgnored())
                .save();

        // Tool and language
        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_TOOL)
                .withValue(report.getTool())
                .save();

        context.<Metric>newMeasure()
                .withMetric(MutationMetrics.MUTATION_LANGUAGE)
                .withValue(report.getLanguage())
                .save();
    }

    private void createIssues(NormalizedMutationReport report, SensorContext context) {
        if (report.getMutants() == null || report.getMutants().isEmpty()) {
            return;
        }

        for (NormalizedMutationReport.MutationMutant mutant : report.getMutants()) {
            // Only create issues for SURVIVED and NO_COVERAGE
            if (!"SURVIVED".equals(mutant.getStatus()) && !"NO_COVERAGE".equals(mutant.getStatus())) {
                continue;
            }

            NormalizedMutationReport.MutationLocation loc = mutant.getLocation();
            if (loc == null || loc.getStart() == null) {
                continue;
            }

            String filePath = mutant.getLocation().getFilePath(); // We'll need to add this to the model
            // For now, skip file-level issues if we can't locate the file
            // In a full implementation, we'd map the mutant to the correct InputFile
        }
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