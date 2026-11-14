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
import java.util.List;
import java.util.Set;
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
  private static final String STATUS_ERROR = "ERROR";
  private static final String STATUS_COMPILE_ERROR = "COMPILE_ERROR";
  private static final String STATUS_RUNTIME_ERROR = "RUNTIME_ERROR";
  private static final String STATUS_UNKNOWN = "UNKNOWN";

  // PIT statuses other than KILLED and TIMED_OUT that PIT's own mutation score
  // counts as detected (org.pitest.mutationtest.DetectionStatus#isDetected).
  private static final Set<String> PITEST_OTHER_DETECTED =
      Set.of("NON_VIABLE", "MEMORY_ERROR", "RUN_ERROR", "EQUIVALENT");

  private static final String UNKNOWN = "unknown";
  private static final String LANGUAGE_PYTHON = "python";
  private static final String LANGUAGE_TYPESCRIPT = "typescript";
  private static final String LANGUAGE_JAVASCRIPT = "javascript";
  private static final String LANGUAGE_JAVA = "java";

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
    // Mutmut's native on-disk format is intentionally not parsed: it does not
    // retain one source location per mutation, so the bundled
    // scripts/mutmut_to_stryker.py converter maps it to the Stryker shape
    // instead (see README "Supported report inputs"). Failing loudly keeps an
    // accidental raw upload from being swallowed as "unknown format".
    if (root.has("mutants")) {
      throw new UnsupportedOperationException(
          "Raw mutmut reports are not supported; convert them with scripts/mutmut_to_stryker.py");
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
    report.setProject(root.path("projectName").asText(UNKNOWN));
    report.setBranch(root.path("branch").asText("main"));
    report.setCommit(root.path("commit").asText(""));
    report.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

    List<NormalizedMutationReport.MutationFile> files = new ArrayList<>();
    List<NormalizedMutationReport.MutationMutant> allMutants = new ArrayList<>();

    JsonNode filesNode = root.path("files");
    if (filesNode.isObject()) {
      filesNode
          .fields()
          .forEachRemaining(
              entry -> files.add(parseStrykerFile(entry.getKey(), entry.getValue(), allMutants)));
    }

    report.setSummary(ReportAssembler.summaryOf(allMutants));
    report.setFiles(files);
    report.setMutants(allMutants);

    return report;
  }

  private NormalizedMutationReport.MutationFile parseStrykerFile(
      String filePath,
      JsonNode fileData,
      List<NormalizedMutationReport.MutationMutant> allMutants) {
    NormalizedMutationReport.MutationFile mFile = new NormalizedMutationReport.MutationFile();
    mFile.setPath(filePath);
    mFile.setLanguage(detectLanguageFromPath(filePath));

    List<NormalizedMutationReport.MutationMutant> fileMutants = new ArrayList<>();
    JsonNode mutantsNode = fileData.path("mutants");
    if (mutantsNode.isArray()) {
      for (JsonNode mutantNode : mutantsNode) {
        fileMutants.add(parseStrykerMutant(mutantNode, filePath));
      }
    }
    allMutants.addAll(fileMutants);

    mFile.setMutants(fileMutants);
    // Per-file breakdown for the UI's file table (file.metrics.{score,...}).
    mFile.setMetrics(ReportAssembler.metricsOf(fileMutants));
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
      case "killed" -> STATUS_KILLED;
      case "survived" -> STATUS_SURVIVED;
      case "nocoverage", "no_coverage" -> STATUS_NO_COVERAGE;
      case "timeout" -> STATUS_TIMEOUT;
      case "ignored" -> STATUS_IGNORED;
      // Kept apart: a CompileError is an expected verdict, a RuntimeError means the test runner
      // crashed, which the upload guard rejects. Both stay outside the score.
      case "compileerror" -> STATUS_COMPILE_ERROR;
      case "runtimeerror" -> STATUS_RUNTIME_ERROR;
      case "error" -> STATUS_ERROR;
      default -> STATUS_UNKNOWN;
    };
  }

  private String detectLanguage(JsonNode root) {
    String projectName = root.path("projectName").asText("");
    if (projectName.toLowerCase().contains(LANGUAGE_PYTHON)
        || projectName.toLowerCase().contains("py")) {
      return LANGUAGE_PYTHON;
    }
    return LANGUAGE_TYPESCRIPT; // Default for Stryker
  }

  private String detectLanguageFromPath(String path) {
    if (path.endsWith(".py")) return LANGUAGE_PYTHON;
    if (path.endsWith(".ts") || path.endsWith(".tsx")) return LANGUAGE_TYPESCRIPT;
    if (path.endsWith(".js") || path.endsWith(".jsx")) return LANGUAGE_JAVASCRIPT;
    if (path.endsWith(".java")) return LANGUAGE_JAVA;
    return UNKNOWN;
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

    List<NormalizedMutationReport.MutationMutant> mutants = new ArrayList<>();
    NodeList mutationNodes = root.getElementsByTagName("mutation");
    for (int i = 0; i < mutationNodes.getLength(); i++) {
      mutants.add(parsePitestMutation((Element) mutationNodes.item(i), i));
    }
    NormalizedMutationReport report = ReportAssembler.assemble("pitest", LANGUAGE_JAVA, mutants);

    LOG.info(
        "Parsed pitest mutation report: score={}, total={}",
        report.getSummary().getScore(),
        report.getSummary().getTotal());
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
    // Element.getAttribute answers "" for a missing attribute, never null.
    String pitestStatus = m.getAttribute("status").toUpperCase();
    mutant.setStatus(mapPitestStatus(pitestStatus));
    // Keep PIT's own status visible where it is folded into KILLED.
    mutant.setStatusReason(PITEST_OTHER_DETECTED.contains(pitestStatus) ? pitestStatus : "");

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

  /**
   * Maps PIT's status so that the shared score equals PIT's own mutation score, detected mutations
   * over all generated ones: every status PIT counts as detected becomes KILLED (TIMED_OUT stays
   * apart as TIMEOUT, which is detected too). NOT_STARTED and STARTED never reach a final report
   * and are left out as UNKNOWN.
   */
  private String mapPitestStatus(String pitestStatus) {
    if (PITEST_OTHER_DETECTED.contains(pitestStatus)) {
      return STATUS_KILLED;
    }
    return switch (pitestStatus) {
      case STATUS_KILLED -> STATUS_KILLED;
      case STATUS_SURVIVED -> STATUS_SURVIVED;
      case STATUS_NO_COVERAGE -> STATUS_NO_COVERAGE;
      case "TIMED_OUT" -> STATUS_TIMEOUT;
      default -> STATUS_UNKNOWN;
    };
  }

  // PIT reports the fully-qualified class and the bare source file name
  // separately (no path); reconstructing a path from the class package
  // lets the UI's per-file table group mutants the same way it does for
  // Stryker's path-keyed files map. Both arguments come from textOf, so they
  // are never null; a class without a package (or no class at all) keeps the
  // bare source file name.
  private String classAndSourceFileToPath(String mutatedClass, String sourceFile) {
    int lastDot = mutatedClass.lastIndexOf('.');
    if (lastDot < 0) {
      return sourceFile;
    }
    String packagePath = mutatedClass.substring(0, lastDot).replace('.', '/');
    String fileName =
        sourceFile.isEmpty() ? mutatedClass.substring(lastDot + 1) + ".java" : sourceFile;
    return packagePath + "/" + fileName;
  }

  private String shortMutatorName(String fqMutatorName) {
    int lastDot = fqMutatorName.lastIndexOf('.');
    return lastDot < 0 ? fqMutatorName : fqMutatorName.substring(lastDot + 1);
  }

  /** The text of the first {@code tagName} child, "" when there is none; never null. */
  private String textOf(Element parent, String tagName) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      return "";
    }
    // An element's text content is never null (only documents and doctypes have none).
    return nodes.item(0).getTextContent();
  }

  private int parseIntOrZero(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
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
}
