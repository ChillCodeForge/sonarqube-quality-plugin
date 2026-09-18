package ch.chillcode.sonar.quality.measure;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.MeasureComputer;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.Set;

// MeasureComputer runs in the Compute Engine, ON THE SONARQUBE SERVER, as
// part of report processing after a scan - unlike Sensor, which runs
// inside the (separate, ephemeral) sonar-scanner container and therefore
// can never see this server's /opt/sonarqube/mutation-reports volume.
// Confirmed by testing: a Sensor-based read of our own storage silently
// found nothing every time because the scanner container has no access to
// it at all. This is the only place in the plugin API that can read our
// storage and also write project-level measures.
public class MutationMeasureComputer implements MeasureComputer {

    private static final Logger LOG = Loggers.get(MutationMeasureComputer.class);

    private final MutationReportStorageService storageService = new MutationReportStorageService();

    @Override
    public MeasureComputerDefinition define(MeasureComputerDefinitionContext defContext) {
        return defContext.newDefinitionBuilder()
                .setOutputMetrics(
                        MutationMetrics.MUTATION_SCORE.getKey(),
                        MutationMetrics.MUTATION_TOTAL.getKey(),
                        MutationMetrics.MUTATION_KILLED.getKey(),
                        MutationMetrics.MUTATION_SURVIVED.getKey(),
                        MutationMetrics.MUTATION_NO_COVERAGE.getKey(),
                        MutationMetrics.MUTATION_TIMEOUT.getKey(),
                        MutationMetrics.MUTATION_IGNORED.getKey(),
                        MutationMetrics.MUTATION_TOOL.getKey(),
                        MutationMetrics.MUTATION_LANGUAGE.getKey()
                )
                .build();
    }

    @Override
    public void compute(MeasureComputerContext context) {
        // Only the project root has a stored report - mutation data is
        // project-wide, not computed per file/directory like coverage.
        if (context.getComponent().getType() != Component.Type.PROJECT) {
            return;
        }

        String projectKey = context.getComponent().getKey();
        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, "main");
            if (report == null) {
                LOG.info("No stored mutation report for {}, skipping measure computation", projectKey);
                return;
            }

            NormalizedMutationReport.MutationSummary summary = report.getSummary();
            context.addMeasure(MutationMetrics.MUTATION_SCORE.getKey(), summary.getScore());
            context.addMeasure(MutationMetrics.MUTATION_TOTAL.getKey(), summary.getTotal());
            context.addMeasure(MutationMetrics.MUTATION_KILLED.getKey(), summary.getKilled());
            context.addMeasure(MutationMetrics.MUTATION_SURVIVED.getKey(), summary.getSurvived());
            context.addMeasure(MutationMetrics.MUTATION_NO_COVERAGE.getKey(), summary.getNoCoverage());
            context.addMeasure(MutationMetrics.MUTATION_TIMEOUT.getKey(), summary.getTimeout());
            context.addMeasure(MutationMetrics.MUTATION_IGNORED.getKey(), summary.getIgnored());
            if (report.getTool() != null) {
                context.addMeasure(MutationMetrics.MUTATION_TOOL.getKey(), report.getTool());
            }
            if (report.getLanguage() != null) {
                context.addMeasure(MutationMetrics.MUTATION_LANGUAGE.getKey(), report.getLanguage());
            }

            LOG.info("Published mutation measures for {}: score={}, total={}",
                    projectKey, summary.getScore(), summary.getTotal());
        } catch (Exception e) {
            LOG.error("Failed to compute mutation measures for " + projectKey, e);
        }
    }
}
