package ch.chillcode.sonar.quality.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.measures.Metric;

class MutationMetricsTest {

  @Test
  void registersEveryMutationMetric() {
    List<String> keys = new MutationMetrics().getMetrics().stream().map(Metric::getKey).toList();

    assertEquals(
        List.of(
            "mutation_score",
            "mutation_total",
            "mutation_killed",
            "mutation_survived",
            "mutation_no_coverage",
            "mutation_timeout",
            "mutation_ignored",
            "mutation_duration",
            "mutation_tool",
            "mutation_language",
            "mutation_report_branch"),
        keys);
  }

  // The branch hand-off between sensor and measure computer is plumbing, not
  // something to show on a project page.
  @Test
  void hidesTheReportBranchHandOff() {
    assertTrue(MutationMetrics.MUTATION_REPORT_BRANCH.isHidden());
  }
}
