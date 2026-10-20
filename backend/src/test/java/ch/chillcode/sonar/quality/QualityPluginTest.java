package ch.chillcode.sonar.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;

class QualityPluginTest {

  @Test
  void registersAllPluginExtensions() {
    Plugin.Context context = new Plugin.Context(mock(SonarRuntime.class));

    new QualityPlugin().define(context);

    List<Object> extensions = context.getExtensions();
    assertEquals(8, extensions.size());
    assertTrue(
        extensions.stream()
            .anyMatch(
                extension ->
                    extension instanceof Class<?> type
                        && type.getSimpleName().equals("MutationSensor")));
  }
}
