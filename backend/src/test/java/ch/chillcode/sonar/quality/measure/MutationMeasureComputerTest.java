package ch.chillcode.sonar.quality.measure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
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
import org.sonar.api.ce.measure.MeasureComputer.MeasureComputerDefinition;
import org.sonar.api.ce.measure.MeasureComputer.MeasureComputerDefinitionContext;

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
    when(storage.loadReport("my-project", "feature-x")).thenReturn(report);
  }

  private void analysedBranch(String branch) {
    Measure measure = mock(Measure.class);
    when(measure.getStringValue()).thenReturn(branch);
    when(context.getMeasure(MutationMetrics.MUTATION_REPORT_BRANCH.getKey())).thenReturn(measure);
  }

  private long addMeasureCalls() {
    return mockingDetails(context).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("addMeasure"))
        .count();
  }

  @Test
  void publishesTheReportOfTheAnalysedBranch() {
    analysedBranch("feature-x");

    computer.compute(context);

    assertEquals(7, addMeasureCalls());
  }

  @Test
  void aBranchWithoutUploadGetsNoMeasures() throws IOException {
    analysedBranch("other");

    computer.compute(context);

    verify(storage).loadReport("my-project", "other");
    assertEquals(0, addMeasureCalls());
  }

  // The sensor records no branch for a pull request.
  @Test
  void anAnalysisWithoutReportBranchGetsNoMeasures() throws IOException {
    computer.compute(context);

    verify(storage, never()).loadReport(anyString(), anyString());
    assertEquals(0, addMeasureCalls());
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
    analysedBranch("feature-x");
    when(context.getMeasure(MutationMetrics.MUTATION_SCORE.getKey()))
        .thenReturn(mock(Measure.class));

    computer.compute(context);

    assertEquals(0, addMeasureCalls());
    verify(storage, never()).loadReport(anyString(), anyString());
  }

  @Test
  void declaresTheReportBranchAsInputAndEveryMutationMetricAsOutput() {
    MeasureComputerDefinitionContext definitionContext =
        mock(MeasureComputerDefinitionContext.class);
    MeasureComputerDefinition.Builder builder =
        mock(MeasureComputerDefinition.Builder.class, RETURNS_SELF);
    MeasureComputerDefinition definition = mock(MeasureComputerDefinition.class);
    when(definitionContext.newDefinitionBuilder()).thenReturn(builder);
    when(builder.build()).thenReturn(definition);

    assertSame(definition, computer.define(definitionContext));

    verify(builder).setInputMetrics("mutation_report_branch");
    verify(builder)
        .setOutputMetrics(
            "mutation_score",
            "mutation_total",
            "mutation_killed",
            "mutation_survived",
            "mutation_no_coverage",
            "mutation_timeout",
            "mutation_ignored",
            "mutation_tool",
            "mutation_language");
  }

  @Test
  void publishesToolAndLanguageWhenTheReportNamesThem() throws IOException {
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setSummary(new NormalizedMutationReport.MutationSummary());
    report.setTool("pitest");
    report.setLanguage("java");
    when(storage.loadReport("my-project", "feature-x")).thenReturn(report);
    analysedBranch("feature-x");

    computer.compute(context);

    assertEquals(9, addMeasureCalls());
    verify(context).addMeasure(MutationMetrics.MUTATION_TOOL.getKey(), "pitest");
    verify(context).addMeasure(MutationMetrics.MUTATION_LANGUAGE.getKey(), "java");
  }

  // Mutation measures are an add-on: an unreadable stored report must not fail
  // the Compute Engine task that processes the whole analysis.
  @Test
  void anUnreadableStoredReportPublishesNothingAndDoesNotFail() throws IOException {
    when(storage.loadReport("my-project", "broken")).thenThrow(new IOException("corrupt gzip"));
    analysedBranch("broken");

    assertDoesNotThrow(() -> computer.compute(context));

    assertEquals(0, addMeasureCalls());
  }
}
