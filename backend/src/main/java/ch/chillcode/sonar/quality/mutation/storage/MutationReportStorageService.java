package ch.chillcode.sonar.quality.mutation.storage;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class MutationReportStorageService {

  private static final Logger LOG = Loggers.get(MutationReportStorageService.class);
  private Path storageRoot;
  private final ObjectMapper mapper;
  private long maxReportSize;
  private int prRetentionDays;
  private Configuration config;

  public MutationReportStorageService() {
    this.storageRoot = Path.of("/opt/sonarqube/mutation-reports");
    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
    this.maxReportSize = 50L * 1024 * 1024; // 50MB
    this.prRetentionDays = 14;
  }

  private void ensureStorageDirectory() {
    try {
      Files.createDirectories(storageRoot);
    } catch (AccessDeniedException e) {
      LOG.warn(
          "Cannot create storage directory (permission denied): {}. Directory must be created by container setup.",
          storageRoot);
    } catch (IOException e) {
      LOG.error("Failed to create storage directory: " + storageRoot, e);
    }
  }

  public MutationReportStorageService(Configuration config) {
    this();
    setConfiguration(config);
  }

  public void setConfiguration(Configuration config) {
    this.config = config;
    if (config != null) {
      String root =
          config.get("chillcode.mutation.storage").orElse("/opt/sonarqube/mutation-reports");
      this.storageRoot = Path.of(root);
      this.maxReportSize =
          config.getLong("chillcode.mutation.maxReportSize").orElse(50L * 1024 * 1024);
      this.prRetentionDays = config.getInt("chillcode.mutation.retention.prDays").orElse(14);
      ensureStorageDirectory();
    }
  }

  public long getMaxReportSize() {
    return maxReportSize;
  }

  public Path storeReport(NormalizedMutationReport report) throws IOException {
    ensureStorageDirectory();
    String projectKey = sanitize(report.getProject());
    String branch = sanitize(report.getBranch());
    Path projectDir = storageRoot.resolve(projectKey).resolve(branch);

    Files.createDirectories(projectDir);

    // Write to temp file first, then atomic rename
    Path tempFile = projectDir.resolve("current.json.gz.tmp");
    Path targetFile = projectDir.resolve("current.json.gz");

    try (GZIPOutputStream gos =
        new GZIPOutputStream(
            Files.newOutputStream(
                tempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING))) {
      mapper.writeValue(gos, report);
    }

    Files.move(
        tempFile,
        targetFile,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    LOG.info(
        "Stored mutation report: project={}, branch={}, size={} bytes",
        projectKey,
        branch,
        Files.size(targetFile));

    return targetFile;
  }

  public NormalizedMutationReport loadReport(String projectKey, String branch) throws IOException {
    ensureStorageDirectory();
    Path reportFile =
        storageRoot
            .resolve(sanitize(projectKey))
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
    ensureStorageDirectory();
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
