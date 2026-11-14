package ch.chillcode.sonar.quality.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Real reports as test fixtures: each is a Stryker mutation.json rebuilt from the report stored in
 * SonarQube on 2026-11-14 (same mutants, files, locations and statuses; see
 * stored-reports/README.md).
 */
public final class StoredReports {

  private StoredReports() {}

  /** The uncompressed Stryker report of {@code project}. */
  public static byte[] stryker(String project) throws IOException {
    String name =
        "/ch/chillcode/sonar/quality/mutation/stored-reports/" + project + ".mutation.json.gz";
    try (InputStream in = StoredReports.class.getResourceAsStream(name);
        GZIPInputStream gz = new GZIPInputStream(in)) {
      return gz.readAllBytes();
    }
  }
}
