package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class MutationReportParser {

    private static final Logger LOG = Loggers.get(MutationReportParser.class);
    private final ObjectMapper mapper;

    public MutationReportParser() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public NormalizedMutationReport parse(File reportFile) throws IOException {
        if (!reportFile.exists()) {
            throw new IllegalArgumentException("Report file does not exist: " + reportFile.getAbsolutePath());
        }

        String content;
        if (reportFile.getName().endsWith(".gz")) {
            try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(reportFile.toPath()))) {
                content = new String(gis.readAllBytes());
            }
        } else {
            content = Files.readString(reportFile.toPath());
        }

        try {
            JsonNode root = mapper.readTree(content);
            NormalizedMutationReport report = detectAndParse(root);
            LOG.info("Parsed mutation report: tool={}, project={}, score={}",
                    report.getTool(), report.getProject(), report.getSummary().getScore());
            return report;
        } catch (Exception e) {
            LOG.error("Failed to parse mutation report: " + reportFile.getAbsolutePath(), e);
            throw new IOException("Failed to parse mutation report", e);
        }
    }

    public NormalizedMutationReport parse(String jsonContent) throws IOException {
        try {
            JsonNode root = mapper.readTree(jsonContent);
            return detectAndParse(root);
        } catch (Exception e) {
            LOG.error("Failed to parse mutation report from string", e);
            throw new IOException("Failed to parse mutation report from string", e);
        }
    }

    public NormalizedMutationReport parseFromStream(InputStream inputStream) throws IOException {
        try {
            JsonNode root = mapper.readTree(inputStream);
            return detectAndParse(root);
        } catch (Exception e) {
            LOG.error("Failed to parse mutation report from stream", e);
            throw new IOException("Failed to parse mutation report from stream", e);
        }
    }

    private NormalizedMutationReport detectAndParse(JsonNode root) throws IOException {
        // Stryker format detection
        if (root.has("schemaVersion") && root.has("files")) {
            return parseStryker(root);
        }
        // Pitest format detection
        if (root.has("mutations")) {
            return parsePitest(root);
        }
        // Mutmut format detection
        if (root.has("mutants")) {
            return parseMutmut(root);
        }
        // Already normalized format
        if (root.has("tool") && root.has("summary")) {
            return mapper.treeToValue(root, NormalizedMutationReport.class);
        }
        throw new IOException("Unknown mutation report format");
    }

    private NormalizedMutationReport parseStryker(JsonNode root) throws IOException {
        NormalizedMutationReport report = new NormalizedMutationReport();
        report.setSchemaVersion(root.path("schemaVersion").asInt(1));
        report.setTool("stryker");
        report.setLanguage(detectLanguage(root));
        report.setProject(root.path("projectName").asText("unknown"));
        report.setBranch(root.path("branch").asText("main"));
        report.setCommit(root.path("commit").asText(""));
        report.setTimestamp(LocalDateTime.now());

        NormalizedMutationReport.MutationSummary summary = new NormalizedMutationReport.MutationSummary();
        List<NormalizedMutationReport.MutationFile> files = new ArrayList<>();
        List<NormalizedMutationReport.MutationMutant> allMutants = new ArrayList<>();

        int[] total = {0};
        int[] killed = {0};
        int[] survived = {0};
        int[] noCoverage = {0};
        int[] timeout = {0};
        int[] ignored = {0};

        JsonNode filesNode = root.path("files");
        if (filesNode.isObject()) {
            filesNode.fields().forEachRemaining(entry -> {
                String filePath = entry.getKey();
                JsonNode fileData = entry.getValue();
                
                NormalizedMutationReport.MutationFile mFile = new NormalizedMutationReport.MutationFile();
                mFile.setPath(filePath);
                mFile.setLanguage(detectLanguageFromPath(filePath));
                
                List<NormalizedMutationReport.MutationMutant> fileMutants = new ArrayList<>();
                
                JsonNode mutantsNode = fileData.path("mutants");
                if (mutantsNode.isArray()) {
                    for (JsonNode mutantNode : mutantsNode) {
                        NormalizedMutationReport.MutationMutant mutant = parseStrykerMutant(mutantNode, filePath);
                        fileMutants.add(mutant);
                        allMutants.add(mutant);
                        
                        total[0]++;
                        switch (mutant.getStatus()) {
                            case "KILLED" -> killed[0]++;
                            case "SURVIVED" -> survived[0]++;
                            case "NO_COVERAGE" -> noCoverage[0]++;
                            case "TIMEOUT" -> timeout[0]++;
                            case "IGNORED" -> ignored[0]++;
                        }
                    }
                }
                
                mFile.setMutants(fileMutants);
                files.add(mFile);
            });
        }

        summary.setTotal(total[0]);
        summary.setKilled(killed[0]);
        summary.setSurvived(survived[0]);
        summary.setNoCoverage(noCoverage[0]);
        summary.setTimeout(timeout[0]);
        summary.setIgnored(ignored[0]);
        summary.setScore(total[0] > 0 ? (double) killed[0] / (total[0] - ignored[0]) * 100 : 0);

        report.setSummary(summary);
        report.setFiles(files);
        report.setMutants(allMutants);

        return report;
    }

    private NormalizedMutationReport.MutationMutant parseStrykerMutant(JsonNode mutantNode, String filePath) {
        NormalizedMutationReport.MutationMutant mutant = new NormalizedMutationReport.MutationMutant();
        mutant.setId(mutantNode.path("id").asText());
        mutant.setMutatorName(mutantNode.path("mutatorName").asText());
        mutant.setReplacement(mutantNode.path("replacement").asText());
        
        String status = mutantNode.path("status").asText();
        mutant.setStatus(mapStrykerStatus(status));
        mutant.setStatusReason(mutantNode.path("statusReason").asText(""));
        
        JsonNode location = mutantNode.path("location");
        if (!location.isMissingNode()) {
            NormalizedMutationReport.MutationLocation loc = new NormalizedMutationReport.MutationLocation();
            NormalizedMutationReport.MutationLocation.Position start = new NormalizedMutationReport.MutationLocation.Position();
            start.setLine(location.path("start").path("line").asInt(0));
            start.setColumn(location.path("start").path("column").asInt(0));
            NormalizedMutationReport.MutationLocation.Position end = new NormalizedMutationReport.MutationLocation.Position();
            end.setLine(location.path("end").path("line").asInt(0));
            end.setColumn(location.path("end").path("column").asInt(0));
            loc.setStart(start);
            loc.setEnd(end);
            mutant.setLocation(loc);
        }
        
        mutant.setFilePath(filePath);
        mutant.setCoveredBy(List.of()); // Stryker doesn't always provide coveredBy in summary
        mutant.setStaticMutant(mutantNode.path("static").asBoolean(false));
        
        return mutant;
    }

    private String mapStrykerStatus(String strykerStatus) {
        return switch (strykerStatus.toLowerCase()) {
            case "killed" -> "KILLED";
            case "survived" -> "SURVIVED";
            case "no_coverage" -> "NO_COVERAGE";
            case "timeout" -> "TIMEOUT";
            case "ignored" -> "IGNORED";
            case "error" -> "ERROR";
            default -> "UNKNOWN";
        };
    }

    private String detectLanguage(JsonNode root) {
        String projectName = root.path("projectName").asText("");
        if (projectName.toLowerCase().contains("python") || projectName.toLowerCase().contains("py")) {
            return "python";
        }
        return "typescript"; // Default for Stryker
    }

    private String detectLanguageFromPath(String path) {
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".ts") || path.endsWith(".tsx")) return "typescript";
        if (path.endsWith(".js") || path.endsWith(".jsx")) return "javascript";
        if (path.endsWith(".java")) return "java";
        return "unknown";
    }

    private NormalizedMutationReport parsePitest(JsonNode root) {
        // TODO: Implement Pitest parsing
        throw new UnsupportedOperationException("Pitest parsing not yet implemented");
    }

    private NormalizedMutationReport parseMutmut(JsonNode root) {
        // TODO: Implement Mutmut parsing
        throw new UnsupportedOperationException("Mutmut parsing not yet implemented");
    }
}