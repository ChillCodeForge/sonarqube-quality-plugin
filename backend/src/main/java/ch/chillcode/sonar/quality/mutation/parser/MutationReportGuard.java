package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a parsed report is a trustworthy result, before it replaces the stored one.
 *
 * <p>A report is rejected when its numbers would describe the test runner rather than the tests:
 *
 * <ul>
 *   <li>the mutants without a clean verdict, timeouts and RuntimeErrors together, exceed {@value
 *       #MAX_UNSETTLED_PERCENT} % of the scored mutants. The reports stored on 2026-11-14 stay at
 *       or below 4.1 %; the one mass-timeout nightly reached 88.5 %, because its runner overran
 *       every timeout under parallel load. Timeouts count as detected, so such a report hides real
 *       survivors behind a score; a RuntimeError means the test runner crashed instead of judging
 *       the mutant.
 *   <li>a mutant has a status outside {@link #STATUSES}, or none: Stryker's Pending, PIT's
 *       NOT_STARTED/STARTED and any status no parser knows become UNKNOWN, and an upload in the
 *       normalized format may carry any string. Either the run did not finish or the report is not
 *       normalized.
 * </ul>
 *
 * <p>CompileErrors (mutants the type checker or compiler refuses) are an expected outcome and stay
 * allowed. The scored mutants are the score's denominator (killed, timed out, survived, no
 * coverage). Every parser builds the summary from the mutants with {@link ReportAssembler}, so the
 * summary read here is never one an upload supplied.
 */
public final class MutationReportGuard {

  static final int MAX_UNSETTLED_PERCENT = 10;

  private static final String RUNTIME_ERROR = "RUNTIME_ERROR";

  /** The normalized statuses, as documented in the README. */
  static final Set<String> STATUSES =
      Set.of(
          "KILLED",
          "SURVIVED",
          "NO_COVERAGE",
          "TIMEOUT",
          "IGNORED",
          "COMPILE_ERROR",
          RUNTIME_ERROR,
          "ERROR");

  private MutationReportGuard() {}

  /** Rejects {@code report} with every {@link #problems} reason, unless there are none. */
  public static void check(NormalizedMutationReport report) throws MutationReportRejectedException {
    List<String> problems = problems(report);
    if (!problems.isEmpty()) {
      throw new MutationReportRejectedException(String.join("; ", problems));
    }
  }

  /** Every reason to reject {@code report}, in a sentence each; empty when it may be stored. */
  public static List<String> problems(NormalizedMutationReport report) {
    List<MutationMutant> mutants = report.getMutants();
    List<String> problems = new ArrayList<>();
    unsettledProblem(report.getSummary(), mutants).ifPresent(problems::add);
    statusProblem(mutants).ifPresent(problems::add);
    return problems;
  }

  private static Optional<String> unsettledProblem(
      MutationSummary summary, List<MutationMutant> mutants) {
    int scored =
        summary.getKilled()
            + summary.getTimeout()
            + summary.getSurvived()
            + summary.getNoCoverage();
    long crashed = mutants.stream().filter(m -> RUNTIME_ERROR.equals(m.getStatus())).count();
    long unsettled = summary.getTimeout() + crashed;
    // Integer comparison: exactly the limit is allowed. With nothing scored, any crash is too many.
    if (unsettled * 100L <= (long) MAX_UNSETTLED_PERCENT * scored) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            Locale.ROOT,
            "%d timed out and %d ended in RuntimeError, %.1f %% of the %d scored mutants (limit %d"
                + " %% together): a share this high means the test runner overran its timeouts or"
                + " crashed, not that the tests decided the mutants",
            summary.getTimeout(),
            crashed,
            scored == 0 ? 100.0 : unsettled * 100.0 / scored,
            scored,
            MAX_UNSETTLED_PERCENT));
  }

  private static Optional<String> statusProblem(List<MutationMutant> mutants) {
    List<MutationMutant> invalid =
        mutants.stream()
            .filter(m -> m.getStatus() == null || !STATUSES.contains(m.getStatus()))
            .toList();
    if (invalid.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Long> counts =
        invalid.stream()
            .collect(
                Collectors.groupingBy(
                    m -> m.getStatus() == null ? "missing" : m.getStatus(),
                    LinkedHashMap::new,
                    Collectors.counting()));
    return Optional.of(
        String.format(
            "%d %s a status outside the documented list (%s), first in %s: the run did not finish"
                + " or the report is not normalized",
            invalid.size(),
            invalid.size() == 1 ? "mutant has" : "mutants have",
            counts.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", ")),
            invalid.get(0).getFilePath()));
  }
}
