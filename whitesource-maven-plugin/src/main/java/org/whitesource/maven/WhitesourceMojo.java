/**
 * Copyright (C) 2011 White Source Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.maven;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.whitesource.agent.client.ClientConstants;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.maven.utils.proxy.ProxySettings;
import org.whitesource.maven.utils.proxy.ProxySettingsProvider;
import org.whitesource.maven.utils.proxy.ProxySettingsProviderFactory;

import java.util.Properties;

/**
 * Concrete implementation holding common functionality to all goals in this plugin.
 *
 * @author Edo.Shor
 */
public abstract class WhitesourceMojo extends AbstractMojo {

    /* --- Static members --- */

    private static final String DEFAULT_CONNECTION_TIMEOUT_MINUTES = "60";
    private static final String DEFAULT_CONNECTION_RETRIES = "1";
    protected static final Integer DEFAULT_CONNECTION_DELAY_TIME = 3000;

    /* --- Members --- */

    /**
     * Indicates whether the build will continue even if there are errors.
     */
    @Parameter(alias = "failOnError", property = Constants.FAIL_ON_ERROR, required = false, defaultValue = "false")
    protected boolean failOnError;

    /**
     * Set this to 'true' to skip the maven execution.
     */
    @Parameter(alias = "skip", property = Constants.SKIP, required = false, defaultValue = "false")
    protected boolean skip;

    @Parameter(alias = "autoDetectProxySettings", property = Constants.AUTO_DETECT_PROXY_SETTINGS, defaultValue = "false")
    protected boolean autoDetectProxySettings;

    @Component
    protected MavenSession session;

    @Component
    protected MavenProject mavenProject;

    /**
     * The project dependency resolver to use.
     */
    @Component(hint = "default")
    protected ProjectDependenciesResolver projectDependenciesResolver;

    @Parameter(alias = "wssUrl", property = ClientConstants.SERVICE_URL_KEYWORD, required = false, defaultValue = ClientConstants.DEFAULT_SERVICE_URL)
    protected String wssUrl;

    @Parameter(alias = "failOnConnectionError", property = Constants.FAIL_ON_CONNECTION_ERROR, required = false, defaultValue = "true")
    protected boolean failOnConnectionError;

    @Parameter(alias = "connectionRetries", property = Constants.CONNECTION_RETRIES, required = false, defaultValue = DEFAULT_CONNECTION_RETRIES)
    protected int connectionRetries;

    @Parameter(alias = "connectionTimeoutMinutes", property = ClientConstants.CONNECTION_TIMEOUT_KEYWORD, required = false, defaultValue = DEFAULT_CONNECTION_TIMEOUT_MINUTES)
    protected int connectionTimeoutMinutes;

    protected WhitesourceService service;

    /* --- Abstract methods --- */

    public abstract void doExecute() throws MojoExecutionException, MojoFailureException, DependencyResolutionException;

    /* --- Concrete implementation methods --- */

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final long startTime = System.currentTimeMillis();

        boolean skip = Boolean.valueOf(session.getSystemProperties().getProperty(
                Constants.SKIP, String.valueOf(this.skip)));
        if (skip) {
            info("Skipping update");
        } else {
            try {
                createService();
                doExecute();
            } catch (DependencyResolutionException e) {
                handleError(e);
            } catch (MojoExecutionException e) {
                handleError(e);
            } catch (RuntimeException e) {
                throw new MojoFailureException("Unexpected error", e);
            } finally {
                if (service != null) {
                    service.shutdown();
                }
            }
        }

        debug("Total execution time is " + (System.currentTimeMillis() - startTime) + " [msec]");
    }

    /* --- Protected methods --- */

    protected void init() {
        Properties systemProperties = session.getSystemProperties();
        failOnError = Boolean.parseBoolean(systemProperties.getProperty(Constants.FAIL_ON_ERROR, Boolean.toString(failOnError)));
        autoDetectProxySettings = Boolean.parseBoolean(systemProperties.getProperty(
                Constants.AUTO_DETECT_PROXY_SETTINGS, Boolean.toString(autoDetectProxySettings)));
        connectionTimeoutMinutes = Integer.parseInt(systemProperties.getProperty(
                ClientConstants.CONNECTION_TIMEOUT_KEYWORD, String.valueOf(connectionTimeoutMinutes)));
        failOnConnectionError = Boolean.parseBoolean(systemProperties.getProperty(Constants.FAIL_ON_CONNECTION_ERROR, Boolean.toString(failOnConnectionError)));
        connectionRetries = Integer.parseInt(systemProperties.getProperty(Constants.CONNECTION_RETRIES, String.valueOf(connectionRetries)));
    }

    protected void createService() {
        String serviceUrl = session.getSystemProperties().getProperty(ClientConstants.SERVICE_URL_KEYWORD);
        if (StringUtils.isBlank(serviceUrl)) {
            serviceUrl = session.getSystemProperties().getProperty(Constants.ALTERNATIVE_SERVICE_URL_KEYWORD, wssUrl);
        }
        info("Service URL is " + serviceUrl);

        service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, Constants.PLUGIN_VERSION,
                serviceUrl, autoDetectProxySettings, connectionTimeoutMinutes);
        if (service == null) {
            info("Failed to initiate WhiteSource Service");
        } else {
            info("Initiated WhiteSource Service");
        }

        // get proxy configuration from session
        ProxySettingsProvider proxySettingsProvider = ProxySettingsProviderFactory.getProxySettingsProviderForUrl(serviceUrl, session);
        if (proxySettingsProvider.isProxyConfigured()) {
            ProxySettings proxySettings = proxySettingsProvider.getProxySettings();
            service.getClient().setProxy(proxySettings.getHostname(), proxySettings.getPort(),
                    proxySettings.getUsername(), proxySettings.getPassword());
            info("Proxy hostname: " + proxySettings.getHostname());
            info("Proxy port: " + proxySettings.getPort());
            debug("Proxy username: " + proxySettings.getUsername());
            debug("Proxy password: " + proxySettings.getPassword());
        } else {
            info("No Proxy Settings");
        }
    }

    protected void handleError(Exception error) throws MojoFailureException {
        String message = error.getMessage();
        boolean failOnError = Boolean.valueOf(session.getSystemProperties().getProperty(
                Constants.FAIL_ON_ERROR, String.valueOf(this.failOnError)));
        boolean failOnConnectionError = Boolean.valueOf(session.getSystemProperties().getProperty(
                Constants.FAIL_ON_CONNECTION_ERROR, String.valueOf(this.failOnConnectionError)));
        boolean connectionError = error.getMessage().contains(Constants.ERROR_CONNECTION_REFUSED);

        if (connectionError && failOnConnectionError) {
            debug(message, error);
            throw new MojoFailureException(message);
        } else if (!connectionError && failOnError) {
            debug(message, error);
            throw new MojoFailureException(message);
        } else {
            error(message, error);
        }
    }

    protected void debug(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content);
        }
    }

    protected void debug(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
        }
    }

    protected void info(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.info(content);
        }
    }

    protected void warn(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
            log.warn(content);
        }
    }

    protected void warn(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.warn(content);
        }
    }

    protected void error(CharSequence content, Throwable error) {
        final Log log = getLog();
        if (log != null) {
            log.debug(content, error);
            log.error(content);
        }
    }

    protected void error(CharSequence content) {
        final Log log = getLog();
        if (log != null) {
            log.error(content);
        }
    }
}
