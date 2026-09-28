package ch.chillcode.sonar.quality.mutation.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MutationReportStorageServiceTest {

  @Test
  void keepsAKeyInSonarQubesAlphabet() {
    assertEquals("Haembina_dosiary", MutationReportStorageService.sanitize("Haembina_dosiary"));
  }

  @Test
  void replacesASeparatorSoAKeyStaysOneSegment() {
    assertEquals("feature_x", MutationReportStorageService.sanitize("feature/x"));
  }

  @Test
  void namesAMissingKeyUnknown() {
    assertEquals("unknown", MutationReportStorageService.sanitize(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ".", "..", "..."})
  void refusesASegmentThatWouldLeaveTheStorageRoot(String segment) {
    assertThrows(
        IllegalArgumentException.class, () -> MutationReportStorageService.sanitize(segment));
  }
}
