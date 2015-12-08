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

package org.sonar.plugins.jira.rest;

import com.atlassian.event.api.EventPublisher;
import com.atlassian.httpclient.apache.httpcomponents.DefaultHttpClient;
import com.atlassian.httpclient.api.factory.HttpClientOptions;
import com.atlassian.httpclient.spi.ThreadLocalContextManagers;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import com.atlassian.jira.rest.client.internal.async.AtlassianHttpClientDecorator;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import com.atlassian.sal.api.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Date;

/**
 * This represents a SOAP session with JIRA including that state of being logged
 * in or not
 */
public class JiraSession {
    private static final Logger LOG = LoggerFactory
	    .getLogger(JiraSession.class);
    private JiraRestClient restClient;
    private URL webServiceUrl;

    public JiraSession(URL url) {
	this.webServiceUrl = url;
    }

    public void connect(String userName, String password)
	    throws RemoteException {
	LOG.debug("Connnecting via SOAP as : {}", userName);
	try {
		// This is super lame.  There is a bug in the JRJC that prevents us from using a JiraRestClientFactory from
		// creating this connection, and they don't seem in any rush to fix it.  Instead, we do exactly what the factory
		// does, but, also provide ApplicationProperties without the bug.  Here's the link to the issue so we can go
		// back to the factory gets fixed: https://ecosystem.atlassian.net/browse/JRJC-149
		final AuthenticationHandler authenticationHandler = new BasicHttpAuthenticationHandler(userName, password);
		final HttpClientOptions options = new HttpClientOptions();
		options.setRequestPreparer((request) -> { authenticationHandler.configure(request); });
		final DefaultHttpClient defaultHttpClient = new DefaultHttpClient(
				new MioNoOpEventPublisher(),
				new MioRestClientApplicationProperties(webServiceUrl.toURI()),
				ThreadLocalContextManagers.noop(),
				options);
		final DisposableHttpClient httpClient = new AtlassianHttpClientDecorator(defaultHttpClient) {

			@Override
			public void destroy() throws Exception {
				defaultHttpClient.destroy();
			}
		};
		restClient = new AsynchronousJiraRestClient(webServiceUrl.toURI(), httpClient);
	} catch (URISyntaxException e) {
	    throw new IllegalStateException(
		    "Exception during JiraService contruction", e);
	}
	LOG.debug("Connected");
    }

    public void disconnect() {
	restClient = null;
    }

    public JiraRestClient getJiraRestClient() {
	return restClient;
    }


	private static class MioNoOpEventPublisher implements EventPublisher {
		@Override
		public void publish(Object o) {
		}

		@Override
		public void register(Object o) {
		}

		@Override
		public void unregister(Object o) {
		}

		@Override
		public void unregisterAll() {
		}
	}

	private static class MioRestClientApplicationProperties implements ApplicationProperties {

		private final String baseUrl;

		private MioRestClientApplicationProperties(URI jiraURI) {
			this.baseUrl = jiraURI.getPath();
		}

		@Override
		public String getBaseUrl() {
			return baseUrl;
		}

		@Override
		public String getDisplayName() {
			return "Atlassian JIRA Rest Java Client";
		}

		@Override
		public String getVersion() {
			return "0.0";
		}

		@Override
		public Date getBuildDate() {
			// TODO implement using MavenUtils, JRJC-123
			throw new UnsupportedOperationException();
		}

		@Override
		public String getBuildNumber() {
			// TODO implement using MavenUtils, JRJC-123
			return String.valueOf(0);
		}

		@Override
		public File getHomeDirectory() {
			return new File(".");
		}

		@Override
		public String getPropertyValue(final String s) {
			throw new UnsupportedOperationException("Not implemented");
		}
	}
}
