package ch.chillcode.sonar.quality.mutation.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComponentShowRequestTest {

  private final ComponentShowRequest request = new ComponentShowRequest("my-project");

  @Test
  void asksForTheProjectAsAnUnauthenticatedJsonGet() {
    assertEquals("api/components/show", request.getPath());
    assertEquals("GET", request.getMethod());
    assertEquals("application/json", request.getMediaType());
    // No header of its own: the local connector runs it as the outer request's user.
    assertEquals(Optional.empty(), request.getHeader("Authorization"));
  }

  @Test
  void carriesOnlyTheComponentParameter() {
    assertTrue(request.hasParam("component"));
    assertEquals("my-project", request.getParam("component"));
    assertEquals(List.of("my-project"), request.getMultiParam("component"));

    Map<String, String[]> parameters = request.getParameterMap();
    assertEquals(1, parameters.size());
    assertArrayEquals(new String[] {"my-project"}, parameters.get("component"));
  }

  @Test
  void answersNothingForAnyOtherParameter() {
    assertFalse(request.hasParam("ps"));
    assertNull(request.getParam("ps"));
    assertTrue(request.getMultiParam("ps").isEmpty());
  }
}
