package ch.chillcode.sonar.quality.mutation.storage;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MutationReportStorageService {

    private static final Logger LOG = Loggers.get(MutationReportStorageService.class);
    private final Path storageRoot;
    private final ObjectMapper mapper;
    private final long maxReportSize;
    private final int prRetentionDays;

    public MutationReportStorageService(Configuration config) {
        String root = config.get("chillcode.mutation.storage").orElse("/opt/sonarqube/mutation-reports");
        this.storageRoot = Path.of(root);
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.maxReportSize = config.getLong("chillcode.mutation.maxReportSize").orElse(50L * 1024 * 1024); // 50MB
        this.prRetentionDays = config.getInt("chillcode.mutation.retention.prDays").orElse(14);

        // Ensure storage directory exists
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            LOG.error("Failed to create storage directory: " + storageRoot, e);
        }
    }

    public Path storeReport(NormalizedMutationReport report) throws IOException {
        String projectKey = sanitize(report.getProject());
        String branch = sanitize(report.getBranch());
        Path projectDir = storageRoot.resolve(projectKey).resolve(branch);

        Files.createDirectories(projectDir);

        // Write to temp file first, then atomic rename
        Path tempFile = projectDir.resolve("current.json.gz.tmp");
        Path targetFile = projectDir.resolve("current.json.gz");

        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(tempFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))) {
            mapper.writeValue(gos, report);
        }

        Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        LOG.info("Stored mutation report: project={}, branch={}, size={} bytes",
                projectKey, branch, Files.size(targetFile));

        return targetFile;
    }

    public NormalizedMutationReport loadReport(String projectKey, String branch) throws IOException {
        Path reportFile = storageRoot.resolve(sanitize(projectKey))
                .resolve(sanitize(branch))
                .resolve("current.json.gz");

        if (!Files.exists(reportFile)) {
            return null;
        }

        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(reportFile))) {
            return mapper.readValue(gis, NormalizedMutationReport.class);
        }
    }

    public void deleteReport(String projectKey, String branch) throws IOException {
        Path projectDir = storageRoot.resolve(sanitize(projectKey)).resolve(sanitize(branch));
        Path reportFile = projectDir.resolve("current.json.gz");
        Files.deleteIfExists(reportFile);

        // Clean up empty directories
        try {
            Files.deleteIfExists(projectDir);
            Files.deleteIfExists(projectDir.getParent());
        } catch (IOException ignored) {
            // Directory not empty, that's fine
        }
    }

    public void cleanupOldPRReports() {
        try {
            Files.walk(storageRoot)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("current.json.gz"))
                    .forEach(this::checkAndDeleteOldPRReport);
        } catch (IOException e) {
            LOG.error("Failed to cleanup old PR reports", e);
        }
    }

    private void checkAndDeleteOldPRReport(Path reportFile) {
        try {
            Path projectDir = reportFile.getParent();
            String branch = projectDir.getFileName().toString();

            // Only clean up PR branches (format: pull-XXX or PR-XXX)
            if (!branch.matches("(?i)pull-\\d+|pr-\\d+")) {
                return;
            }

            // Check last modified time
            long lastModified = Files.getLastModifiedTime(reportFile).toMillis();
            long cutoff = System.currentTimeMillis() - (prRetentionDays * 24L * 60 * 60 * 1000);

            if (lastModified < cutoff) {
                deleteReport(projectDir.getParent().getFileName().toString(), branch);
                LOG.info("Deleted old PR mutation report: {}", reportFile);
            }
        } catch (IOException e) {
            LOG.error("Failed to check/delete old PR report: " + reportFile, e);
        }
    }

    private String sanitize(String input) {
        if (input == null) return "unknown";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}