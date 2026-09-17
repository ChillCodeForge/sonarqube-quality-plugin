package ch.chillcode.sonar.quality.mutation.parser;

import ch.chillcode.sonar.quality.mutation.model.NormalizedMutationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
            NormalizedMutationReport report = mapper.readValue(content, NormalizedMutationReport.class);
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
            return mapper.readValue(jsonContent, NormalizedMutationReport.class);
        } catch (Exception e) {
            LOG.error("Failed to parse mutation report from string", e);
            throw new IOException("Failed to parse mutation report from string", e);
        }
    }
}