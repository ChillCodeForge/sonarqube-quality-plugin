package ch.chillcode.sonar.quality.ui;

import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

public class QualityDashboardPage implements PageDefinition {

  @Override
  public void define(Context context) {
    context.addPage(
        Page.builder("chillcodequality/quality_dashboard")
            .setName("Quality Dashboard")
            .setScope(Page.Scope.COMPONENT)
            .setComponentQualifiers(Page.Qualifier.PROJECT)
            .build());
  }
}
