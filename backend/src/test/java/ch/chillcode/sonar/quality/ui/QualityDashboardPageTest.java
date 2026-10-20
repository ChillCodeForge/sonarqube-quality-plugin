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

class QualityDashboardPageTest {

  @Test
  void registersProjectQualityDashboardPage() {
    Context context = mock(Context.class);
    ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);

    new QualityDashboardPage().define(context);

    verify(context).addPage(page.capture());
    verifyNoMoreInteractions(context);
    assertEquals("chillcodequality/quality_dashboard", page.getValue().getKey());
    assertEquals("Quality Dashboard", page.getValue().getName());
    assertEquals(Page.Scope.COMPONENT, page.getValue().getScope());
    assertEquals(Set.of(Page.Qualifier.PROJECT), page.getValue().getComponentQualifiers());
  }
}
