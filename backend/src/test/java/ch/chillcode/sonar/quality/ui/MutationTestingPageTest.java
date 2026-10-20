package ch.chillcode.sonar.quality.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;

class MutationTestingPageTest {

  @Test
  void registersProjectMutationTestingPage() {
    Context context = mock(Context.class);
    ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);

    new MutationTestingPage().define(context);

    verify(context).addPage(page.capture());
    verifyNoMoreInteractions(context);
    assertEquals("chillcodequality/mutation_testing", page.getValue().getKey());
    assertEquals("Mutation Testing", page.getValue().getName());
    assertEquals(Page.Scope.COMPONENT, page.getValue().getScope());
    assertEquals(Set.of(Page.Qualifier.PROJECT), page.getValue().getComponentQualifiers());
  }
}
