package ch.chillcode.sonar.quality.sensor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.config.Configuration;

// The sensor runs in the scanner and publishes only a local report file;
// uploaded reports are the MutationMeasureComputer's.
class MutationSensorTest {

  private static final String PITEST_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <mutations partial="false">
          <mutation detected="true" status="KILLED" numberOfTestsRun="1">
              <sourceFile>A.java</sourceFile>
              <mutatedClass>a.A</mutatedClass>
              <mutatedMethod>m</mutatedMethod>
              <methodDescription>()V</methodDescription>
              <lineNumber>1</lineNumber>
              <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
              <killingTest>a.ATest.t</killingTest>
              <description>replaced operator</description>
          </mutation>
      </mutations>
      """;

  @TempDir Path tempDir;
  private Configuration config;
  private SensorContext context;

  @SuppressWarnings("rawtypes")
  private NewMeasure measure;

  private MutationSensor sensor;

  @BeforeEach
  void setUp() {
    config = mock(Configuration.class);
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.empty());
    when(config.get("sonar.branch.name")).thenReturn(Optional.empty());
    when(config.get("sonar.pullrequest.key")).thenReturn(Optional.empty());

    context = mock(SensorContext.class, RETURNS_DEEP_STUBS);
    measure = mock(NewMeasure.class, RETURNS_SELF);
    when(context.newMeasure()).thenAnswer(invocation -> measure);

    sensor = new MutationSensor(config);
  }

  @Test
  void withoutLocalReportRecordsOnlyTheReportBranch() {
    sensor.execute(context);

    verify(measure).on(context.project());
    verify(measure).forMetric(MutationMetrics.MUTATION_REPORT_BRANCH);
    verify(measure).withValue("main");
    verify(measure, times(1)).save();
  }

  @Test
  void recordsTheAnalysedBranch() {
    when(config.get("sonar.branch.name")).thenReturn(Optional.of("feature-x"));

    sensor.execute(context);

    verify(measure).on(context.project());
    verify(measure).withValue("feature-x");
  }

  @Test
  void pullRequestRecordsNoReportBranch() {
    when(config.get("sonar.pullrequest.key")).thenReturn(Optional.of("42"));

    sensor.execute(context);

    verify(context, never()).newMeasure();
  }

  @Test
  void localReportPublishesAllMeasures() throws IOException {
    Path report = Files.writeString(tempDir.resolve("mutations.xml"), PITEST_XML);
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.of(report.toString()));

    sensor.execute(context);

    verify(measure, times(10)).save(); // 9 mutation measures + report branch
  }

  @Test
  void aFailingMeasureNeverAbortsTheAnalysis() {
    doThrow(new NullPointerException("component is null")).when(measure).save();

    assertDoesNotThrow(() -> sensor.execute(context));
  }

  @Test
  void anUnreadableLocalReportNeverAbortsTheAnalysis() throws IOException {
    Path report = Files.writeString(tempDir.resolve("mutations.xml"), "not a report");
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.of(report.toString()));

    assertDoesNotThrow(() -> sensor.execute(context));
    verify(measure, times(1)).save(); // only the report branch, no mutation measures
  }
}
