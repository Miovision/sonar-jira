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

import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

/**
 * This represents a SOAP session with JIRA including that state of being logged
 * in or not
 */
public class JiraSession {
    private static final Logger LOG = LoggerFactory
	    .getLogger(JiraSession.class);
    private JiraRestClientFactory factory;
    private JiraRestClient restClient;
    private URL webServiceUrl;

    public JiraSession(URL url) {
	this.webServiceUrl = url;
	factory = new AsynchronousJiraRestClientFactory();
	// URI jiraServerUri = new URI(url.get);
	/*
	 * try { restClient = factory.create(url.toURI());
	 * LOG.debug("SOAP Session service endpoint at " +
	 * url.toExternalForm()); } catch (Exception e) { throw new
	 * IllegalStateException(
	 * "Exception during JiraSoapService contruction", e); }
	 */
    }

    public void connect(String userName, String password)
	    throws RemoteException {
	LOG.debug("Connnecting via SOAP as : {}", userName);
	try {
	    restClient = factory.createWithBasicHttpAuthentication(
		    webServiceUrl.toURI(), userName, password);
	} catch (URISyntaxException e) {
	    throw new IllegalStateException(
		    "Exception during JiraService contruction", e);
	}
	LOG.debug("Connected");
    }

    public void disconnect() throws RemoteException {
	restClient = null;
    }

    public JiraRestClient getJiraRestClient() {
	return restClient;
    }

    public JiraRestClientFactory getJiraRestClientFactory() {
	return factory;
    }

    public URL getWebServiceUrl() {
	return webServiceUrl;
    }
}
