package ch.chillcode.sonar.quality.ui;

import org.sonar.api.web.UserRole;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

public class MutationTestingPage implements PageDefinition {

    public static final String KEY = "mutation-testing";
    public static final String TITLE = "Mutation Testing";
    public static final String CATEGORY = "Quality";

    @Override
    public void define(Context context) {
        context.addPage(Page.builder(KEY)
                .setTitle(TITLE)
                .setCategory(CATEGORY)
                .setIcon("bug")
                .setTemplate("chillcode-mutation-testing")
                .setRequiredRole(UserRole.USER)
                .build());
    }
}