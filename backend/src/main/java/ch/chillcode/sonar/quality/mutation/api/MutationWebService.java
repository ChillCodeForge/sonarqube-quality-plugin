package ch.chillcode.sonar.quality.mutation.api;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class MutationWebService implements WebService {

    private final MutationReportParser parser;
    private final MutationReportStorageService storage;

    public MutationWebService() {
        this.parser = new MutationReportParser();
        this.storage = new MutationReportStorageService();
    }

    public MutationWebService(MutationReportParser parser, MutationReportStorageService storage) {
        this.parser = parser;
        this.storage = storage;
    }

    @Override
    public void define(Context context) {
        NewController controller = context.createController("api/chillcode_mutation")
            .setDescription("ChillCode Mutation Testing API")
            .setSince("1.0");

        // Upload mutation report
        NewAction uploadAction = controller.createAction("upload")
            .setDescription("Upload normalized mutation report (JSON gzipped)")
            .setPost(true)
            .setHandler(this::handleUpload);
        
        uploadAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");
        
        uploadAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");
        
        uploadAction.createParam("report")
            .setRequired(true)
            .setDescription("Mutation report JSON (gzipped)");

        // Download current report
        NewAction downloadAction = controller.createAction("download")
            .setDescription("Download current mutation report for project")
            .setHandler(this::handleDownload);
        
        downloadAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");
        
        downloadAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");

        // Summary (lightweight)
        NewAction summaryAction = controller.createAction("summary")
            .setDescription("Get mutation summary (score, counts)")
            .setHandler(this::handleSummary);
        
        summaryAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");
        
        summaryAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");

        // Status check
        NewAction statusAction = controller.createAction("status")
            .setDescription("Quick status check - has report? score?")
            .setHandler(this::handleStatus);
        
        statusAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");
        
        statusAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");

        // Delete report (cleanup)
        NewAction deleteAction = controller.createAction("delete")
            .setDescription("Delete mutation report for project/branch")
            .setPost(true)
            .setHandler(this::handleDelete);
        
        deleteAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");
        
        deleteAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");

        // SVG badge (shields.io style) - SonarQube's own project badge
        // endpoint only accepts a fixed whitelist of core metrics and
        // rejects custom ones like mutation_score with HTTP 400 ("Value
        // of parameter 'metric' ... must be one of: [coverage, ...]"),
        // so a live badge for mutation data needs its own endpoint.
        NewAction badgeAction = controller.createAction("badge")
            .setDescription("Mutation score badge (SVG, shields.io style)")
            .setHandler(this::handleBadge);

        badgeAction.createParam("projectKey")
            .setRequired(true)
            .setDescription("SonarQube project key");

        badgeAction.createParam("branch")
            .setRequired(false)
            .setDescription("Branch name (default: main)");

        controller.done();
    }

    private void handleUpload(Request request, Response response) throws Exception {
        String projectKey = request.mandatoryParam("projectKey");
        String branch = request.param("branch");
        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }
        
        try (InputStream is = request.paramAsInputStream("report");
             GZIPInputStream gis = new GZIPInputStream(is)) {

            byte[] content = gis.readAllBytes();
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
                    .prop("status", "ok")
                    .prop("projectKey", projectKey)
                    .prop("branch", branch)
                    .prop("mutationScore", report.getSummary().getScore())
                    .prop("totalMutants", report.getSummary().getTotal())
                    .endObject();
            }
        } catch (IOException e) {
            response.stream().setStatus(400).output().write(("Bad Request: " + e.getMessage()).getBytes());
        }
    }

    private void handleDownload(Request request, Response response) throws Exception {
        String projectKey = request.mandatoryParam("projectKey");
        String branch = request.param("branch");
        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }
        
        NormalizedMutationReport report = storage.loadReport(projectKey, branch);
        if (report == null) {
            response.stream().setStatus(404).output().write("Not Found".getBytes());
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
        String branch = request.param("branch");
        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }
        
        NormalizedMutationReport report = storage.loadReport(projectKey, branch);
        if (report == null) {
            try (JsonWriter json = response.newJsonWriter()) {
                json.beginObject()
                    .prop("hasReport", false)
                    .endObject();
            }
            return;
        }
        
        try (JsonWriter json = response.newJsonWriter()) {
            json.beginObject()
                .prop("hasReport", true)
                .prop("projectKey", projectKey)
                .prop("branch", branch)
                .prop("mutationScore", report.getSummary().getScore())
                .prop("totalMutants", report.getSummary().getTotal())
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
        String branch = request.param("branch");
        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }
        
        NormalizedMutationReport report = storage.loadReport(projectKey, branch);
        
        try (JsonWriter json = response.newJsonWriter()) {
            json.beginObject()
                .prop("projectKey", projectKey)
                .prop("branch", branch)
                .prop("hasReport", report != null);
            if (report != null) {
                json.prop("mutationScore", report.getSummary().getScore())
                    .prop("totalMutants", report.getSummary().getTotal());
            }
            json.endObject();
        }
    }

    private void handleDelete(Request request, Response response) throws Exception {
        String projectKey = request.mandatoryParam("projectKey");
        String branch = request.param("branch");
        if (branch == null || branch.isEmpty()) {
            branch = "main";
        }
        
        storage.deleteReport(projectKey, branch);
        
        try (JsonWriter json = response.newJsonWriter()) {
            json.beginObject()
                .prop("status", "deleted")
                .prop("projectKey", projectKey)
                .prop("branch", branch)
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
            if (score >= 90) color = "#4c1";       // A - bright green
            else if (score >= 75) color = "#97CA00"; // B - green
            else if (score >= 60) color = "#dfb317"; // C - yellow
            else if (score >= 40) color = "#fe7d37"; // D - orange
            else color = "#e05d44";                   // E - red
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

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + totalWidth + "\" height=\"20\" role=\"img\" aria-label=\"" + label + ": " + value + "\">"
            + "<linearGradient id=\"s\" x2=\"0\" y2=\"100%\">"
            + "<stop offset=\"0\" stop-color=\"#bbb\" stop-opacity=\".1\"/>"
            + "<stop offset=\"1\" stop-opacity=\".1\"/>"
            + "</linearGradient>"
            + "<clipPath id=\"r\"><rect width=\"" + totalWidth + "\" height=\"20\" rx=\"3\" fill=\"#fff\"/></clipPath>"
            + "<g clip-path=\"url(#r)\">"
            + "<rect width=\"" + labelWidth + "\" height=\"20\" fill=\"#555\"/>"
            + "<rect x=\"" + labelWidth + "\" width=\"" + valueWidth + "\" height=\"20\" fill=\"" + color + "\"/>"
            + "<rect width=\"" + totalWidth + "\" height=\"20\" fill=\"url(#s)\"/>"
            + "</g>"
            + "<g fill=\"#fff\" text-anchor=\"middle\" font-family=\"Verdana,Geneva,DejaVu Sans,sans-serif\" font-size=\"11\">"
            + "<text x=\"" + (labelWidth / 2.0) + "\" y=\"14\">" + escapeXml(label) + "</text>"
            + "<text x=\"" + (labelWidth + valueWidth / 2.0) + "\" y=\"14\">" + escapeXml(value) + "</text>"
            + "</g>"
            + "</svg>";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}