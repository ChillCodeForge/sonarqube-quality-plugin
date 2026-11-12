package ch.chillcode.sonar.quality.mutation.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.sonar.api.config.Configuration;

class MutationReportStorageServiceTest {

  @Test
  void keepsAKeyInSonarQubesAlphabet() {
    assertEquals("Haembina_dosiary", MutationReportStorageService.sanitize("Haembina_dosiary"));
  }

  @Test
  void replacesASeparatorSoAKeyStaysOneSegment() {
    assertEquals("feature_x", MutationReportStorageService.sanitize("feature/x"));
  }

  @Test
  void namesAMissingKeyUnknown() {
    assertEquals("unknown", MutationReportStorageService.sanitize(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ".", "..", "..."})
  void refusesASegmentThatWouldLeaveTheStorageRoot(String segment) {
    assertThrows(
        IllegalArgumentException.class, () -> MutationReportStorageService.sanitize(segment));
  }

  @Test
  void storesLoadsAndDeletesReport(@TempDir Path tempDir) throws IOException {
    Configuration config = mock(Configuration.class);
    when(config.get("chillcode.mutation.storage")).thenReturn(Optional.of(tempDir.toString()));
    when(config.getLong("chillcode.mutation.maxReportSize")).thenReturn(Optional.of(1024L * 1024L));
    when(config.getInt("chillcode.mutation.retention.prDays")).thenReturn(Optional.of(7));

    MutationReportStorageService storage = new MutationReportStorageService(config);
    assertEquals(1024L * 1024L, storage.getMaxReportSize());

    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setProject("my-project");
    report.setBranch("feature/test");
    NormalizedMutationReport.MutationSummary summary =
        new NormalizedMutationReport.MutationSummary();
    summary.setTotal(10);
    summary.setKilled(8);
    summary.setScore(80.0);
    report.setSummary(summary);

    Path stored = storage.storeReport(report);
    assertNotNull(stored);
    assertTrue(Files.exists(stored));

    NormalizedMutationReport loaded = storage.loadReport("my-project", "feature/test");
    assertNotNull(loaded);
    assertEquals("my-project", loaded.getProject());
    assertEquals(10, loaded.getSummary().getTotal());
    assertEquals(8, loaded.getSummary().getKilled());

    storage.deleteReport("my-project", "feature/test");
    assertNull(storage.loadReport("my-project", "feature/test"));
  }

  @Test
  void loadReturnsNullWhenReportDoesNotExist(@TempDir Path tempDir) throws IOException {
    Configuration config = mock(Configuration.class);
    when(config.get("chillcode.mutation.storage")).thenReturn(Optional.of(tempDir.toString()));
    MutationReportStorageService storage = new MutationReportStorageService(config);

    assertNull(storage.loadReport("nonexistent", "main"));
  }

  @Test
  void readingAMissingRootCreatesNothing(@TempDir Path tempDir) throws IOException {
    Path root = tempDir.resolve("absent");
    Configuration config = mock(Configuration.class);
    when(config.get("chillcode.mutation.storage")).thenReturn(Optional.of(root.toString()));
    MutationReportStorageService storage = new MutationReportStorageService(config);
    Files.delete(root);

    assertNull(storage.loadReport("my-project", "main"));
    storage.deleteReport("my-project", "main");
    storage.cleanupOldPRReports();

    assertFalse(Files.exists(root));
  }

  @Test
  void cleansUpOldPRReports(@TempDir Path tempDir) throws IOException {
    Configuration config = mock(Configuration.class);
    when(config.get("chillcode.mutation.storage")).thenReturn(Optional.of(tempDir.toString()));
    when(config.getInt("chillcode.mutation.retention.prDays")).thenReturn(Optional.of(1));
    MutationReportStorageService storage = new MutationReportStorageService(config);

    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setProject("my-project");
    report.setBranch("pull-42");
    report.setSummary(new NormalizedMutationReport.MutationSummary());

    Path stored = storage.storeReport(report);
    assertTrue(Files.exists(stored));

    Files.setLastModifiedTime(
        stored,
        java.nio.file.attribute.FileTime.fromMillis(
            System.currentTimeMillis() - 2L * 86400 * 1000));

    storage.cleanupOldPRReports();

    assertNull(storage.loadReport("my-project", "pull-42"));
  }

  private static Configuration storageAt(Path root, int prRetentionDays) {
    Configuration config = mock(Configuration.class);
    when(config.get("chillcode.mutation.storage")).thenReturn(Optional.of(root.toString()));
    when(config.getInt("chillcode.mutation.retention.prDays"))
        .thenReturn(Optional.of(prRetentionDays));
    return config;
  }

  private static Path store(MutationReportStorageService storage, String project, String branch)
      throws IOException {
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setProject(project);
    report.setBranch(branch);
    report.setSummary(new NormalizedMutationReport.MutationSummary());
    return storage.storeReport(report);
  }

  private static void ageByDays(Path file, int days) throws IOException {
    Files.setLastModifiedTime(
        file, FileTime.fromMillis(System.currentTimeMillis() - days * 86_400_000L));
  }

  @Test
  void aMissingConfigurationKeepsTheDefaults() {
    MutationReportStorageService storage = new MutationReportStorageService();

    storage.setConfiguration(null);

    assertEquals(50L * 1024 * 1024, storage.getMaxReportSize());
  }

  @Test
  void deletingOneBranchKeepsTheProjectsOtherBranches(@TempDir Path tempDir) throws IOException {
    MutationReportStorageService storage = new MutationReportStorageService(storageAt(tempDir, 14));
    store(storage, "my-project", "main");
    store(storage, "my-project", "feature");

    storage.deleteReport("my-project", "feature");

    assertNull(storage.loadReport("my-project", "feature"));
    assertNotNull(storage.loadReport("my-project", "main"));
    assertTrue(Files.isDirectory(tempDir.resolve("my-project")));
  }

  @Test
  void cleanupKeepsBranchReportsAndRecentPullRequestReports(@TempDir Path tempDir)
      throws IOException {
    MutationReportStorageService storage = new MutationReportStorageService(storageAt(tempDir, 1));
    ageByDays(store(storage, "my-project", "main"), 30);
    store(storage, "my-project", "PR-7");

    storage.cleanupOldPRReports();

    assertNotNull(storage.loadReport("my-project", "main"));
    assertNotNull(storage.loadReport("my-project", "PR-7"));
  }

  @Test
  void aStorageRootThatCannotBeCreatedFailsOnlyTheWrite(@TempDir Path tempDir) throws IOException {
    Path blocker = Files.writeString(tempDir.resolve("file"), "not a directory");
    Configuration config = storageAt(blocker.resolve("reports"), 14);

    MutationReportStorageService storage = new MutationReportStorageService(config);

    assertThrows(IOException.class, () -> store(storage, "my-project", "main"));
  }

  /**
   * Files with one static method failing for one path and every other call real. An Answer, not
   * {@code when(...)}: stubbing a static call through {@code when} would run the real method once.
   */
  private static MockedStatic<Files> filesFailing(String method, Path target, IOException error) {
    return mockStatic(
        Files.class,
        invocation -> {
          if (invocation.getMethod().getName().equals(method)
              && target.equals(invocation.getArgument(0))) {
            throw error;
          }
          return invocation.callRealMethod();
        });
  }

  // The container's entrypoint, not the plugin, owns the storage volume: a
  // permission refusal is logged and the plugin still starts.
  @Test
  void aPermissionRefusalForTheStorageRootDoesNotStopThePlugin(@TempDir Path tempDir) {
    Path root = tempDir.resolve("reports");
    Configuration config = storageAt(root, 14);
    when(config.getLong("chillcode.mutation.maxReportSize")).thenReturn(Optional.of(2048L));

    try (MockedStatic<Files> ignored =
        filesFailing("createDirectories", root, new AccessDeniedException(root.toString()))) {
      MutationReportStorageService storage = new MutationReportStorageService(config);

      assertEquals(2048L, storage.getMaxReportSize());
    }
    assertFalse(Files.exists(root));
  }

  @Test
  void aStorageTreeThatCannotBeWalkedLeavesEveryReport(@TempDir Path tempDir) throws IOException {
    MutationReportStorageService storage = new MutationReportStorageService(storageAt(tempDir, 1));
    ageByDays(store(storage, "my-project", "pull-1"), 30);

    try (MockedStatic<Files> ignored =
        filesFailing("walk", tempDir, new IOException("unreadable"))) {
      assertDoesNotThrow(storage::cleanupOldPRReports);
    }
    assertNotNull(storage.loadReport("my-project", "pull-1"));
  }

  @Test
  void aReportWhoseAgeCannotBeReadIsKept(@TempDir Path tempDir) throws IOException {
    MutationReportStorageService storage = new MutationReportStorageService(storageAt(tempDir, 1));
    Path report = store(storage, "my-project", "pull-2");
    ageByDays(report, 30);

    try (MockedStatic<Files> ignored =
        filesFailing("getLastModifiedTime", report, new IOException("vanished"))) {
      assertDoesNotThrow(storage::cleanupOldPRReports);
    }
    assertNotNull(storage.loadReport("my-project", "pull-2"));
  }
}
