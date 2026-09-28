package ch.chillcode.sonar.quality.mutation.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.server.ws.LocalConnector;

/**
 * A local call to SonarQube's own {@code api/components/show} for one project.
 *
 * <p>The local connector runs it as the user making the outer request, so its status is the
 * server's own answer to whether that user may browse the project: 200 when they may, 403 or 404
 * when they may not or the project does not exist.
 */
final class ComponentShowRequest implements LocalConnector.LocalRequest {

  private static final String COMPONENT = "component";

  private final String projectKey;

  ComponentShowRequest(String projectKey) {
    this.projectKey = projectKey;
  }

  @Override
  public String getPath() {
    return "api/components/show";
  }

  @Override
  public String getMediaType() {
    return "application/json";
  }

  @Override
  public String getMethod() {
    return "GET";
  }

  @Override
  public boolean hasParam(String key) {
    return COMPONENT.equals(key);
  }

  @Override
  public String getParam(String key) {
    return COMPONENT.equals(key) ? projectKey : null;
  }

  @Override
  public List<String> getMultiParam(String key) {
    return COMPONENT.equals(key) ? List.of(projectKey) : List.of();
  }

  @Override
  public Optional<String> getHeader(String name) {
    return Optional.empty();
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return Map.of(COMPONENT, new String[] {projectKey});
  }
}
