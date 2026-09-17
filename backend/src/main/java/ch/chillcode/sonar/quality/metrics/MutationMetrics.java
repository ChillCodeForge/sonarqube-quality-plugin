package ch.chillcode.sonar.quality.metrics;

import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;

import java.util.Arrays;
import java.util.List;

public class MutationMetrics implements Metrics {

    public static final String DOMAIN = "Mutation Testing";

    // Core mutation metrics
    public static final Metric MUTATION_SCORE = new Metric.Builder("mutation_score", "Mutation Score", Metric.ValueType.PERCENT)
            .setDescription("Percentage of killed mutants out of total relevant mutants")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_TOTAL = new Metric.Builder("mutation_total", "Total Mutants", Metric.ValueType.INT)
            .setDescription("Total number of mutants generated")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_KILLED = new Metric.Builder("mutation_killed", "Killed Mutants", Metric.ValueType.INT)
            .setDescription("Number of mutants killed by tests")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_SURVIVED = new Metric.Builder("mutation_survived", "Survived Mutants", Metric.ValueType.INT)
            .setDescription("Number of mutants that survived (not killed by any test)")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_NO_COVERAGE = new Metric.Builder("mutation_no_coverage", "No Coverage Mutants", Metric.ValueType.INT)
            .setDescription("Number of mutants in code not covered by any test")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_TIMEOUT = new Metric.Builder("mutation_timeout", "Timeout Mutants", Metric.ValueType.INT)
            .setDescription("Number of mutants that caused test timeouts")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_IGNORED = new Metric.Builder("mutation_ignored", "Ignored Mutants", Metric.ValueType.INT)
            .setDescription("Number of ignored mutants (e.g., static mutants)")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_DURATION = new Metric.Builder("mutation_duration", "Mutation Testing Duration", Metric.ValueType.INT)
            .setDescription("Total duration of mutation testing run in milliseconds")
            .setDomain(DOMAIN)
            .create();

    // Tool-specific breakdown metrics
    public static final Metric MUTATION_TOOL = new Metric.Builder("mutation_tool", "Mutation Tool", Metric.ValueType.STRING)
            .setDescription("Mutation testing tool used (pitest, stryker, mutmut)")
            .setDomain(DOMAIN)
            .create();

    public static final Metric MUTATION_LANGUAGE = new Metric.Builder("mutation_language", "Mutation Language", Metric.ValueType.STRING)
            .setDescription("Programming language of mutated code")
            .setDomain(DOMAIN)
            .create();

    @Override
    public List<Metric> getMetrics() {
        return Arrays.asList(
                MUTATION_SCORE,
                MUTATION_TOTAL,
                MUTATION_KILLED,
                MUTATION_SURVIVED,
                MUTATION_NO_COVERAGE,
                MUTATION_TIMEOUT,
                MUTATION_IGNORED,
                MUTATION_DURATION,
                MUTATION_TOOL,
                MUTATION_LANGUAGE
        );
    }
}