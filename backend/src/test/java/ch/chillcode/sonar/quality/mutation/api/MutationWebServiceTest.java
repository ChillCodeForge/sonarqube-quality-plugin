package ch.chillcode.sonar.quality.mutation.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
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
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;

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
  void definesControllerAndActions() {
    MutationWebService service =
        new MutationWebService(new MutationReportParser(), new MutationReportStorageService());
    WebService.Context context = new WebService.Context();
    service.define(context);

    WebService.Controller controller = context.controller("api/chillcode_mutation");
    assertNotNull(controller);
    assertEquals("ChillCode Mutation Testing API", controller.description());
    assertEquals("1.0", controller.since());

    assertNotNull(controller.action("upload"));
    assertTrue(controller.action("upload").isPost());
    assertNotNull(controller.action("upload").param("projectKey"));
    assertNotNull(controller.action("upload").param("branch"));
    assertNotNull(controller.action("upload").param("report"));

    assertNotNull(controller.action("download"));
    assertNotNull(controller.action("summary"));
    assertNotNull(controller.action("status"));
    assertNotNull(controller.action("delete"));
    assertTrue(controller.action("delete").isPost());
    assertNotNull(controller.action("badge"));
  }

  @Test
  void handlesStatusAction() throws Exception {
    MutationReportStorageService storage =
        org.mockito.Mockito.mock(MutationReportStorageService.class);
    MutationWebService service =
        new MutationWebService(new MutationReportParser(), storage, "secret");
    WebService.Context context = new WebService.Context();
    service.define(context);

    WebService.Action statusAction = context.controller("api/chillcode_mutation").action("status");

    // Case 1: No report
    Request req1 = org.mockito.Mockito.mock(Request.class);
    org.mockito.Mockito.when(req1.mandatoryParam("projectKey")).thenReturn("proj1");
    org.mockito.Mockito.when(req1.param("branch")).thenReturn(null);
    org.mockito.Mockito.when(req1.localConnector()).thenReturn(connectorAnswering(200));

    RecordingResponse res1 = new RecordingResponse();
    statusAction.handler().handle(req1, res1);
    assertTrue(res1.body().contains("\"hasReport\":false"));
    assertTrue(res1.body().contains("\"projectKey\":\"proj1\""));

    // Case 2: Has report
    NormalizedMutationReport report = new NormalizedMutationReport();
    NormalizedMutationReport.MutationSummary summary =
        new NormalizedMutationReport.MutationSummary();
    summary.setScore(75.5);
    summary.setTotal(40);
    report.setSummary(summary);
    org.mockito.Mockito.when(storage.loadReport("proj1", "main")).thenReturn(report);

    RecordingResponse res2 = new RecordingResponse();
    statusAction.handler().handle(req1, res2);
    assertTrue(res2.body().contains("\"hasReport\":true"));
    assertTrue(res2.body().contains("\"mutationScore\":75.5"));
    assertTrue(res2.body().contains("\"totalMutants\":40"));
  }

  @Test
  void handlesSummaryAction() throws Exception {
    MutationReportStorageService storage =
        org.mockito.Mockito.mock(MutationReportStorageService.class);
    MutationWebService service =
        new MutationWebService(new MutationReportParser(), storage, "secret");
    WebService.Context context = new WebService.Context();
    service.define(context);

    WebService.Action summaryAction =
        context.controller("api/chillcode_mutation").action("summary");

    Request req = org.mockito.Mockito.mock(Request.class);
    org.mockito.Mockito.when(req.mandatoryParam("projectKey")).thenReturn("proj1");
    org.mockito.Mockito.when(req.param("branch")).thenReturn("main");
    org.mockito.Mockito.when(req.localConnector()).thenReturn(connectorAnswering(200));

    // Case 1: No report
    RecordingResponse res1 = new RecordingResponse();
    summaryAction.handler().handle(req, res1);
    assertTrue(res1.body().contains("\"hasReport\":false"));

    // Case 2: With report
    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setTool("pitest");
    report.setLanguage("java");
    NormalizedMutationReport.MutationSummary summary =
        new NormalizedMutationReport.MutationSummary();
    summary.setScore(90.0);
    summary.setTotal(20);
    summary.setKilled(18);
    summary.setSurvived(2);
    summary.setNoCoverage(0);
    summary.setTimeout(0);
    summary.setIgnored(0);
    report.setSummary(summary);
    org.mockito.Mockito.when(storage.loadReport("proj1", "main")).thenReturn(report);

    RecordingResponse res2 = new RecordingResponse();
    summaryAction.handler().handle(req, res2);
    assertTrue(res2.body().contains("\"hasReport\":true"));
    assertTrue(res2.body().contains("\"projectKey\":\"proj1\""));
    assertTrue(res2.body().contains("\"mutationScore\":90.0"));
    assertTrue(res2.body().contains("\"tool\":\"pitest\""));
  }

  @Test
  void handlesDeleteAction() throws Exception {
    MutationReportStorageService storage =
        org.mockito.Mockito.mock(MutationReportStorageService.class);
    MutationWebService service =
        new MutationWebService(new MutationReportParser(), storage, "secret-token");
    WebService.Context context = new WebService.Context();
    service.define(context);

    WebService.Action deleteAction = context.controller("api/chillcode_mutation").action("delete");

    // Case 1: Unauthorized
    Request req1 = org.mockito.Mockito.mock(Request.class);
    org.mockito.Mockito.when(req1.header("Authorization"))
        .thenReturn(java.util.Optional.of("Bearer wrong"));
    RecordingResponse res1 = new RecordingResponse();
    deleteAction.handler().handle(req1, res1);
    assertEquals(403, res1.status());

    // Case 2: Authorized
    Request req2 = org.mockito.Mockito.mock(Request.class);
    org.mockito.Mockito.when(req2.header("Authorization"))
        .thenReturn(java.util.Optional.of("Bearer secret-token"));
    org.mockito.Mockito.when(req2.mandatoryParam("projectKey")).thenReturn("proj1");
    org.mockito.Mockito.when(req2.param("branch")).thenReturn("main");

    RecordingResponse res2 = new RecordingResponse();
    deleteAction.handler().handle(req2, res2);
    assertTrue(res2.body().contains("\"status\":\"deleted\""));
    assertTrue(res2.body().contains("\"projectKey\":\"proj1\""));
    org.mockito.Mockito.verify(storage).deleteReport("proj1", "main");
  }

  @Test
  void handlesUploadAction() throws Exception {
    MutationReportStorageService storage =
        org.mockito.Mockito.mock(MutationReportStorageService.class);
    org.mockito.Mockito.when(storage.getMaxReportSize()).thenReturn(10L * 1024 * 1024);
    MutationWebService service =
        new MutationWebService(new MutationReportParser(), storage, "secret-token");
    WebService.Context context = new WebService.Context();
    service.define(context);

    WebService.Action uploadAction = context.controller("api/chillcode_mutation").action("upload");

    Request req = org.mockito.Mockito.mock(Request.class);
    org.mockito.Mockito.when(req.header("Authorization"))
        .thenReturn(java.util.Optional.of("Bearer secret-token"));
    org.mockito.Mockito.when(req.mandatoryParam("projectKey")).thenReturn("proj1");
    org.mockito.Mockito.when(req.param("branch")).thenReturn("main");

    // Prepare gzipped report
    String xml = "<mutations></mutations>";
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    try (java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(baos)) {
      gos.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    org.mockito.Mockito.when(req.paramAsInputStream("report"))
        .thenReturn(new ByteArrayInputStream(baos.toByteArray()));

    RecordingResponse res = new RecordingResponse();
    uploadAction.handler().handle(req, res);
    assertTrue(res.body().contains("\"status\":\"ok\""));
    assertTrue(res.body().contains("\"projectKey\":\"proj1\""));
    assertTrue(res.body().contains("\"branch\":\"main\""));
    org.mockito.Mockito.verify(storage).storeReport(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsAReportAboveTheConfiguredLimit() {
    byte[] report = "123456".getBytes();

    assertThrows(
        IOException.class,
        () -> MutationWebService.readLimited(new ByteArrayInputStream(report), report.length - 1));
  }
}
