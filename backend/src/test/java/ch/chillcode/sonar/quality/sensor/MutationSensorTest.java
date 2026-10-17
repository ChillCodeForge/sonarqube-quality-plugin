package ch.chillcode.sonar.quality.sensor;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  private NewMeasure<?> measure;
  private MutationSensor sensor;

  @BeforeEach
  void setUp() {
    config = mock(Configuration.class);
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.empty());

    context = mock(SensorContext.class, RETURNS_DEEP_STUBS);
    measure = mock(NewMeasure.class, RETURNS_SELF);
    when(context.newMeasure()).thenAnswer(invocation -> measure);

    sensor = new MutationSensor(config);
  }

  @Test
  void withoutLocalReportPublishesNothing() {
    sensor.execute(context);

    verify(context, never()).newMeasure();
  }

  @Test
  void localReportPublishesAllMeasures() throws IOException {
    Path report = Files.writeString(tempDir.resolve("mutations.xml"), PITEST_XML);
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.of(report.toString()));

    sensor.execute(context);

    verify(measure, times(9)).save();
  }
}
