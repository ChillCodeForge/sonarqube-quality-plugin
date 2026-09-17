package ch.chillcode.sonar.quality.mutation.api;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import ch.chillcode.sonar.quality.mutation.parser.MutationReportParser;
import ch.chillcode.sonar.quality.mutation.service.MutationService;
import ch.chillcode.sonar.quality.mutation.storage.MutationReportStorageService;
import org.sonar.api.config.Configuration;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import jakarta.annotation.Nullable;
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
                .setDescription("ChillCode Mutation Testing Report API");

        // Upload report
        WebService.NewAction upload = ws.createAction("upload")
                .setDescription("Upload a normalized mutation testing report")
                .setHandler(this::uploadReport);
        upload.createParam("projectKey").setDescription("SonarQube project key").setRequired(true);
        upload.createParam("branch").setDescription("Branch name").setRequired(true);
        upload.createParam("commit").setDescription("Commit SHA").setRequired(true);
        upload.createParam("report").setDescription("Normalized mutation report JSON (multipart or raw body)").setRequired(true);

        // Download report
        WebService.NewAction download = ws.createAction("download")
                .setDescription("Download the current mutation report for a project/branch")
                .setHandler(this::downloadReport);
        download.createParam("projectKey").setDescription("SonarQube project key").setRequired(true);
        download.createParam("branch").setDescription("Branch name").setRequired(true);

        // Summary
        WebService.NewAction summary = ws.createAction("summary")
                .setDescription("Get mutation summary for a project/branch")
                .setHandler(this::getSummary);
        summary.createParam("projectKey").setDescription("SonarQube project key").setRequired(true);
        summary.createParam("branch").setDescription("Branch name").setRequired(true);

        // Status
        WebService.NewAction status = ws.createAction("status")
                .setDescription("Get mutation testing status for a project")
                .setHandler(this::getStatus);
        status.createParam("projectKey").setDescription("SonarQube project key").setRequired(true);
        status.createParam("branch").setDescription("Branch name (optional, defaults to main)").setRequired(false);

        // Delete
        WebService.NewAction delete = ws.createAction("delete")
                .setDescription("Delete mutation report for a project/branch")
                .setHandler(this::deleteReport);
        delete.createParam("projectKey").setDescription("SonarQube project key").setRequired(true);
        delete.createParam("branch").setDescription("Branch name").setRequired(true);
    }

    private void checkAuth(Request request) {
        if (authToken != null) {
            String token = request.param("authToken");
            if (!authToken.equals(token)) {
                throw new SecurityException("Invalid authentication token");
            }
        }
        String authHeader = request.param("Authorization");
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
            writeError(response, 400, "Missing required parameters");
            return;
        }

        try {
            NormalizedMutationReport report = new MutationReportParser().parse(reportJson);
            report.setProject(projectKey);
            report.setBranch(branch);
            report.setCommit(commit);

            storageService.storeReport(report);

            writeSuccess(response, "{\"status\":\"ok\",\"message\":\"Report uploaded and stored\"}");

        } catch (IOException e) {
            LOG.error("Failed to parse uploaded report", e);
            writeError(response, 400, "Invalid report format: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to store report", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private void downloadReport(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            writeError(response, 400, "Missing required parameters");
            return;
        }

        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, branch);
            if (report == null) {
                writeError(response, 404, "Report not found");
                return;
            }

            writeJson(response, report.toString());

        } catch (IOException e) {
            LOG.error("Failed to load report", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to load report", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private void getSummary(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            writeError(response, 400, "Missing required parameters");
            return;
        }

        try {
            NormalizedMutationReport report = storageService.loadReport(projectKey, branch);
            if (report == null) {
                writeError(response, 404, "Report not found");
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

            writeJson(response, json);

        } catch (IOException e) {
            LOG.error("Failed to get summary", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to get summary", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private void getStatus(Request request, Response response) {
        String projectKey = request.param("projectKey");
        String branch = request.param("branch");
        if (branch == null) branch = "main";

        if (projectKey == null) {
            writeError(response, 400, "Missing projectKey");
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
            writeJson(response, json);
        } catch (IOException e) {
            LOG.error("Failed to get status", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to get status", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private void deleteReport(Request request, Response response) {
        checkAuth(request);

        String projectKey = request.param("projectKey");
        String branch = request.param("branch");

        if (projectKey == null || branch == null) {
            writeError(response, 400, "Missing required parameters");
            return;
        }

        try {
            storageService.deleteReport(projectKey, branch);
            writeSuccess(response, "{\"status\":\"ok\",\"message\":\"Report deleted\"}");
        } catch (IOException e) {
            LOG.error("Failed to delete report", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to delete report", e);
            writeError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private void writeSuccess(Response response, String json) {
        try {
            response.stream().setMediaType("application/json").setStatus(200).output().write(json.getBytes());
        } catch (IOException e) {
            LOG.error("Failed to write success response", e);
        }
    }

    private void writeError(Response response, int status, String message) {
        try {
            response.stream().setStatus(status).output().write(message.getBytes());
        } catch (IOException e) {
            LOG.error("Failed to write error response", e);
        }
    }

    private void writeJson(Response response, String json) {
        try {
            response.stream().setMediaType("application/json").output().write(json.getBytes());
        } catch (IOException e) {
            LOG.error("Failed to write JSON response", e);
        }
    }
}