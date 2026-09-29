package ch.chillcode.sonar.quality.mutation.api;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import org.sonar.api.server.ws.LocalConnector;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;

public class MutationWebService implements WebService {

  static final Path UPLOAD_TOKEN_FILE = Path.of("/run/secrets/chillcode_mutation_upload_token");

  // API parameter names and the response property keys this service emits,
  // constants keep the web-service description and the JSON responses in sync.
  static final String PARAM_PROJECT_KEY = "projectKey";
  static final String PARAM_BRANCH = "branch";
  static final String PARAM_REPORT = "report";
  static final String DESC_PROJECT_KEY = "SonarQube project key";
  static final String DESC_BRANCH = "Branch name (default: main)";
  static final String PROP_STATUS = "status";
  static final String PROP_PROJECT_KEY = "projectKey";
  static final String PROP_BRANCH = "branch";
  static final String PROP_HAS_REPORT = "hasReport";
  static final String PROP_MUTATION_SCORE = "mutationScore";
  static final String PROP_TOTAL_MUTANTS = "totalMutants";

  private final MutationReportParser parser;
  private final MutationReportStorageService storage;
  private final String uploadToken;

  public MutationWebService(MutationReportParser parser, MutationReportStorageService storage) {
    this(parser, storage, readUploadToken(UPLOAD_TOKEN_FILE));
  }

  MutationWebService(
      MutationReportParser parser, MutationReportStorageService storage, String uploadToken) {
    this.parser = parser;
    this.storage = storage;
    this.uploadToken = uploadToken;
  }

  @Override
  public void define(Context context) {
    NewController controller =
        context
            .createController("api/chillcode_mutation")
            .setDescription("ChillCode Mutation Testing API")
            .setSince("1.0");

    // Upload mutation report
    NewAction uploadAction =
        controller
            .createAction("upload")
            .setDescription("Upload normalized mutation report (JSON gzipped)")
            .setSince("1.0")
            .setPost(true)
            .setHandler(this::handleUpload);

    uploadAction.createParam(PARAM_PROJECT_KEY).setRequired(true).setDescription(DESC_PROJECT_KEY);

    uploadAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    uploadAction
        .createParam(PARAM_REPORT)
        .setRequired(true)
        .setDescription("Mutation report JSON (gzipped)");

    // Download current report
    NewAction downloadAction =
        controller
            .createAction("download")
            .setDescription("Download current mutation report for project")
            .setSince("1.0")
            .setResponseExample(example("download.json"))
            .setHandler(this::handleDownload);

    downloadAction
        .createParam(PARAM_PROJECT_KEY)
        .setRequired(true)
        .setDescription(DESC_PROJECT_KEY);

    downloadAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    // Summary (lightweight)
    NewAction summaryAction =
        controller
            .createAction("summary")
            .setDescription("Get mutation summary (score, counts)")
            .setSince("1.0")
            .setResponseExample(example("summary.json"))
            .setHandler(this::handleSummary);

    summaryAction.createParam(PARAM_PROJECT_KEY).setRequired(true).setDescription(DESC_PROJECT_KEY);

    summaryAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    // Status check
    NewAction statusAction =
        controller
            .createAction("status")
            .setDescription("Quick status check - has report? score?")
            .setSince("1.0")
            .setResponseExample(example("status.json"))
            .setHandler(this::handleStatus);

    statusAction.createParam(PARAM_PROJECT_KEY).setRequired(true).setDescription(DESC_PROJECT_KEY);

    statusAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    // Delete report (cleanup)
    NewAction deleteAction =
        controller
            .createAction("delete")
            .setDescription("Delete mutation report for project/branch")
            .setSince("1.0")
            .setPost(true)
            .setHandler(this::handleDelete);

    deleteAction.createParam(PARAM_PROJECT_KEY).setRequired(true).setDescription(DESC_PROJECT_KEY);

    deleteAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    // SVG badge (shields.io style) - SonarQube's own project badge
    // endpoint only accepts a fixed whitelist of core metrics and
    // rejects custom ones like mutation_score with HTTP 400 ("Value
    // of parameter 'metric' ... must be one of: [coverage, ...]"),
    // so a live badge for mutation data needs its own endpoint.
    NewAction badgeAction =
        controller
            .createAction("badge")
            .setDescription("Mutation score badge (SVG, shields.io style)")
            .setSince("1.0")
            .setResponseExample(example("badge.svg"))
            .setHandler(this::handleBadge);

    badgeAction.createParam(PARAM_PROJECT_KEY).setRequired(true).setDescription(DESC_PROJECT_KEY);

    badgeAction.createParam(PARAM_BRANCH).setRequired(false).setDescription(DESC_BRANCH);

    controller.done();
  }

