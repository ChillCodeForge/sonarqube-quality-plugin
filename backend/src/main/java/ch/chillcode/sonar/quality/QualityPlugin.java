package ch.chillcode.sonar.quality;

import org.sonar.api.Plugin;

public class QualityPlugin implements Plugin {

    @Override
    public void define(Context context) {
        // Custom Metrics
        context.addExtension(ch.chillcode.sonar.quality.metrics.MutationMetrics.class);

        // Sensor for Mutation Testing Reports
        context.addExtension(ch.chillcode.sonar.quality.sensor.MutationSensor.class);

        // Web Services for Report Storage API
        context.addExtension(ch.chillcode.sonar.quality.mutation.api.MutationWebService.class);
    }
}