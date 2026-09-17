package ch.chillcode.sonar.quality.config;

import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Configuration;

import java.util.Arrays;
import java.util.List;

public class QualityConfiguration {

    public static final String CATEGORY = "ChillCode Quality";

    public static List<PropertyDefinition> definitions() {
        return Arrays.asList(
                // Storage configuration
                PropertyDefinition.builder("chillcode.mutation.storage")
                        .name("Mutation Reports Storage Path")
                        .description("Filesystem path where mutation reports are stored")
                        .type(PropertyDefinition.Type.STRING)
                        .defaultValue("/opt/sonarqube/mutation-reports")
                        .category(CATEGORY)
                        .build(),

                PropertyDefinition.builder("chillcode.mutation.maxReportSize")
                        .name("Max Report Size")
                        .description("Maximum size of a mutation report in bytes")
                        .type(PropertyDefinition.Type.LONG)
                        .defaultValue("52428800")
                        .category(CATEGORY)
                        .build(),

                PropertyDefinition.builder("chillcode.mutation.retention.prDays")
                        .name("PR Report Retention Days")
                        .description("Days to keep PR mutation reports before cleanup")
                        .type(PropertyDefinition.Type.INTEGER)
                        .defaultValue("14")
                        .category(CATEGORY)
                        .build(),

                // API Authentication
                PropertyDefinition.builder("chillcode.mutation.apiToken")
                        .name("Mutation API Token")
                        .description("Token for authenticating mutation report uploads (optional, can also use SonarQube user token)")
                        .type(PropertyDefinition.Type.STRING)
                        .category(CATEGORY)
                        .build(),

                // Sensor configuration
                PropertyDefinition.builder("sonar.chillcode.mutationReport")
                        .name("Mutation Report Path")
                        .description("Path to the normalized mutation report JSON file (set by CI pipeline)")
                        .type(PropertyDefinition.Type.STRING)
                        .category(CATEGORY)
                        .build()
        );
    }
}