package ch.chillcode.sonar.quality;

import ch.chillcode.sonar.quality.measure.MutationMeasureComputer;
import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.api.MutationWebService;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import ch.chillcode.sonar.quality.sensor.MutationSensor;
import ch.chillcode.sonar.quality.ui.MutationTestingPage;
import ch.chillcode.sonar.quality.ui.QualityDashboardPage;
import org.sonar.api.Plugin;
import java.util.List;  // DELIBERATE LINT ERROR: unused import

public class QualityPlugin implements Plugin {

  @Override
  public void define(Context context) {
    // Custom Metrics
    context.addExtension(new MutationMetrics());

    // Services
    context.addExtension(MutationReportParser.class);
    context.addExtension(MutationReportStorageService.class);

    // WebService
    context.addExtension(MutationWebService.class);

    // Sensor - will be instantiated with Configuration by SonarQube DI
    context.addExtension(MutationSensor.class);

    // Measure Computer - runs server-side in the Compute Engine (has
    // access to our storage volume), unlike Sensor which runs inside
    // the ephemeral scanner container and never sees it.
    context.addExtension(new MutationMeasureComputer());

    // UI Pages
    context.addExtension(new QualityDashboardPage());
    context.addExtension(new MutationTestingPage());
  }
}
