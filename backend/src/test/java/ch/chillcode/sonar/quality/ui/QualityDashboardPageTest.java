package ch.chillcode.sonar.quality.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;

class QualityDashboardPageTest {

  @Test
  void registersProjectQualityDashboardPage() {
    Context context = mock(Context.class);

    new QualityDashboardPage().define(context);

    verify(context).addPage(any(Page.class));
  }
}
