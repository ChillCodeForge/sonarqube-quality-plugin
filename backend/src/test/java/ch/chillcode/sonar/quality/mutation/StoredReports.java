package ch.chillcode.sonar.quality.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Real reports as test fixtures, from the reports stored in SonarQube on 2026-11-14 (see
 * stored-reports/README.md): Stryker mutation.json files rebuilt from them (same mutants, files,
 * locations and statuses), and the stored PITest report of Walletchart in the normalized format.
 */
public final class StoredReports {

  private StoredReports() {}

  /** The uncompressed Stryker report of {@code project}. */
  public static byte[] stryker(String project) throws IOException {
    return read(project + ".mutation.json.gz");
  }

  /** The uncompressed normalized report of {@code project}, as the plugin stored it. */
  public static byte[] normalized(String project) throws IOException {
    return read(project + ".normalized.json.gz");
  }

  private static byte[] read(String fileName) throws IOException {
    String name = "/ch/chillcode/sonar/quality/mutation/stored-reports/" + fileName;
    try (InputStream in = StoredReports.class.getResourceAsStream(name);
        GZIPInputStream gz = new GZIPInputStream(in)) {
      return gz.readAllBytes();
    }
  }
}
