package ch.chillcode.sonar.quality.ui;

import org.sonar.api.web.UserRole;
import org.sonar.api.web.page.Page;
import org.sonar.api.web.page.PageDefinition;

public class QualityDashboardPage implements PageDefinition {

    public static final String KEY = "quality-dashboard";
    public static final String TITLE = "Quality Dashboard";
    public static final String CATEGORY = "Quality";

    @Override
    public void define(Context context) {
        context.addPage(Page.builder(KEY)
                .setTitle(TITLE)
                .setCategory(CATEGORY)
                .setIcon("dashboard")
                .setTemplate("chillcode-quality-dashboard")
                .setRequiredRole(UserRole.USER)
                .build());
    }
}