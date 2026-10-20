package ch.chillcode.sonar.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ch.chillcode.sonar.quality.measure.MutationMeasureComputer;
import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.api.MutationWebService;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import ch.chillcode.sonar.quality.sensor.MutationSensor;
import ch.chillcode.sonar.quality.ui.MutationTestingPage;
import ch.chillcode.sonar.quality.ui.QualityDashboardPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;

class QualityPluginTest {

  @Test
  void registersEveryExpectedPluginExtension() {
    Plugin.Context context = new Plugin.Context(mock(SonarRuntime.class));

    new QualityPlugin().define(context);

    List<Class<?>> extensions =
        context.getExtensions().stream()
            .map(extension -> extension instanceof Class<?> type ? type : extension.getClass())
            .toList();
    assertEquals(
        List.of(
            MutationMetrics.class,
            MutationReportParser.class,
            MutationReportStorageService.class,
            MutationWebService.class,
            MutationSensor.class,
            MutationMeasureComputer.class,
            QualityDashboardPage.class,
            MutationTestingPage.class),
        extensions);
  }
}
