package ch.chillcode.sonar.quality.sensor;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.service.MutationService;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MutationSensor implements Sensor {

    private static final Logger LOG = Loggers.get(MutationSensor.class);

    private Configuration config;
    private MutationReportStorageService storageService;
    private MutationService mutationService;

    public MutationSensor() {
        // No-arg constructor for SonarQube DI
    }

    public void setConfiguration(Configuration config) {
        this.config = config;
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
        // Check if mutation report path is configured
        String reportPath = config.get("sonar.chillcode.mutationReport").orElse(null);
        if (reportPath == null || reportPath.isEmpty()) {
            LOG.info("No mutation report configured (sonar.chillcode.mutationReport), skipping mutation sensor");
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