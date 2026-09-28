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
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

@ServerSide
public class MutationReportStorageService {

  private static final Logger LOG = Loggers.get(MutationReportStorageService.class);
  private Path storageRoot;
  private final ObjectMapper mapper;
  private long maxReportSize;
  private int prRetentionDays;

  private static final String FILE_NAME = "current.json.gz";
  private static final String DEFAULT_ROOT = "/opt/sonarqube/mutation-reports";
  private static final long DEFAULT_MAX_REPORT_SIZE = 50L * 1024 * 1024; // 50MB

  public MutationReportStorageService() {
    this.storageRoot = Path.of(DEFAULT_ROOT);
    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
    this.maxReportSize = DEFAULT_MAX_REPORT_SIZE;
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
    if (config != null) {
      this.storageRoot = Path.of(config.get("chillcode.mutation.storage").orElse(DEFAULT_ROOT));
      this.maxReportSize =
          config.getLong("chillcode.mutation.maxReportSize").orElse(DEFAULT_MAX_REPORT_SIZE);
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
    Path tempFile = projectDir.resolve(FILE_NAME + ".tmp");
    Path targetFile = projectDir.resolve(FILE_NAME);

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
        storageRoot.resolve(sanitize(projectKey)).resolve(sanitize(branch)).resolve(FILE_NAME);

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
    Path reportFile = projectDir.resolve(FILE_NAME);
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
          .filter(path -> FILE_NAME.equals(path.getFileName().toString()))
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

  /**
   * The one path segment a project key or branch becomes: characters outside SonarQube's key
   * alphabet turn into {@code _}. A segment made only of dots is refused, because {@code .} and
   * {@code ..} pass that replacement unchanged and would resolve outside the storage root. The
   * refusal is an {@link IllegalArgumentException}, which the web service engine answers with 400.
   */
  static String sanitize(String input) {
    if (input == null) return "unknown";
    String segment = input.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (segment.isEmpty() || segment.chars().allMatch(c -> c == '.')) {
      throw new IllegalArgumentException("Invalid project key or branch: " + segment);
    }
    return segment;
  }
}
