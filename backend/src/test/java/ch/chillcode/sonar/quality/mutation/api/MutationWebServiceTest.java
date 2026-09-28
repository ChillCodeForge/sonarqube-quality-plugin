package ch.chillcode.sonar.quality.mutation.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonar.api.server.ws.LocalConnector;

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
  void letsACallerWhoMayBrowseTheProjectRead() throws IOException {
    RecordingResponse response = new RecordingResponse();

    assertTrue(
        MutationWebService.authorizeProjectRead(connectorAnswering(200), response, "project"));
    assertNull(response.status());
    assertEquals("", response.body());
  }

  @Test
  void answersNotFoundToACallerWhoMayNotBrowseTheProject() throws IOException {
    RecordingResponse response = new RecordingResponse();

    assertFalse(
        MutationWebService.authorizeProjectRead(
            connectorAnswering(403), response, "private-project"));
    assertEquals(404, response.status());
    assertEquals("text/plain", response.mediaType());
    assertEquals("Not Found", response.body());
  }

  @Test
  void asksSonarQubeAboutTheProjectItWasGiven() throws IOException {
    List<LocalConnector.LocalRequest> calls = new ArrayList<>();
    LocalConnector connector =
        call -> {
          calls.add(call);
          return answer(200);
        };

    MutationWebService.authorizeProjectRead(connector, new RecordingResponse(), "project");

    assertEquals(1, calls.size());
    assertEquals("api/components/show", calls.get(0).getPath());
    assertEquals("project", calls.get(0).getParam("component"));
  }

  /** A connector whose every local call answers {@code status}. */
  private static LocalConnector connectorAnswering(int status) {
    return call -> answer(status);
  }

  private static LocalConnector.LocalResponse answer(int status) {
    return new LocalConnector.LocalResponse() {
      @Override
      public int getStatus() {
        return status;
      }

      @Override
      public String getMediaType() {
        return "application/json";
      }

      @Override
      public byte[] getBytes() {
        return new byte[0];
      }

      @Override
      public Collection<String> getHeaderNames() {
        return List.of();
      }

      @Override
      public String getHeader(String name) {
        return null;
      }
    };
  }

  @ParameterizedTest
  @ValueSource(strings = {"download.json", "summary.json", "status.json", "badge.svg"})
  void shipsAResponseExampleForEveryReadAction(String name) {
    assertNotNull(MutationWebService.example(name));
  }

  @Test
  void rejectsAReportAboveTheConfiguredLimit() {
    byte[] report = "123456".getBytes();

    assertThrows(
        IOException.class,
        () -> MutationWebService.readLimited(new ByteArrayInputStream(report), report.length - 1));
  }
}
