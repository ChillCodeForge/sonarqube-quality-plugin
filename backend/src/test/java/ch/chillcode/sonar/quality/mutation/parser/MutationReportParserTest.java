package ch.chillcode.sonar.quality.mutation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MutationReportParserTest {

  private final MutationReportParser parser = new MutationReportParser();

  // --- Pitest XML ---

  @Test
  void parsesPitestXmlWithMixedStatuses() throws IOException {
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations partial="false">
                    <mutation detected="true" status="KILLED" numberOfTestsRun="3">
                        <sourceFile>DocService.java</sourceFile>
                        <mutatedClass>de.doccomplete.DocService</mutatedClass>
                        <mutatedMethod>validate</mutatedMethod>
                        <methodDescription>(Ljava/lang/String;)Z</methodDescription>
                        <lineNumber>42</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator</mutator>
                        <killingTest>de.doccomplete.DocServiceTest.testValidate</killingTest>
                        <description>negated conditional</description>
                    </mutation>
                    <mutation detected="false" status="SURVIVED" numberOfTestsRun="2">
                        <sourceFile>DocService.java</sourceFile>
                        <mutatedClass>de.doccomplete.DocService</mutatedClass>
                        <mutatedMethod>compute</mutatedMethod>
                        <methodDescription>(I)I</methodDescription>
                        <lineNumber>77</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
                        <description>replaced operator</description>
                    </mutation>
                    <mutation detected="false" status="NO_COVERAGE" numberOfTestsRun="0">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>format</mutatedMethod>
                        <methodDescription>()Ljava/lang/String;</methodDescription>
                        <lineNumber>10</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator</mutator>
                        <description>replaced return value</description>
                    </mutation>
                    <mutation detected="false" status="TIMED_OUT" numberOfTestsRun="1">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>loop</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>20</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator</mutator>
                        <description>incremented</description>
                    </mutation>
                    <mutation detected="false" status="NON_VIABLE" numberOfTestsRun="0">
                        <sourceFile>Helper.java</sourceFile>
                        <mutatedClass>de.doccomplete.Helper</mutatedClass>
                        <mutatedMethod>build</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>30</lineNumber>
                        <mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator>
                        <description>removed call, does not compile</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals("pitest", report.getTool());
    assertEquals("java", report.getLanguage());
    assertEquals(5, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getKilled());
    assertEquals(1, report.getSummary().getSurvived());
    assertEquals(1, report.getSummary().getNoCoverage());
    assertEquals(1, report.getSummary().getTimeout());
    // NON_VIABLE (compile-error mutants) must not silently disappear;
    // treated as ignored so they neither count as killed nor survived.
    assertEquals(1, report.getSummary().getIgnored());

    // Score excludes ignored (NON_VIABLE) mutants from the denominator,
    // matching PIT's own "test strength" semantics: 1 killed out of
    // (5 - 1 ignored) = 4 relevant mutants = 25%.
    assertEquals(25.0, report.getSummary().getScore(), 0.001);

    assertEquals(2, report.getFiles().size());
  }

  @Test
  void mapsPitestFilePathsUsingMutatedClassPackage() throws IOException {
    String xml =
        """
                <mutations>
                    <mutation detected="true" status="KILLED" numberOfTestsRun="1">
                        <sourceFile>Widget.java</sourceFile>
                        <mutatedClass>de.doccomplete.backend.Widget</mutatedClass>
                        <mutatedMethod>render</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>5</lineNumber>
                        <mutator>SomeMutator</mutator>
                        <description>x</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(1, report.getFiles().size());
    assertEquals("de/doccomplete/backend/Widget.java", report.getFiles().get(0).getPath());
  }

  @Test
  void pitestReportWithZeroMutationsHasZeroScoreNotNaN() throws IOException {
    String xml = "<mutations></mutations>";

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(0, report.getSummary().getTotal());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
    assertFalse(Double.isNaN(report.getSummary().getScore()));
  }

  @Test
  void allNonViableMutationsYieldZeroScoreNotDivisionByZero() throws IOException {
    String xml =
        """
                <mutations>
                    <mutation detected="false" status="NON_VIABLE" numberOfTestsRun="0">
                        <sourceFile>A.java</sourceFile>
                        <mutatedClass>a.A</mutatedClass>
                        <mutatedMethod>m</mutatedMethod>
                        <methodDescription>()V</methodDescription>
                        <lineNumber>1</lineNumber>
                        <mutator>X</mutator>
                        <description>x</description>
                    </mutation>
                </mutations>
                """;

    NormalizedMutationReport report = parser.parse(xml.getBytes(), "pitest.xml");

    assertEquals(1, report.getSummary().getTotal());
    assertEquals(1, report.getSummary().getIgnored());
    assertEquals(0.0, report.getSummary().getScore(), 0.001);
    assertFalse(Double.isNaN(report.getSummary().getScore()));
  }

  // --- Format detection ---

  @Test
  void detectsPitestFormatFromMutationsRootElement() throws IOException {
    String xml = "<mutations></mutations>";
    NormalizedMutationReport report = parser.parse(xml.getBytes(), "report.xml");
    assertEquals("pitest", report.getTool());
  }

  @Test
  void rejectsUnknownXmlRootElement() {
    String xml = "<somethingElse></somethingElse>";
    assertThrows(IOException.class, () -> parser.parse(xml.getBytes(), "report.xml"));
  }

  @Test
  void mutmutStillUnsupportedAndFailsLoudlyNotSilently() {
    String json = "{\"mutants\": []}";
    IOException ex = assertThrows(IOException.class, () -> parser.parse(json));
    assertTrue(
        ex.getMessage().contains("mutmut")
            || ex.getCause() instanceof UnsupportedOperationException);
  }
}
