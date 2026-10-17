package ch.chillcode.sonar.quality.sensor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
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

// The sensor runs in the scanner, which has no access to the server's report
// storage: it must neither create that directory nor depend on writing to it.
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
  private Path storageRoot;
  private Configuration config;
  private SensorContext context;
  private NewMeasure<?> measure;
  private MutationSensor sensor;

  @BeforeEach
  void setUp() throws IOException {
    storageRoot = tempDir.resolve("server-storage");
    Configuration storageConfig = mock(Configuration.class);
    when(storageConfig.get("chillcode.mutation.storage"))
        .thenReturn(Optional.of(storageRoot.toString()));
    MutationReportStorageService storage = new MutationReportStorageService(storageConfig);
    Files.delete(storageRoot); // the scanner host has no such directory

    config = mock(Configuration.class);
    when(config.get("sonar.branch.name")).thenReturn(Optional.empty());
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.empty());

    context = mock(SensorContext.class, RETURNS_DEEP_STUBS);
    when(context.project().key()).thenReturn("my-project");
    measure = mock(NewMeasure.class, RETURNS_SELF);
    when(context.newMeasure()).thenAnswer(invocation -> measure);

    sensor = new MutationSensor(config);
    sensor.setMutationReportStorageService(storage);
  }

  @Test
  void withoutAnyReportCreatesNoStorageDirectory() {
    sensor.execute(context);

    verify(context, never()).newMeasure();
    assertFalse(Files.exists(storageRoot));
  }

  @Test
  void localReportPublishesMeasuresWithoutWritingStorage() throws IOException {
    Path report = Files.writeString(tempDir.resolve("mutations.xml"), PITEST_XML);
    when(config.get("sonar.chillcode.mutationReport")).thenReturn(Optional.of(report.toString()));

    sensor.execute(context);

    verify(measure, times(9)).save();
    assertFalse(Files.exists(storageRoot));
  }
}
