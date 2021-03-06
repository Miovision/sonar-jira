/*
 * Sonar JIRA Plugin
 * Copyright (C) 2009 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.jira.reviews;

import static org.fest.assertions.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.rmi.RemoteException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RulePriority;
import org.sonar.plugins.jira.JiraConstants;
import org.sonar.plugins.jira.JiraPlugin;
import org.sonar.plugins.jira.rest.JiraSession;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;

public class JiraIssueCreatorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private JiraIssueCreator jiraIssueCreator;
    private Issue sonarIssue;
    private Settings settings;
    private RuleFinder ruleFinder;

    @Before
    public void init() throws Exception {
	sonarIssue = new DefaultIssue()
		.setKey("ABCD")
		.setMessage(
			"The Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.")
		.setSeverity("MINOR")
		.setRuleKey(RuleKey.of("squid", "CycleBetweenPackages"));

	ruleFinder = mock(RuleFinder.class);
	when(ruleFinder.findByKey(RuleKey.of("squid", "CycleBetweenPackages")))
		.thenReturn(
			org.sonar.api.rules.Rule.create().setName(
				"Avoid cycle between java packages"));

	settings = new Settings(new PropertyDefinitions(JiraIssueCreator.class,
		JiraPlugin.class));
	settings.setProperty(CoreProperties.SERVER_BASE_URL,
		"http://my.sonar.com");
	settings.setProperty(JiraConstants.SERVER_URL_PROPERTY,
		"http://my.jira.com");
	settings.setProperty(JiraConstants.USERNAME_PROPERTY, "foo");
	settings.setProperty(JiraConstants.PASSWORD_PROPERTY, "bar");
	settings.setProperty(JiraConstants.JIRA_PROJECT_KEY_PROPERTY, "TEST");

	jiraIssueCreator = new JiraIssueCreator(ruleFinder);
    }

    @Test
    public void shouldCreateSoapSession() throws Exception {
	JiraSession soapSession = jiraIssueCreator.createSession(settings);
	assertThat(soapSession.getWebServiceUrl().toString()).isEqualTo(
		"http://my.jira.com/rpc/soap/jirasoapservice-v2");
    }

    @Test
    public void shouldFailToCreateSoapSessionWithIncorrectUrl()
	    throws Exception {
	settings.removeProperty(JiraConstants.SERVER_URL_PROPERTY);
	settings.appendProperty(JiraConstants.SERVER_URL_PROPERTY, "my.server");

	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("The JIRA server URL is not a valid one: my.server/rpc/soap/jirasoapservice-v2");

	jiraIssueCreator.createSession(settings);
    }

    @Test
    public void shouldFailToCreateIssueIfCantConnect() throws Exception {
	// Given that
	JiraSession soapSession = mock(JiraSession.class);
	doThrow(RemoteException.class).when(soapSession).connect(anyString(),
		anyString());

	// Verify
	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("Impossible to connect to the JIRA server");

	jiraIssueCreator.doCreateIssue(sonarIssue, soapSession, settings);
    }

    @Test
    public void shouldFailToCreateIssueIfCantAuthenticate() throws Exception {
	// Given that
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	doThrow(RemoteAuthenticationException.class).when(jiraSoapService)
		.createIssue(anyString(), any(BasicIssue.class));

	// Verify
	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("Impossible to connect to the JIRA server (my.jira) because of invalid credentials for user foo");

	jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira",
		"foo");
    }

    @Test
    public void shouldFailToCreateIssueIfNotEnoughRights() throws Exception {
	// Given that
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	doThrow(RemotePermissionException.class).when(jiraSoapService)
		.createIssue(anyString(), any(RemoteIssue.class));

	// Verify
	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("Impossible to create the issue on the JIRA server (my.jira) because user foo does not have enough rights.");

	jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira",
		"foo");
    }

    @Test
    public void shouldFailToCreateIssueIfRemoteError() throws Exception {
	// Given that
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	doThrow(RemoteException.class).when(jiraSoapService).createIssue(
		anyString(), any(RemoteIssue.class));

	// Verify
	thrown.expect(IllegalStateException.class);
	thrown.expectMessage("Impossible to create the issue on the JIRA server (my.jira)");

	jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira",
		"foo");
    }

    @Test
    public void shouldCreateIssue() throws Exception {
	// Given that
	Issue issue = new Issue();
	JiraRestClient jiraSoapService = mock(JiraRestClient.class);
	when(jiraSoapService.createIssue(anyString(), any(Issue.class)))
		.thenReturn(issue);

	JiraSession soapSession = mock(JiraSession.class);
	when(soapSession.getJiraRestClient()).thenReturn(jiraSoapService);

	// Verify
	BasicIssue returnedIssue = jiraIssueCreator.doCreateIssue(sonarIssue,
		soapSession, settings);

	verify(soapSession).connect("foo", "bar");
	verify(soapSession).getJiraRestClient();
	// verify(soapSession).getAuthenticationToken();

	assertThat(returnedIssue).isEqualTo(issue);
    }

    @Test
    public void shouldInitRemoteIssue() throws Exception {
	// Given that
	Issue expectedIssue = new Issue();
	expectedIssue.setProject("TEST");
	expectedIssue.setType("3");
	expectedIssue.setPriority("4");
	expectedIssue
		.setSummary("Sonar Issue #ABCD - Avoid cycle between java packages");
	expectedIssue
		.setDescription("Issue detail:\n{quote}\nThe Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.\n"
			+ "{quote}\n\n\nCheck it on Sonar: http://my.sonar.com/issue/show/ABCD");

	// Verify
	IssueInput returnedIssue = jiraIssueCreator.initRemoteIssue(sonarIssue,
		settings);

	assertThat(returnedIssue.getSummary()).isEqualTo(
		expectedIssue.getSummary());
	assertThat(returnedIssue.getDescription()).isEqualTo(
		expectedIssue.getDescription());
	assertThat(returnedIssue).isEqualTo(expectedIssue);
    }

    @Test
    public void shouldInitRemoteIssueWithTaskType() throws Exception {
	// Given that
	settings.setProperty(JiraConstants.JIRA_ISSUE_TYPE_ID, "4");
	Issue expectedIssue = new Issue();
	expectedIssue.setProject("TEST");
	expectedIssue.setType("4");
	expectedIssue.setPriority("4");
	expectedIssue
		.setSummary("Sonar Issue #ABCD - Avoid cycle between java packages");
	expectedIssue
		.setDescription("Issue detail:\n{quote}\nThe Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.\n"
			+ "{quote}\n\n\nCheck it on Sonar: http://my.sonar.com/issue/show/ABCD");

	// Verify
	Issue returnedIssue = jiraIssueCreator.initRemoteIssue(sonarIssue,
		settings);

	assertThat(returnedIssue.getSummary()).isEqualTo(
		expectedIssue.getSummary());
	assertThat(returnedIssue.getDescription()).isEqualTo(
		expectedIssue.getDescription());
	assertThat(returnedIssue).isEqualTo(expectedIssue);
    }

    @Test
    public void shouldInitRemoteIssueWithComponent() throws Exception {
	// Given that
	settings.setProperty(JiraConstants.JIRA_ISSUE_COMPONENT_ID, "123");
	IssueInput expectedIssue = new IssueInput();
	expectedIssue.setProject("TEST");
	expectedIssue.setType("3");
	expectedIssue.setPriority("4");
	expectedIssue
		.setSummary("Sonar Issue #ABCD - Avoid cycle between java packages");
	expectedIssue
		.setDescription("Issue detail:\n{quote}\nThe Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.\n"
			+ "{quote}\n\n\nCheck it on Sonar: http://my.sonar.com/issue/show/ABCD");
	expectedIssue
		.setComponents(new RemoteComponent[] { new RemoteComponent(
			"123", null) });

	// Verify
	IssueInput returnedIssue = jiraIssueCreator.initRemoteIssue(sonarIssue,
		settings);

	assertThat(returnedIssue).isEqualTo(expectedIssue);
    }

    @Test
    public void shouldGiveDefaultPriority() throws Exception {
	assertThat(
		jiraIssueCreator.sonarSeverityToJiraPriorityId(
			RulePriority.BLOCKER, settings)).isEqualTo(1);
	assertThat(
		jiraIssueCreator.sonarSeverityToJiraPriorityId(
			RulePriority.CRITICAL, settings)).isEqualTo(2);
	assertThat(
		jiraIssueCreator.sonarSeverityToJiraPriorityId(
			RulePriority.MAJOR, settings)).isEqualTo(3);
	assertThat(
		jiraIssueCreator.sonarSeverityToJiraPriorityId(
			RulePriority.MINOR, settings)).isEqualTo(4);
	assertThat(
		jiraIssueCreator.sonarSeverityToJiraPriorityId(
			RulePriority.INFO, settings)).isEqualTo(5);
    }

    @Test
    public void shouldInitRemoteIssueWithoutName() throws Exception {
	// Given that
	when(ruleFinder.findByKey(RuleKey.of("squid", "CycleBetweenPackages")))
		.thenReturn(org.sonar.api.rules.Rule.create().setName(null));

	IssueInput expectedIssue = new IssueInput();
	expectedIssue.setProject("TEST");
	expectedIssue.setType("3");
	expectedIssue.setPriority("4");
	expectedIssue.setSummary("Sonar Issue #ABCD");
	expectedIssue
		.setDescription("Issue detail:\n{quote}\nThe Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.\n"
			+ "{quote}\n\n\nCheck it on Sonar: http://my.sonar.com/issue/show/ABCD");

	// Verify
	IssueInput returnedIssue = jiraIssueCreator.initRemoteIssue(sonarIssue,
		settings);

	assertThat(returnedIssue.getSummary()).isEqualTo(
		expectedIssue.getSummary());
	assertThat(returnedIssue.getDescription()).isEqualTo(
		expectedIssue.getDescription());
	assertThat(returnedIssue).isEqualTo(expectedIssue);
    }
}
