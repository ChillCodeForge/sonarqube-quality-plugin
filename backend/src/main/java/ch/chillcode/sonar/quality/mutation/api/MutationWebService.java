package ch.chillcode.sonar.quality.mutation.api;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.service.MutationService;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.config.Configuration;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class MutationWebService {

    private static final Logger LOG = Loggers.get(MutationWebService.class);

    private final MutationService mutationService;
    private final MutationReportStorageService storageService;
    private final String authToken;

    public MutationWebService(Configuration config, MutationReportStorageService storageService) {
        this.storageService = storageService;
        this.mutationService = new MutationService(storageService);
        this.authToken = config.get("chillcode.mutation.apiToken").orElse(null);
    }

    public void define(WebService.NewController controller) {
        WebService.NewController ws = controller
                .setDescription("ChillCode Mutation Testing Report API")
                .setName("api/chillcode_mutation")
                .setSince("1.0");

        // Upload report
        ws.createAction("upload")
                .setDescription("Upload a normalized mutation testing report")
                .setSince("1.0")
                .setHandler(this::uploadReport)
                .createParam("projectKey")
                .setDescription("SonarQube project key")
                .setRequired(true)
                .done()
                .createParam("branch")
                .setDescription("Branch name")
                .setRequired(true)
                .done()
                .createParam("commit")
                .setDescription("Commit SHA")
                .setRequired(true)
                .done()
                .createParam("report")
                .setDescription("Normalized mutation report JSON (multipart or raw body)")
                .setRequired(true)
                .done();

        // Download report
        ws.createAction("download")
                .setDescription("Download the current mutation report for a project/branch")
                .setSince("1.0")
                .setHandler(this::downloadReport)
                .createParam("projectKey")
                .setDescription("SonarQube project key")
                .setRequired(true)
                .done()
                .createParam("branch")
                .setDescription("Branch name")
                .setRequired(true)
                .done();

        // Summary
        ws.createAction("summary")
                .setDescription("Get mutation summary for a project/branch")
                .setSince("1.0")
                .setHandler(this::getSummary)
                .createParam("projectKey")
                .setDescription("SonarQube project key")
                .setRequired(true)
                .done()
                .createParam("branch")
                .setDescription("Branch name")
                .setRequired(true)
                .done();

        // Status
        ws.createAction("status")
                .setDescription("Get mutation testing status for a project")
                .setSince("1.0")
                .setHandler(this::getStatus)
                .createParam("projectKey")
                .setDescription("SonarQube project key")
                .setRequired(true)
                .done()
                .createParam("branch")
                .setDescription("Branch name (optional, defaults to main)")
                .setRequired(false)
                .done();

        // Delete
        ws.createAction("delete")
                .setDescription("Delete mutation report for a project/branch")
                .setSince("1.0")
                .setHandler(this::deleteReport)
                .createParam("projectKey")
                .setDescription("SonarQube project key")
                .setRequired(true)
                .done()
                .createParam("branch")
                .setDescription("Branch name")
                .setRequired(true)
                .done();
    }

    private void checkAuth(Request request) {
        if (authToken != null) {
            String token = request.param("authToken");
            if (!authToken.equals(token)) {
                throw new SecurityException("Invalid authentication token");
            }
        }
        // Also check Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (authToken != null && !authToken.equals(token)) {
                throw new SecurityException("Invalid authentication token");
            }
        }
    }

    private void uploadReport(Request request, Response response) {
        checkAuth(request);

        String projectKey = request.param("projectKey");
        String branch = request.param("branch");
        String commit = request.param("commit");
        String reportJson = request.param("report");

        if (projectKey == null || branch == null || commit == null || reportJson == null) {
            response.status(400).write("Missing required parameters").done();
            return;
        }

        try {
            NormalizedMutationReport report = new MutationReportParser().parse(reportJson);
            report.setProject(projectKey);
            report.setBranch(branch);
            report.setCommit(commit);

            storageService.storeReport(report);

            response.stream(MediaType.JSON).write("{\"status\":\"ok\",\"message\":\"Report uploaded and stored\"}").done();

        } catch (IOException e) {
            LOG.error("Failed to parse uploaded report", e);
            response.status(400).write("Invalid report format: " + e.getMessage()).done();
        } catch (Exception e) {
            LOG.error("Failed to store report", e);
            response.status(500).write("Internal error: " + e.getMessage()).done();
        }
    }

    private void downloadReport(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            response.status(400).write("Missing required parameters").done();
            return;
        }

        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, branch);
            if (report == null) {
                response.status(404).write("Report not found").done();
                return;
            }

            response.stream(MediaType.JSON).write(report).done();

        } catch (Exception e) {
            LOG.error("Failed to load report", e);
            response.status(500).write("Internal error: " + e.getMessage()).done();
        }
    }

    private void getSummary(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            response.status(400).write("Missing required parameters").done();
            return;
        }

        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, branch);
            if (report == null) {
                response.status(404).write("Report not found").done();
                return;
            }

            NormalizedMutationReport.MutationSummary summary = report.getSummary();
            String json = String.format(
                    "{\"score\":%.2f,\"total\":%d,\"killed\":%d,\"survived\":%d,\"noCoverage\":%d,\"timeout\":%d,\"ignored\":%d,\"tool\":\"%s\",\"language\":\"%s\"}",
                    summary.getScore(),
                    summary.getTotal(),
                    summary.getKilled(),
                    summary.getSurvived(),
                    summary.getNoCoverage(),
                    summary.getTimeout(),
                    summary.getIgnored(),
                    report.getTool(),
                    report.getLanguage()
            );

            response.stream(MediaType.JSON).write(json).done();

        } catch (Exception e) {
            LOG.error("Failed to get summary", e);
            response.status(500).write("Internal error: " + e.getMessage()).done();
        }
    }

    private void getStatus(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");
        if (branch == null) branch = "main";

        if (projectKey == null) {
            response.status(400).write("Missing projectKey").done();
            return;
        }

        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, branch);
            String json;
            if (report == null) {
                json = "{\"hasReport\":false}";
            } else {
                NormalizedMutationReport.MutationSummary s = report.getSummary();
                json = String.format(
                        "{\"hasReport\":true,\"score\":%.2f,\"total\":%d,\"survived\":%d,\"tool\":\"%s\",\"timestamp\":\"%s\"}",
                        s.getScore(), s.getTotal(), s.getSurvived(), report.getTool(), report.getTimestamp()
                );
            }
            response.stream(MediaType.JSON).write(json).done();
        } catch (Exception e) {
            LOG.error("Failed to get status", e);
            response.status(500).write("Internal error: " + e.getMessage()).done();
        }
    }

    private void deleteReport(Request request, Response response) {
        checkAuth(request);

        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            response.status(400).write("Missing required parameters").done();
            return;
        }

        try {
            storageService.deleteReport(projectKey, branch);
            response.stream(MediaType.JSON).write("{\"status\":\"ok\",\"message\":\"Report deleted\"}").done();
        } catch (Exception e) {
            LOG.error("Failed to delete report", e);
            response.status(500).write("Internal error: " + e.getMessage()).done();
        }
    }
}