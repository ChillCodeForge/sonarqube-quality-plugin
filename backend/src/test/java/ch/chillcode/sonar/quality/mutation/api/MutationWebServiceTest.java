package ch.chillcode.sonar.quality.mutation.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MutationWebServiceTest {

  @Test
  void readsAReportAtTheConfiguredLimit() throws IOException {
    byte[] report = "12345".getBytes();

    assertArrayEquals(
        report, MutationWebService.readLimited(new ByteArrayInputStream(report), report.length));
  }

  @Test
  void loadsTokenFromASwarmSecretFile() throws IOException {
    Path tokenFile = Files.createTempFile("mutation-upload-token", ".txt");
    try {
      Files.writeString(tokenFile, "token-value\n");

      assertEquals("token-value", MutationWebService.readUploadToken(tokenFile));
    } finally {
      Files.deleteIfExists(tokenFile);
    }
  }

  @Test
  void failsClosedWhenTheSwarmSecretFileIsMissing() {
    assertNull(MutationWebService.readUploadToken(Path.of("/missing/mutation-upload-token")));
  }

  @Test
  void rejectsAReportAboveTheConfiguredLimit() {
    byte[] report = "123456".getBytes();

    assertThrows(
        IOException.class,
        () -> MutationWebService.readLimited(new ByteArrayInputStream(report), report.length - 1));
  }
}