  /**
   * A response example shipped as a resource beside this class, which SonarQube's web API
   * documentation renders; it logs a warning at startup for each GET action without one.
   */
  static URL example(String name) {
    return MutationWebService.class.getResource("example-" + name);
  }

  private boolean authorizeMutationWrite(Request request, Response response) throws IOException {
    if (uploadToken == null || uploadToken.isBlank()) {
      writeText(response, 503, "Mutation upload is not configured");
      return false;
    }
    String provided = request.header("Authorization").orElse("");
    String expected = "Bearer " + uploadToken;
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
      writeText(response, 403, "Forbidden");
      return false;
    }
    return true;
  }

  /**
   * Whether the caller may browse {@code projectKey}, answered by SonarQube's own permission model
   * through the local connector. A report names every surviving mutant with its file and line, so
   * it is as private as the project's source. A refusal answers 404, the same as a project with no
   * report, so the endpoint does not say which private projects exist.
   */
  static boolean authorizeProjectRead(
      LocalConnector connector, Response response, String projectKey) throws IOException {
    int status = connector.call(new ComponentShowRequest(projectKey)).getStatus();
    if (status == 200) {
      return true;
    }
    writeText(response, 404, "Not Found");
    return false;
  }

  /**
   * A plain-text answer. The media type is set explicitly because some messages echo parser errors
   * that quote the uploaded body, and a browser must never sniff one as HTML.
   */
  static void writeText(Response response, int status, String message) throws IOException {
    response.stream()
        .setMediaType("text/plain")
        .setStatus(status)
        .output()
        .write(message.getBytes(StandardCharsets.UTF_8));
  }

  static String readUploadToken(Path tokenFile) {
    try {
      String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
      return token.isEmpty() ? null : token;
    } catch (IOException e) {
      return null;
    }
  }

  static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new IOException("Decompressed mutation report exceeds configured size limit");
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private void handleUpload(Request request, Response response) throws Exception {
    if (!authorizeMutationWrite(request, response)) {
      return;
    }
    String projectKey = request.mandatoryParam("projectKey");
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    try (InputStream is = request.paramAsInputStream("report");
        GZIPInputStream gis = new GZIPInputStream(is)) {

      byte[] content = readLimited(gis, storage.getMaxReportSize());
      NormalizedMutationReport report = parser.parse(content, "report");
      // The report body (raw Stryker JSON / PITest XML) has no
      // reliable project identifier of its own - the projectKey/
      // branch the caller passed as query params are the source of
      // truth and must win, otherwise storeReport() saves under
      // "unknown" and the project's own /summary lookup never finds
      // it again.
      report.setProject(projectKey);
      report.setBranch(branch);
      storage.storeReport(report);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject()
            .prop(PROP_STATUS, "ok")
            .prop(PROP_PROJECT_KEY, projectKey)
            .prop(PROP_BRANCH, branch)
            .prop(PROP_MUTATION_SCORE, report.getSummary().getScore())
            .prop(PROP_TOTAL_MUTANTS, report.getSummary().getTotal())
            .endObject();
      }
    } catch (IOException e) {
      writeText(response, 400, "Bad Request: " + e.getMessage());
    }
  }

  private void handleDownload(Request request, Response response) throws Exception {
    String projectKey = request.mandatoryParam("projectKey");
    if (!authorizeProjectRead(request.localConnector(), response, projectKey)) {
      return;
    }
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    NormalizedMutationReport report = storage.loadReport(projectKey, branch);
    if (report == null) {
      writeText(response, 404, "Not Found");
      return;
    }

    response.stream()
        .setMediaType("application/json")
        .setStatus(200)
        .output()
        .write(report.toJson().getBytes());
  }

  private void handleSummary(Request request, Response response) throws Exception {
    String projectKey = request.mandatoryParam("projectKey");
    if (!authorizeProjectRead(request.localConnector(), response, projectKey)) {
      return;
    }
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    NormalizedMutationReport report = storage.loadReport(projectKey, branch);
    if (report == null) {
      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject().prop(PROP_HAS_REPORT, false).endObject();
      }
      return;
    }

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject()
          .prop(PROP_HAS_REPORT, true)
          .prop(PROP_PROJECT_KEY, projectKey)
          .prop(PROP_BRANCH, branch)
          .prop(PROP_MUTATION_SCORE, report.getSummary().getScore())
          .prop(PROP_TOTAL_MUTANTS, report.getSummary().getTotal())
          .prop("killedMutants", report.getSummary().getKilled())
          .prop("survivedMutants", report.getSummary().getSurvived())
          .prop("noCoverageMutants", report.getSummary().getNoCoverage())
          .prop("timeoutMutants", report.getSummary().getTimeout())
          .prop("ignoredMutants", report.getSummary().getIgnored())
          .prop("tool", report.getTool())
          .prop("language", report.getLanguage())
          .endObject();
    }
  }

  private void handleStatus(Request request, Response response) throws Exception {
    String projectKey = request.mandatoryParam("projectKey");
    if (!authorizeProjectRead(request.localConnector(), response, projectKey)) {
      return;
    }
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    NormalizedMutationReport report = storage.loadReport(projectKey, branch);

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject()
          .prop(PROP_PROJECT_KEY, projectKey)
          .prop(PROP_BRANCH, branch)
          .prop(PROP_HAS_REPORT, report != null);
      if (report != null) {
        json.prop(PROP_MUTATION_SCORE, report.getSummary().getScore())
            .prop(PROP_TOTAL_MUTANTS, report.getSummary().getTotal());
      }
      json.endObject();
    }
  }

  private void handleDelete(Request request, Response response) throws Exception {
    if (!authorizeMutationWrite(request, response)) {
      return;
    }
    String projectKey = request.mandatoryParam("projectKey");
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    storage.deleteReport(projectKey, branch);

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject()
          .prop(PROP_STATUS, "deleted")
          .prop(PROP_PROJECT_KEY, projectKey)
          .prop(PROP_BRANCH, branch)
          .endObject();
    }
  }

  private void handleBadge(Request request, Response response) throws Exception {
    String projectKey = request.mandatoryParam("projectKey");
    String branch = request.param("branch");
    if (branch == null || branch.isEmpty()) {
      branch = "main";
    }

    NormalizedMutationReport report = storage.loadReport(projectKey, branch);
    String label = "mutation score";
    String value;
    String color;

    if (report == null) {
      value = "no report";
      color = "#9f9f9f"; // grey, shields.io "inactive" convention
    } else {
      double score = report.getSummary().getScore();
      value = String.format("%.1f%%", score);
      // Same 5-band coloring the UI already uses for the A-E rating
      // (getRating() in MutationTesting.tsx), so the badge and the
      // in-app score always agree on what counts as "good".
      if (score >= 90) color = "#4c1"; // A - bright green
      else if (score >= 75) color = "#97CA00"; // B - green
      else if (score >= 60) color = "#dfb317"; // C - yellow
      else if (score >= 40) color = "#fe7d37"; // D - orange
      else color = "#e05d44"; // E - red
    }

    String svg = buildShieldsBadgeSvg(label, value, color);

    response.stream()
        .setMediaType("image/svg+xml")
        .setStatus(200)
        .output()
        .write(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  // Minimal flat-style badge, shape/metrics modeled on shields.io's
  // "flat" template so it looks at home next to coverage/build badges in
  // a README. No external HTTP call, no dependency - just static SVG
  // text with widths estimated from character count (good enough for
  // short label/value strings, avoids pulling in a font-metrics lib).
  private String buildShieldsBadgeSvg(String label, String value, String color) {
    int labelWidth = 11 + label.length() * 6;
    int valueWidth = 14 + value.length() * 6;
    int totalWidth = labelWidth + valueWidth;

    return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""
        + totalWidth
        + "\" height=\"20\" role=\"img\" aria-label=\""
        + label
        + ": "
        + value
        + "\">"
        + "<linearGradient id=\"s\" x2=\"0\" y2=\"100%\">"
        + "<stop offset=\"0\" stop-color=\"#bbb\" stop-opacity=\".1\"/>"
        + "<stop offset=\"1\" stop-opacity=\".1\"/>"
        + "</linearGradient>"
        + "<clipPath id=\"r\"><rect width=\""
        + totalWidth
        + "\" height=\"20\" rx=\"3\" fill=\"#fff\"/></clipPath>"
        + "<g clip-path=\"url(#r)\">"
        + "<rect width=\""
        + labelWidth
        + "\" height=\"20\" fill=\"#555\"/>"
        + "<rect x=\""
        + labelWidth
        + "\" width=\""
        + valueWidth
        + "\" height=\"20\" fill=\""
        + color
        + "\"/>"
        + "<rect width=\""
        + totalWidth
        + "\" height=\"20\" fill=\"url(#s)\"/>"
        + "</g>"
        + "<g fill=\"#fff\" text-anchor=\"middle\" font-family=\"Verdana,Geneva,DejaVu Sans,sans-serif\" font-size=\"11\">"
        + "<text x=\""
        + (labelWidth / 2.0)
        + "\" y=\"14\">"
        + escapeXml(label)
        + "</text>"
        + "<text x=\""
        + (labelWidth + valueWidth / 2.0)
        + "\" y=\"14\">"
        + escapeXml(value)
        + "</text>"
        + "</g>"
        + "</svg>";
  }

  private String escapeXml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
