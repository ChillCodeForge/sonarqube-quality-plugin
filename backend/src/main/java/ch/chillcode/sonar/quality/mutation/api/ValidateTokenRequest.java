package ch.chillcode.sonar.quality.mutation.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.server.ws.LocalConnector;

/**
 * A local call to SonarQube's own {@code api/users/current}, which answers {@code
 * "isLoggedIn":true} when the request's Authorization header carries a valid user or analysis
 * token. {@code api/authentication/validate} cannot stand in: SonarQube implements it as a servlet
 * filter, which a local call cannot reach.
 */
final class ValidateTokenRequest implements LocalConnector.LocalRequest {

  private final String authHeader;

  ValidateTokenRequest(String authHeader) {
    this.authHeader = authHeader;
  }

  @Override
  public String getPath() {
    return "api/users/current";
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
    if ("Authorization".equalsIgnoreCase(name)) {
      return Optional.ofNullable(authHeader);
    }
    return Optional.empty();
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return Map.of();
  }
}
