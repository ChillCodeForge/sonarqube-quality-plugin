package ch.chillcode.sonar.quality.mutation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class NormalizedMutationReport {

  @JsonProperty("schemaVersion")
  private int schemaVersion = 1;

  @JsonProperty("tool")
  private String tool;

  @JsonProperty("language")
  private String language;

  @JsonProperty("project")
  private String project;

  @JsonProperty("branch")
  private String branch;

  @JsonProperty("commit")
  private String commit;

  @JsonProperty("timestamp")
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  private LocalDateTime timestamp;

  @JsonProperty("summary")
  private MutationSummary summary;

  @JsonProperty("files")
  private List<MutationFile> files;

  @JsonProperty("mutants")
  private List<MutationMutant> mutants;

  // Getters and setters
  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getTool() {
    return tool;
  }

  public void setTool(String tool) {
    this.tool = tool;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = project;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getCommit() {
    return commit;
  }

  public void setCommit(String commit) {
    this.commit = commit;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public MutationSummary getSummary() {
    return summary;
  }

  public void setSummary(MutationSummary summary) {
    this.summary = summary;
  }

  public List<MutationFile> getFiles() {
    return files;
  }

  public void setFiles(List<MutationFile> files) {
    this.files = files;
  }

  public List<MutationMutant> getMutants() {
    return mutants;
  }

  public void setMutants(List<MutationMutant> mutants) {
    this.mutants = mutants;
  }

  public String toJson() throws IOException {
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .writeValueAsString(this);
  }

  public static class MutationSummary {
    @JsonProperty("total")
    private int total;

    @JsonProperty("killed")
    private int killed;

    @JsonProperty("survived")
    private int survived;

    @JsonProperty("noCoverage")
    private int noCoverage;

    @JsonProperty("timeout")
    private int timeout;

    @JsonProperty("ignored")
    private int ignored;

    @JsonProperty("score")
    private double score;

    public int getTotal() {
      return total;
    }

    public void setTotal(int total) {
      this.total = total;
    }

    public int getKilled() {
      return killed;
    }

    public void setKilled(int killed) {
      this.killed = killed;
    }

    public int getSurvived() {
      return survived;
    }

    public void setSurvived(int survived) {
      this.survived = survived;
    }

    public int getNoCoverage() {
      return noCoverage;
    }

    public void setNoCoverage(int noCoverage) {
      this.noCoverage = noCoverage;
    }

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }

    public int getIgnored() {
      return ignored;
    }

    public void setIgnored(int ignored) {
      this.ignored = ignored;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }
  }

  public static class MutationFile {
    @JsonProperty("path")
    private String path;

    @JsonProperty("language")
    private String language;

    @JsonProperty("mutants")
    private List<MutationMutant> mutants;

    @JsonProperty("metrics")
    private Map<String, Object> metrics;

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String language) {
      this.language = language;
    }

    public List<MutationMutant> getMutants() {
      return mutants;
    }

    public void setMutants(List<MutationMutant> mutants) {
      this.mutants = mutants;
    }

    public Map<String, Object> getMetrics() {
      return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
      this.metrics = metrics;
    }
  }

  public static class MutationMutant {
    @JsonProperty("id")
    private String id;

    @JsonProperty("mutatorName")
    private String mutatorName;

    @JsonProperty("replacement")
    private String replacement;

    @JsonProperty("status")
    private String status; // KILLED, SURVIVED, NO_COVERAGE, TIMEOUT, IGNORED, ERROR

    @JsonProperty("statusReason")
    private String statusReason;

    @JsonProperty("location")
    private MutationLocation location;

    @JsonProperty("filePath")
    private String filePath;

    @JsonProperty("coveredBy")
    private List<String> coveredBy;

    @JsonProperty("static")
    private boolean staticMutant;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getMutatorName() {
      return mutatorName;
    }

    public void setMutatorName(String mutatorName) {
      this.mutatorName = mutatorName;
    }

    public String getReplacement() {
      return replacement;
    }

    public void setReplacement(String replacement) {
      this.replacement = replacement;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getStatusReason() {
      return statusReason;
    }

    public void setStatusReason(String statusReason) {
      this.statusReason = statusReason;
    }

    public MutationLocation getLocation() {
      return location;
    }

    public void setLocation(MutationLocation location) {
      this.location = location;
    }

    public String getFilePath() {
      return filePath;
    }

    public void setFilePath(String filePath) {
      this.filePath = filePath;
    }

    public List<String> getCoveredBy() {
      return coveredBy;
    }

    public void setCoveredBy(List<String> coveredBy) {
      this.coveredBy = coveredBy;
    }

    public boolean isStaticMutant() {
      return staticMutant;
    }

    public void setStaticMutant(boolean staticMutant) {
      this.staticMutant = staticMutant;
    }
  }

  public static class MutationLocation {
    @JsonProperty("start")
    private Position start;

    @JsonProperty("end")
    private Position end;

    public Position getStart() {
      return start;
    }

    public void setStart(Position start) {
      this.start = start;
    }

    public Position getEnd() {
      return end;
    }

    public void setEnd(Position end) {
      this.end = end;
    }

    public static class Position {
      @JsonProperty("line")
      private int line;

      @JsonProperty("column")
      private int column;

      public int getLine() {
        return line;
      }

      public void setLine(int line) {
        this.line = line;
      }

      public int getColumn() {
        return column;
      }

      public void setColumn(int column) {
        this.column = column;
      }
    }
  }
}
