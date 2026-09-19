package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

        byte[] content;
        if (reportFile.getName().endsWith(".gz")) {
            try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(reportFile.toPath()))) {
                content = gis.readAllBytes();
            }
        } else {
            content = Files.readAllBytes(reportFile.toPath());
        }

        String logicalName = reportFile.getName().endsWith(".gz")
                ? reportFile.getName().substring(0, reportFile.getName().length() - 3)
                : reportFile.getName();
        return parse(content, logicalName);
    }

    /**
     * Parses a report whose format is decided by content, not just the file
     * name: PIT's mutations.xml is XML, Stryker/mutmut reports are JSON.
     * fileName is only used as a hint when content sniffing is ambiguous
     * (e.g. an empty body).
     */
    public NormalizedMutationReport parse(byte[] content, String fileName) throws IOException {
        String text = new String(content, StandardCharsets.UTF_8).stripLeading();
        boolean looksLikeXml = text.startsWith("<") || (fileName != null && fileName.endsWith(".xml"));
        try {
            if (looksLikeXml) {
                return parsePitestXml(text);
            }
            JsonNode root = mapper.readTree(text);
            NormalizedMutationReport report = detectAndParse(root);
            LOG.info("Parsed mutation report: tool={}, project={}, score={}",
                    report.getTool(), report.getProject(), report.getSummary().getScore());
            return report;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to parse mutation report: " + fileName, e);
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
                int[] fileTotal = {0};
                int[] fileKilled = {0};
                int[] fileSurvived = {0};
                int[] fileNoCoverage = {0};
                int[] fileTimeout = {0};
                int[] fileIgnored = {0};
                if (mutantsNode.isArray()) {
                    for (JsonNode mutantNode : mutantsNode) {
                        NormalizedMutationReport.MutationMutant mutant = parseStrykerMutant(mutantNode, filePath);
                        fileMutants.add(mutant);
                        allMutants.add(mutant);
                        
                        total[0]++;
                        fileTotal[0]++;
                        switch (mutant.getStatus()) {
                            case "KILLED" -> { killed[0]++; fileKilled[0]++; }
                            case "SURVIVED" -> { survived[0]++; fileSurvived[0]++; }
                            case "NO_COVERAGE" -> { noCoverage[0]++; fileNoCoverage[0]++; }
                            case "TIMEOUT" -> { timeout[0]++; fileTimeout[0]++; }
                            case "IGNORED" -> { ignored[0]++; fileIgnored[0]++; }
                        }
                    }
                }
                
                mFile.setMutants(fileMutants);
                // Per-file breakdown so the UI's file table can show a
                // per-file score/killed/survived instead of "N/A" - the
                // frontend reads file.metrics.{score,killed,survived,
                // noCoverage}, this was previously never populated.
                int fileValid = fileKilled[0] + fileSurvived[0] + fileNoCoverage[0] + fileTimeout[0];
                java.util.Map<String, Object> fileMetrics = new java.util.HashMap<>();
                fileMetrics.put("score", fileValid > 0 ? (double) fileKilled[0] / fileValid * 100 : 0.0);
                fileMetrics.put("total", fileTotal[0]);
                fileMetrics.put("killed", fileKilled[0]);
                fileMetrics.put("survived", fileSurvived[0]);
                fileMetrics.put("noCoverage", fileNoCoverage[0]);
                fileMetrics.put("timeout", fileTimeout[0]);
                fileMetrics.put("ignored", fileIgnored[0]);
                mFile.setMetrics(fileMetrics);
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
            case "nocoverage", "no_coverage" -> "NO_COVERAGE";
            case "timeout" -> "TIMEOUT";
            case "ignored" -> "IGNORED";
            case "error", "compileerror" -> "ERROR";
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

    private NormalizedMutationReport parsePitestXml(String xml) throws IOException {
        Document doc = parseXmlDocument(xml);
        Element root = doc.getDocumentElement();
        if (!"mutations".equals(root.getTagName())) {
            throw new IOException("Unknown mutation report format (expected <mutations> root, got <"
                    + root.getTagName() + ">)");
        }

        NormalizedMutationReport report = new NormalizedMutationReport();
        report.setSchemaVersion(1);
        report.setTool("pitest");
        report.setLanguage("java");
        report.setProject("unknown");
        report.setBranch("main");
        report.setCommit("");
        report.setTimestamp(LocalDateTime.now());

        int total = 0, killed = 0, survived = 0, noCoverage = 0, timeout = 0, ignored = 0;
        Map<String, List<NormalizedMutationReport.MutationMutant>> byFile = new HashMap<>();
        List<NormalizedMutationReport.MutationMutant> allMutants = new ArrayList<>();

        NodeList mutationNodes = root.getElementsByTagName("mutation");
        for (int i = 0; i < mutationNodes.getLength(); i++) {
            Element m = (Element) mutationNodes.item(i);
            NormalizedMutationReport.MutationMutant mutant = parsePitestMutation(m, i);
            allMutants.add(mutant);
            byFile.computeIfAbsent(mutant.getFilePath(), k -> new ArrayList<>()).add(mutant);

            total++;
            switch (mutant.getStatus()) {
                case "KILLED" -> killed++;
                case "SURVIVED" -> survived++;
                case "NO_COVERAGE" -> noCoverage++;
                case "TIMEOUT" -> timeout++;
                // NON_VIABLE mutants failed to compile; PIT itself excludes
                // them from its mutation score, so they are counted here as
                // ignored rather than survived (a compile failure is not a
                // gap in test coverage).
                case "NON_VIABLE" -> ignored++;
                default -> ignored++;
            }
        }

        List<NormalizedMutationReport.MutationFile> files = new ArrayList<>();
        for (Map.Entry<String, List<NormalizedMutationReport.MutationMutant>> entry : byFile.entrySet()) {
            NormalizedMutationReport.MutationFile mFile = new NormalizedMutationReport.MutationFile();
            mFile.setPath(entry.getKey());
            mFile.setLanguage("java");
            mFile.setMutants(entry.getValue());

            int fTotal = entry.getValue().size();
            int fKilled = 0, fSurvived = 0, fNoCoverage = 0, fTimeout = 0, fIgnored = 0;
            for (NormalizedMutationReport.MutationMutant mutant : entry.getValue()) {
                switch (mutant.getStatus()) {
                    case "KILLED" -> fKilled++;
                    case "SURVIVED" -> fSurvived++;
                    case "NO_COVERAGE" -> fNoCoverage++;
                    case "TIMEOUT" -> fTimeout++;
                    default -> fIgnored++;
                }
            }
            int fValid = fTotal - fIgnored;
            Map<String, Object> fileMetrics = new HashMap<>();
            fileMetrics.put("score", fValid > 0 ? (double) fKilled / fValid * 100 : 0.0);
            fileMetrics.put("total", fTotal);
            fileMetrics.put("killed", fKilled);
            fileMetrics.put("survived", fSurvived);
            fileMetrics.put("noCoverage", fNoCoverage);
            fileMetrics.put("timeout", fTimeout);
            fileMetrics.put("ignored", fIgnored);
            mFile.setMetrics(fileMetrics);
            files.add(mFile);
        }

        NormalizedMutationReport.MutationSummary summary = new NormalizedMutationReport.MutationSummary();
        summary.setTotal(total);
        summary.setKilled(killed);
        summary.setSurvived(survived);
        summary.setNoCoverage(noCoverage);
        summary.setTimeout(timeout);
        summary.setIgnored(ignored);
        int relevant = total - ignored;
        summary.setScore(relevant > 0 ? (double) killed / relevant * 100 : 0.0);

        report.setSummary(summary);
        report.setFiles(files);
        report.setMutants(allMutants);

        LOG.info("Parsed pitest mutation report: score={}, total={}", summary.getScore(), total);
        return report;
    }

    private NormalizedMutationReport.MutationMutant parsePitestMutation(Element m, int index) {
        NormalizedMutationReport.MutationMutant mutant = new NormalizedMutationReport.MutationMutant();
        mutant.setId("pitest-" + index);

        String mutatedClass = textOf(m, "mutatedClass");
        String sourceFile = textOf(m, "sourceFile");
        mutant.setFilePath(classAndSourceFileToPath(mutatedClass, sourceFile));

        mutant.setMutatorName(shortMutatorName(textOf(m, "mutator")));
        mutant.setReplacement(textOf(m, "description"));
        mutant.setStatus(mapPitestStatus(m.getAttribute("status")));
        mutant.setStatusReason("");

        int line = parseIntOrZero(textOf(m, "lineNumber"));
        NormalizedMutationReport.MutationLocation loc = new NormalizedMutationReport.MutationLocation();
        NormalizedMutationReport.MutationLocation.Position start = new NormalizedMutationReport.MutationLocation.Position();
        start.setLine(line);
        NormalizedMutationReport.MutationLocation.Position end = new NormalizedMutationReport.MutationLocation.Position();
        end.setLine(line);
        loc.setStart(start);
        loc.setEnd(end);
        mutant.setLocation(loc);

        String killingTest = textOf(m, "killingTest");
        mutant.setCoveredBy(killingTest.isEmpty() ? List.of() : List.of(killingTest));
        mutant.setStaticMutant(false);

        return mutant;
    }

    private String mapPitestStatus(String pitestStatus) {
        return switch (pitestStatus == null ? "" : pitestStatus.toUpperCase()) {
            case "KILLED" -> "KILLED";
            case "SURVIVED" -> "SURVIVED";
            case "NO_COVERAGE" -> "NO_COVERAGE";
            case "TIMED_OUT" -> "TIMEOUT";
            case "NON_VIABLE" -> "NON_VIABLE";
            default -> "UNKNOWN";
        };
    }

    // PIT reports the fully-qualified class and the bare source file name
    // separately (no path); reconstructing a path from the class package
    // lets the UI's per-file table group mutants the same way it does for
    // Stryker's path-keyed files map.
    private String classAndSourceFileToPath(String mutatedClass, String sourceFile) {
        if (mutatedClass == null || mutatedClass.isEmpty()) {
            return sourceFile == null ? "unknown" : sourceFile;
        }
        int lastDot = mutatedClass.lastIndexOf('.');
        if (lastDot < 0) {
            return sourceFile == null ? mutatedClass : sourceFile;
        }
        String packagePath = mutatedClass.substring(0, lastDot).replace('.', '/');
        String fileName = (sourceFile == null || sourceFile.isEmpty())
                ? mutatedClass.substring(lastDot + 1) + ".java"
                : sourceFile;
        return packagePath + "/" + fileName;
    }

    private String shortMutatorName(String fqMutatorName) {
        if (fqMutatorName == null) {
            return "";
        }
        int lastDot = fqMutatorName.lastIndexOf('.');
        return lastDot < 0 ? fqMutatorName : fqMutatorName.substring(lastDot + 1);
    }

    private String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text;
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private Document parseXmlDocument(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Mutation reports are internal CI artifacts, not untrusted
            // user input, but disabling external entities is a cheap,
            // permanent guard against XXE regardless of source.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML mutation report", e);
        }
    }

    private NormalizedMutationReport parseMutmut(JsonNode root) {
        // TODO: Implement Mutmut parsing
        throw new UnsupportedOperationException("Mutmut parsing not yet implemented");
    }
}