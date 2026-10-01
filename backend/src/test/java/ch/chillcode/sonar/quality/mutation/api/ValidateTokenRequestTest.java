package ch.chillcode.sonar.quality.mutation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidateTokenRequestTest {

  @Test
  void providesExpectedRequestProperties() {
    ValidateTokenRequest request = new ValidateTokenRequest("Bearer my-token");

    assertEquals("api/users/current", request.getPath());
    assertEquals("application/json", request.getMediaType());
    assertEquals("GET", request.getMethod());
    assertFalse(request.hasParam("any"));
    assertNull(request.getParam("any"));
    assertTrue(request.getMultiParam("any").isEmpty());
    assertTrue(request.getParameterMap().isEmpty());
    assertEquals(Optional.of("Bearer my-token"), request.getHeader("Authorization"));
    assertEquals(Optional.of("Bearer my-token"), request.getHeader("authorization"));
    assertEquals(Optional.empty(), request.getHeader("Content-Type"));
  }

  @Test
  void handlesNullAuthHeader() {
    ValidateTokenRequest request = new ValidateTokenRequest(null);
    assertEquals(Optional.empty(), request.getHeader("Authorization"));
  }
}
