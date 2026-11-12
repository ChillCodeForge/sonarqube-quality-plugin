package ch.chillcode.sonar.quality.mutation.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NormalizedMutationReportTest {

  private static NormalizedMutationReport reportWith(boolean staticMutant) {
    NormalizedMutationReport.MutationMutant mutant = new NormalizedMutationReport.MutationMutant();
    mutant.setId("1");
    mutant.setStatus("SURVIVED");
    mutant.setStaticMutant(staticMutant);
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setTool("stryker");
    report.setSummary(new NormalizedMutationReport.MutationSummary());
    report.setMutants(List.of(mutant));
    return report;
  }

  // The frontend reads a mutant's static flag under the key "static".
  @Test
  void serializesTheStaticFlagUnderItsSchemaName() throws IOException {
    String json = reportWith(true).toJson();

    assertTrue(json.contains("\"static\":true"), json);
  }

  @Test
  void keepsTheStaticFlagThroughAStoreAndReloadCycle() throws IOException {
    // The same mapper setup MutationReportStorageService reads stored reports with.
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    NormalizedMutationReport reloaded =
        mapper.readValue(reportWith(true).toJson(), NormalizedMutationReport.class);

    assertTrue(reloaded.getMutants().get(0).isStaticMutant());
  }
}
