package ch.chillcode.sonar.quality.mutation.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.server.ws.LocalConnector;

/** Common base for local GET requests dispatched via {@link LocalConnector}. */
abstract class BaseLocalGetRequest implements LocalConnector.LocalRequest {

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
    return false;
  }

  @Override
  public String getParam(String key) {
    return null;
  }

  @Override
  public List<String> getMultiParam(String key) {
    return List.of();
  }

  @Override
  public Optional<String> getHeader(String name) {
    return Optional.empty();
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return Map.of();
  }
}
