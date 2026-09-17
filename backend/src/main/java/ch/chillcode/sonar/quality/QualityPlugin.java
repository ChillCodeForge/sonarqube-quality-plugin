package ch.chillcode.sonar.quality;

import ch.chillcode.sonar.quality.metrics.MutationMetrics;
import ch.chillcode.sonar.quality.mutation.api.MutationWebService;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.sensor.MutationSensor;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import ch.chillcode.sonar.quality.ui.MutationTestingPage;
import ch.chillcode.sonar.quality.ui.QualityDashboardPage;
import org.sonar.api.Plugin;

public class QualityPlugin implements Plugin {

    @Override
    public void define(Context context) {
        // Custom Metrics
        context.addExtension(new MutationMetrics());

        // Services
        context.addExtension(new MutationReportParser());
        context.addExtension(new MutationReportStorageService());

        // WebService
        context.addExtension(new MutationWebService());

        // Sensor - will be instantiated with Configuration by SonarQube DI
        context.addExtension(MutationSensor.class);

        // UI Pages
        context.addExtension(new QualityDashboardPage());
        context.addExtension(new MutationTestingPage());
    }
}