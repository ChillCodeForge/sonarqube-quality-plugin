package ch.chillcode.sonar.quality.ui;

import org.sonar.api.web.page.Context;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

public class MutationTestingPage implements PageDefinition {

  @Override
  public void define(Context context) {
    context.addPage(
        Page.builder("chillcodequality/mutation_testing")
            .setName("Mutation Testing")
            .setScope(Page.Scope.COMPONENT)
            .setComponentQualifiers(Page.Qualifier.PROJECT)
            .build());
  }
}
