package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@ServerSide
public class MutationReportParser {

  // Normalized mutant status values emitted by the parsers and consumed by
  // the summary counters and the frontend.
  private static final String STATUS_KILLED = "KILLED";
  private static final String STATUS_SURVIVED = "SURVIVED";
  private static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
  private static final String STATUS_TIMEOUT = "TIMEOUT";
  private static final String STATUS_IGNORED = "IGNORED";
  private static final String STATUS_NON_VIABLE = "NON_VIABLE";
  private static final String STATUS_UNKNOWN = "UNKNOWN";

  // Metric keys emitted in per-file metrics maps and consumed by the frontend.
  private static final String METRIC_SCORE = "score";
  private static final String METRIC_TOTAL = "total";
  private static final String METRIC_KILLED = "killed";
  private static final String METRIC_SURVIVED = "survived";
  private static final String METRIC_NO_COVERAGE = "noCoverage";
  private static final String METRIC_TIMEOUT = "timeout";
  private static final String METRIC_IGNORED = "ignored";

  private static final Logger LOG = Loggers.get(MutationReportParser.class);
  private final ObjectMapper mapper;

  public MutationReportParser() {
    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
  }

  public NormalizedMutationReport parse(File reportFile) throws IOException {
    if (!reportFile.exists()) {
      throw new IllegalArgumentException(
          "Report file does not exist: " + reportFile.getAbsolutePath());
    }

    byte[] content;
    if (reportFile.getName().endsWith(".gz")) {
      try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(reportFile.toPath()))) {
        content = gis.readAllBytes();
      }
    } else {
      content = Files.readAllBytes(reportFile.toPath());
    }

    String logicalName =
        reportFile.getName().endsWith(".gz")
            ? reportFile.getName().substring(0, reportFile.getName().length() - 3)
            : reportFile.getName();
    return parse(content, logicalName);
  }

  /**
   * Parses a report whose format is decided by content, not just the file name: PIT's mutations.xml
   * is XML, Stryker/mutmut reports are JSON. fileName is only used as a hint when content sniffing
   * is ambiguous (e.g. an empty body).
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
      LOG.info(
          "Parsed mutation report: tool={}, project={}, score={}",
          report.getTool(),
          report.getProject(),
          report.getSummary().getScore());
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
    if (CargoMutantsParser.accepts(root)) {
      return CargoMutantsParser.parse(root);
    }
    if (RubyMutantParser.accepts(root)) {
      return RubyMutantParser.parse(root);
    }
    // Mutmut format detection
    if (root.has("mutants")) {
      return parseMutmut();
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
    report.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

    NormalizedMutationReport.MutationSummary summary =
        new NormalizedMutationReport.MutationSummary();
    List<NormalizedMutationReport.MutationFile> files = new ArrayList<>();
    List<NormalizedMutationReport.MutationMutant> allMutants = new ArrayList<>();

    StrykerCounts counts = new StrykerCounts();

    JsonNode filesNode = root.path("files");
    if (filesNode.isObject()) {
      filesNode
          .fields()
          .forEachRemaining(
              entry -> {
                NormalizedMutationReport.MutationFile mFile =
                    parseStrykerFile(entry.getKey(), entry.getValue(), allMutants, counts);
                files.add(mFile);
              });
    }

    summary.setTotal(counts.total);
    summary.setKilled(counts.killed);
    summary.setSurvived(counts.survived);
    summary.setNoCoverage(counts.noCoverage);
    summary.setTimeout(counts.timeout);
    summary.setIgnored(counts.ignored);
    summary.setScore(counts.score());

    report.setSummary(summary);
    report.setFiles(files);
    report.setMutants(allMutants);

    return report;
  }

  private static final class StrykerCounts {
    int total;
    int killed;
    int survived;
    int noCoverage;
    int timeout;
    int ignored;

    double score() {
      int relevant = total - ignored;
      return relevant > 0 ? (double) killed / relevant * 100 : 0.0;
    }
  }

  private NormalizedMutationReport.MutationFile parseStrykerFile(
      String filePath,
      JsonNode fileData,
      List<NormalizedMutationReport.MutationMutant> allMutants,
      StrykerCounts counts) {
    NormalizedMutationReport.MutationFile mFile = new NormalizedMutationReport.MutationFile();
    mFile.setPath(filePath);
    mFile.setLanguage(detectLanguageFromPath(filePath));

    List<NormalizedMutationReport.MutationMutant> fileMutants = new ArrayList<>();

    JsonNode mutantsNode = fileData.path("mutants");
    StrykerCounts fileCounts = new StrykerCounts();
    if (mutantsNode.isArray()) {
      for (JsonNode mutantNode : mutantsNode) {
        NormalizedMutationReport.MutationMutant mutant = parseStrykerMutant(mutantNode, filePath);
        fileMutants.add(mutant);
        allMutants.add(mutant);

        counts.total++;
        fileCounts.total++;
        switch (mutant.getStatus()) {
          case STATUS_KILLED -> {
            counts.killed++;
            fileCounts.killed++;
          }
          case STATUS_SURVIVED -> {
            counts.survived++;
            fileCounts.survived++;
          }
          case STATUS_NO_COVERAGE -> {
            counts.noCoverage++;
            fileCounts.noCoverage++;
          }
          case STATUS_TIMEOUT -> {
            counts.timeout++;
            fileCounts.timeout++;
          }
          case STATUS_IGNORED -> {
            counts.ignored++;
            fileCounts.ignored++;
          }
          default -> {
            // Unhandled mutant status contributes to total but not specific counts
          }
        }
      }
    }

    mFile.setMutants(fileMutants);
    // Per-file breakdown so the UI's file table can show a
    // per-file score/killed/survived instead of "N/A" - the
    // frontend reads file.metrics.{score,killed,survived,
    // noCoverage}, this was previously never populated.
    int fileValid =
        fileCounts.killed + fileCounts.survived + fileCounts.noCoverage + fileCounts.timeout;
    java.util.Map<String, Object> fileMetrics = new java.util.HashMap<>();
    fileMetrics.put(
        METRIC_SCORE, fileValid > 0 ? (double) fileCounts.killed / fileValid * 100 : 0.0);
    fileMetrics.put(METRIC_TOTAL, fileCounts.total);
    fileMetrics.put(METRIC_KILLED, fileCounts.killed);
    fileMetrics.put(METRIC_SURVIVED, fileCounts.survived);
    fileMetrics.put(METRIC_NO_COVERAGE, fileCounts.noCoverage);
    fileMetrics.put(METRIC_TIMEOUT, fileCounts.timeout);
    fileMetrics.put(METRIC_IGNORED, fileCounts.ignored);
    mFile.setMetrics(fileMetrics);
    return mFile;
  }

  private NormalizedMutationReport.MutationMutant parseStrykerMutant(
      JsonNode mutantNode, String filePath) {
    NormalizedMutationReport.MutationMutant mutant = new NormalizedMutationReport.MutationMutant();
    mutant.setId(mutantNode.path("id").asText());
    mutant.setMutatorName(mutantNode.path("mutatorName").asText());
    mutant.setReplacement(mutantNode.path("replacement").asText());

    String status = mutantNode.path("status").asText();
    mutant.setStatus(mapStrykerStatus(status));
    mutant.setStatusReason(mutantNode.path("statusReason").asText(""));

    JsonNode location = mutantNode.path("location");
    if (!location.isMissingNode()) {
      NormalizedMutationReport.MutationLocation loc =
          new NormalizedMutationReport.MutationLocation();
      NormalizedMutationReport.MutationLocation.Position start =
          new NormalizedMutationReport.MutationLocation.Position();
      start.setLine(location.path("start").path("line").asInt(0));
      start.setColumn(location.path("start").path("column").asInt(0));
      NormalizedMutationReport.MutationLocation.Position end =
          new NormalizedMutationReport.MutationLocation.Position();
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
      case METRIC_KILLED -> STATUS_KILLED;
      case METRIC_SURVIVED -> STATUS_SURVIVED;
      case "nocoverage", "no_coverage" -> STATUS_NO_COVERAGE;
      case METRIC_TIMEOUT -> STATUS_TIMEOUT;
      case METRIC_IGNORED -> STATUS_IGNORED;
      case "error", "compileerror" -> "ERROR";
      default -> STATUS_UNKNOWN;
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
      throw new IOException(
          "Unknown mutation report format (expected <mutations> root, got <"
              + root.getTagName()
              + ">)");
    }

    NormalizedMutationReport report = new NormalizedMutationReport();
    report.setSchemaVersion(1);
    report.setTool("pitest");
    report.setLanguage("java");
    report.setProject("unknown");
    report.setBranch("main");
    report.setCommit("");
    report.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

    int total = 0;
    int killed = 0;
    int survived = 0;
    int noCoverage = 0;
    int timeout = 0;
    int ignored = 0;
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
        case STATUS_KILLED -> killed++;
        case STATUS_SURVIVED -> survived++;
        case STATUS_NO_COVERAGE -> noCoverage++;
        case STATUS_TIMEOUT -> timeout++;
        // NON_VIABLE mutants failed to compile; PIT itself excludes
        // them from its mutation score, so they are counted here as
        // ignored rather than survived (a compile failure is not a
        // gap in test coverage).
        case STATUS_NON_VIABLE -> ignored++;
        default -> ignored++;
      }
    }

    List<NormalizedMutationReport.MutationFile> files = new ArrayList<>();
    for (Map.Entry<String, List<NormalizedMutationReport.MutationMutant>> entry :
        byFile.entrySet()) {
      NormalizedMutationReport.MutationFile mFile = new NormalizedMutationReport.MutationFile();
      mFile.setPath(entry.getKey());
      mFile.setLanguage("java");
      mFile.setMutants(entry.getValue());

      int fTotal = entry.getValue().size();
      int fKilled = 0;
      int fSurvived = 0;
      int fNoCoverage = 0;
      int fTimeout = 0;
      int fIgnored = 0;
      for (NormalizedMutationReport.MutationMutant mutant : entry.getValue()) {
        switch (mutant.getStatus()) {
          case STATUS_KILLED -> fKilled++;
          case STATUS_SURVIVED -> fSurvived++;
          case STATUS_NO_COVERAGE -> fNoCoverage++;
          case STATUS_TIMEOUT -> fTimeout++;
          default -> fIgnored++;
        }
      }
      int fValid = fTotal - fIgnored;
      Map<String, Object> fileMetrics = new HashMap<>();
      fileMetrics.put(METRIC_SCORE, fValid > 0 ? (double) fKilled / fValid * 100 : 0.0);
      fileMetrics.put(METRIC_TOTAL, fTotal);
      fileMetrics.put(METRIC_KILLED, fKilled);
      fileMetrics.put(METRIC_SURVIVED, fSurvived);
      fileMetrics.put(METRIC_NO_COVERAGE, fNoCoverage);
      fileMetrics.put(METRIC_TIMEOUT, fTimeout);
      fileMetrics.put(METRIC_IGNORED, fIgnored);
      mFile.setMetrics(fileMetrics);
      files.add(mFile);
    }

    NormalizedMutationReport.MutationSummary summary =
        new NormalizedMutationReport.MutationSummary();
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
    NormalizedMutationReport.MutationLocation.Position start =
        new NormalizedMutationReport.MutationLocation.Position();
    start.setLine(line);
    NormalizedMutationReport.MutationLocation.Position end =
        new NormalizedMutationReport.MutationLocation.Position();
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
      case STATUS_KILLED -> STATUS_KILLED;
      case STATUS_SURVIVED -> STATUS_SURVIVED;
      case STATUS_NO_COVERAGE -> STATUS_NO_COVERAGE;
      case "TIMED_OUT" -> STATUS_TIMEOUT;
      case STATUS_NON_VIABLE -> STATUS_NON_VIABLE;
      default -> STATUS_UNKNOWN;
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
    String fileName =
        (sourceFile == null || sourceFile.isEmpty())
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

  // Mutmut's native on-disk format is intentionally not parsed: it does not
  // retain one source location per mutation, so the bundled
  // scripts/mutmut_to_stryker.py converter maps it to the Stryker shape
  // instead (see README "Supported report inputs"). Failing loudly keeps an
  // accidental raw upload from being swallowed as "unknown format".
  private NormalizedMutationReport parseMutmut() {
    throw new UnsupportedOperationException(
        "Raw mutmut reports are not supported; convert them with scripts/mutmut_to_stryker.py");
  }
}
