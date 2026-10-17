package ch.chillcode.sonar.quality.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer.MeasureComputerContext;

class MutationMeasureComputerTest {

  private final MutationReportStorageService storage = mock(MutationReportStorageService.class);
  private final MutationMeasureComputer computer = new MutationMeasureComputer(storage);
  private final MeasureComputerContext context =
      mock(MeasureComputerContext.class, RETURNS_DEEP_STUBS);

  @BeforeEach
  void setUp() throws IOException {
    when(context.getComponent().getType()).thenReturn(Component.Type.PROJECT);
    when(context.getComponent().getKey()).thenReturn("my-project");
    when(context.getMeasure(anyString())).thenReturn(null); // deep stubs would invent one
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setSummary(new NormalizedMutationReport.MutationSummary());
    when(storage.loadReport("my-project", "main")).thenReturn(report);
  }

  private long addMeasureCalls() {
    return mockingDetails(context).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("addMeasure"))
        .count();
  }

  @Test
  void publishesTheStoredReport() {
    computer.compute(context);

    assertEquals(7, addMeasureCalls());
  }

  @Test
  void ignoresFilesAndDirectories() {
    MeasureComputerContext fileContext = mock(MeasureComputerContext.class, RETURNS_DEEP_STUBS);
    when(fileContext.getComponent().getType()).thenReturn(Component.Type.FILE);

    new MutationMeasureComputer().compute(fileContext);

    verify(fileContext, never()).getMeasure(anyString());
    assertEquals(
        0,
        mockingDetails(fileContext).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("addMeasure"))
            .count());
  }

  // SonarQube rejects a second measure on a metric, so a report the scanner
  // already published for this analysis must not be overwritten.
  @Test
  void leavesMeasuresFromTheScannerAlone() throws IOException {
    when(context.getMeasure(MutationMetrics.MUTATION_SCORE.getKey()))
        .thenReturn(mock(Measure.class));

    computer.compute(context);

    assertEquals(0, addMeasureCalls());
    verify(storage, never()).loadReport(anyString(), anyString());
  }
}
