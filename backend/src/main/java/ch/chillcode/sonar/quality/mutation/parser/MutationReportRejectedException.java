package ch.chillcode.sonar.quality.mutation.parser;

import java.io.IOException;

/**
 * A report that parses but must not be stored, for example a normalized upload without mutants. The
 * upload answers it with HTTP 422 and this message, like a report the guard rejects.
 */
public class MutationReportRejectedException extends IOException {

  public MutationReportRejectedException(String reason) {
    super(reason);
  }
}
