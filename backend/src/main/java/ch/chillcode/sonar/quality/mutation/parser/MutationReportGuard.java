package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationMutant;
import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport.MutationSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides whether an uploaded report is a trustworthy result, before it replaces the stored one.
 *
 * <p>A report is rejected when its numbers would describe the test runner rather than the tests:
 *
 * <ul>
 *   <li>more than {@value #MAX_TIMEOUT_PERCENT} % of the scored mutants timed out. The reports
 *       stored on 2026-11-14 stay below 4 %; the one mass-timeout nightly reached 88.5 %, because
 *       its runner overran every timeout under parallel load. Timeouts count as detected, so such a
 *       report hides real survivors behind a score.
 *   <li>a mutant ended in RuntimeError: the test runner crashed instead of judging it.
 *   <li>a mutant has no final status (Stryker's Pending, PIT's NOT_STARTED/STARTED or a status no
 *       parser knows): the run did not finish.
 * </ul>
 *
 * <p>CompileErrors (mutants the type checker or compiler refuses) are an expected outcome and stay
 * allowed. The scored mutants are the score's denominator (killed, timed out, survived, no
 * coverage), recounted from the mutants so an uploaded summary cannot vouch for itself.
 */
public final class MutationReportGuard {

  static final int MAX_TIMEOUT_PERCENT = 10;

  private static final String RUNTIME_ERROR = "RUNTIME_ERROR";
  private static final String UNKNOWN = "UNKNOWN";

  private MutationReportGuard() {}

  /** Every reason to reject {@code report}, in a sentence each; empty when it may be stored. */
  public static List<String> problems(NormalizedMutationReport report) {
    List<MutationMutant> mutants = report.getMutants() == null ? List.of() : report.getMutants();
    List<String> problems = new ArrayList<>();
    timeoutProblem(ReportAssembler.summaryOf(mutants)).ifPresent(problems::add);
    List<MutationMutant> crashed = withStatus(mutants, RUNTIME_ERROR);
    if (!crashed.isEmpty()) {
      boolean one = crashed.size() == 1;
      problems.add(
          String.format(
              "%d %s in RuntimeError (the test runner crashed instead of judging %s), first in %s",
              crashed.size(),
              one ? "mutant ended" : "mutants ended",
              one ? "it" : "them",
              crashed.get(0).getFilePath()));
    }
    List<MutationMutant> unfinished = withStatus(mutants, UNKNOWN);
    if (!unfinished.isEmpty()) {
      problems.add(
          String.format(
              "%d %s no final status (pending or unknown), first in %s: the report is incomplete",
              unfinished.size(),
              unfinished.size() == 1 ? "mutant has" : "mutants have",
              unfinished.get(0).getFilePath()));
    }
    return problems;
  }

  private static Optional<String> timeoutProblem(MutationSummary summary) {
    int scored =
        summary.getKilled()
            + summary.getTimeout()
            + summary.getSurvived()
            + summary.getNoCoverage();
    // Integer comparison: exactly the limit is allowed.
    if (scored == 0 || summary.getTimeout() * 100L <= (long) MAX_TIMEOUT_PERCENT * scored) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            Locale.ROOT,
            "%d of %d scored mutants timed out (%.1f %%, limit %d %%): a share this high means the"
                + " test runner overran its timeouts, not that the mutants loop",
            summary.getTimeout(),
            scored,
            summary.getTimeout() * 100.0 / scored,
            MAX_TIMEOUT_PERCENT));
  }

  private static List<MutationMutant> withStatus(List<MutationMutant> mutants, String status) {
    return mutants.stream().filter(m -> status.equals(m.getStatus())).toList();
  }
}
