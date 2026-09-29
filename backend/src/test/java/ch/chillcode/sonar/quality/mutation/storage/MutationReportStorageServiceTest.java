package ch.chillcode.sonar.quality.mutation.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
}
