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
            
            NormalizedMutationReport report = parser.parseFromStream(gis);
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
}