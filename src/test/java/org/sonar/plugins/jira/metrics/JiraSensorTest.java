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

import static org.fest.assertions.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Project;
import org.sonar.api.test.IsMeasure;
import org.sonar.plugins.jira.JiraConstants;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Filter;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Priority;

public class JiraSensorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private JiraSensor sensor;
    private Settings settings;

    @Before
    public void setUp() {
	settings = new Settings();
	settings.setProperty(JiraConstants.SERVER_URL_PROPERTY,
		"http://my.jira.server");
	settings.setProperty(JiraConstants.USERNAME_PROPERTY, "admin");
	settings.setProperty(JiraConstants.PASSWORD_PROPERTY, "adminPwd");
	settings.setProperty(JiraConstants.FILTER_PROPERTY, "myFilter");
	sensor = new JiraSensor(settings);
    }

    @Test
    public void testToString() throws Exception {
	assertThat(sensor.toString()).isEqualTo("JIRA issues sensor");
    }

    @Test
    public void testPresenceOfProperties() throws Exception {
	assertThat(sensor.missingMandatoryParameters()).isEqualTo(false);

	settings.removeProperty(JiraConstants.PASSWORD_PROPERTY);
	sensor = new JiraSensor(settings);
	assertThat(sensor.missingMandatoryParameters()).isEqualTo(true);

	settings.removeProperty(JiraConstants.USERNAME_PROPERTY);
	sensor = new JiraSensor(settings);
	assertThat(sensor.missingMandatoryParameters()).isEqualTo(true);

	settings.removeProperty(JiraConstants.FILTER_PROPERTY);
	sensor = new JiraSensor(settings);
	assertThat(sensor.missingMandatoryParameters()).isEqualTo(true);

	settings.removeProperty(JiraConstants.SERVER_URL_PROPERTY);
	sensor = new JiraSensor(settings);
	assertThat(sensor.missingMandatoryParameters()).isEqualTo(true);
    }

    @Test
    public void shouldExecuteOnRootProjectWithAllParams() throws Exception {
	Project project = mock(Project.class);
	when(project.isRoot()).thenReturn(true).thenReturn(false);

	assertThat(sensor.shouldExecuteOnProject(project)).isEqualTo(true);
    }

    @Test
    public void shouldNotExecuteOnNonRootProject() throws Exception {
	assertThat(sensor.shouldExecuteOnProject(mock(Project.class)))
		.isEqualTo(false);
    }

    @Test
    public void shouldNotExecuteOnRootProjectifOneParamMissing()
	    throws Exception {
	Project project = mock(Project.class);
	when(project.isRoot()).thenReturn(true).thenReturn(false);

	settings.removeProperty(JiraConstants.SERVER_URL_PROPERTY);
	sensor = new JiraSensor(settings);

	assertThat(sensor.shouldExecuteOnProject(project)).isEqualTo(false);
    }

    @Test
    public void testSaveMeasures() {
	SensorContext context = mock(SensorContext.class);
	String url = "http://localhost/jira";
	String priorityDistribution = "Critical=1";

	sensor.saveMeasures(context, url, 1, priorityDistribution);

	verify(context).saveMeasure(
		argThat(new IsMeasure(JiraMetrics.ISSUES, 1.0,
			priorityDistribution)));
	verifyNoMoreInteractions(context);
    }

    @Test
    public void shouldCollectPriorities() throws Exception {
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	ArrayList<Priority> expected = new ArrayList<Priority>();
	Priority priority1 = new Priority(new URI("http://127.0.0.1/"),
		(long) 1, "Minor", "", "", new URI("http://127.0.0.1/"));
	expected.add(priority1);
	when(jiraSoapService.getMetadataClient().getPriorities().get())
		.thenReturn(expected);

	Map<Long, String> foundPriorities = sensor
		.collectPriorities(jiraSoapService);
	assertThat(foundPriorities.size()).isEqualTo(1);
	assertThat(foundPriorities.get(1)).isEqualTo("Minor");
    }

    @Test
    public void shouldCollectIssuesByPriority() throws Exception {
	Filter filter = new Filter();
	filter.setId("1");
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	Issue issue1 = new Issue();
	issue1.setPriority("minor");
	Issue issue2 = new Issue();
	issue2.setPriority("critical");
	Issue issue3 = new Issue();
	issue3.setPriority("critical");
	when(jiraSoapService.getIssuesFromFilter(1)).thenReturn(
		new Issue[] { issue1, issue2, issue3 });

	Map<Long, Integer> foundIssues = sensor.collectIssuesByPriority(
		jiraSoapService, filter);
	assertThat(foundIssues.size()).isEqualTo(2);
	assertThat(foundIssues.get("critical")).isEqualTo(2);
	assertThat(foundIssues.get("minor")).isEqualTo(1);
    }

    @Test
    public void shouldFindFilters() throws Exception {
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	Filter filter1 = new Filter();
	filter1.setName("fooFilter");
	Filter myFilter = new Filter();
	myFilter.setName("myFilter");
	when(jiraSoapService.getFavouriteFilters()).thenReturn(
		new Filter[] { filter1, myFilter });

	Filter foundFilter = sensor.findJiraFilter(jiraSoapService);
	assertThat(foundFilter).isEqualTo(myFilter);
    }

    @Test
    public void shouldFindFiltersWithPreviousJiraVersions() throws Exception {
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	Filter myFilter = new Filter();
	myFilter.setName("myFilter");
	when(jiraSoapService.getSavedFilters()).thenReturn(
		new Filter[] { myFilter });
	when(jiraSoapService.getFavouriteFilters()).thenThrow(
		RemoteException.class);

	Filter foundFilter = sensor.findJiraFilter(jiraSoapService);
	assertThat(foundFilter).isEqualTo(myFilter);
    }

    @Test
    public void faillIfNoFilterFound() throws Exception {
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	when(jiraSoapService.getFavouriteFilters()).thenReturn(new Filter[0]);

	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("Unable to find filter 'myFilter' in JIRA");

	sensor.findJiraFilter(jiraSoapService);
    }

}
