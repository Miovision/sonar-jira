/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.jira.metrics;

import org.sonar.api.web.AbstractRubyTemplate;
import org.sonar.api.web.RubyRailsWidget;
import org.sonar.api.web.WidgetCategory;
import org.sonar.api.web.WidgetProperties;
import org.sonar.api.web.WidgetProperty;
import org.sonar.api.web.WidgetPropertyType;
import org.sonar.api.web.WidgetScope;

@WidgetCategory({"Filters", "JIRA"})
@WidgetScope({"GLOBAL"})
@WidgetProperties({
        @WidgetProperty(key = "filter", type = WidgetPropertyType.FILTER, optional = false),
        @WidgetProperty(key = "module-key", type = WidgetPropertyType.STRING, optional = false),
        @WidgetProperty(key = "chart-title", type = WidgetPropertyType.STRING)
})
public final class JiraFilterWidget extends AbstractRubyTemplate implements RubyRailsWidget {
    public String getId() {
        return "jira_filter";
    }

    public String getTitle() {
        return "JIRA issues";
    }

    @Override
    protected String getTemplatePath() {
        return "/org/sonar/plugins/jira/metrics/jiraFilterWidget.html.erb";
    }
}
