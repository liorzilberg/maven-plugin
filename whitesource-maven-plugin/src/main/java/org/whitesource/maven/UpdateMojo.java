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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DependencyResolutionException;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WssServiceException;

import java.util.Collection;
import java.util.Properties;

/**
 * Send updates of open source software usage information to White Source.
 *
 * <p>
 *     Further documentation for the plugin and its usage can be found in the
 *     <a href="http://docs.whitesourcesoftware.com/display/serviceDocs/Maven+plugin">online documentation</a>.
 * </p>
 *
 * @author Edo.Shor
 */
@Mojo(name = "update",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.TEST,
        aggregator = true )
public class UpdateMojo extends AgentMojo {

    /* --- Static members --- */

    public static final String POLICY_VIOLATIONS_FOUND = "Some dependencies were rejected by the organization's policies";
    public static final String NO_POLICY_VIOLATIONS = "All dependencies conform with the organization's policies";
    public static final String SENDING_FORCE_UPDATE = "Force Update Enabled, Sending Update Request to WhiteSource";
    public static final String SENDING_UPDATE = "Sending Update Request to WhiteSource";

    /* --- Members --- */

    /**
     * Optional. Set to true to check policies.
     */
    @Parameter( alias = "checkPolicies", property = Constants.CHECK_POLICIES, required = false, defaultValue = "false")
    private boolean checkPolicies;

    /* --- Constructors --- */

    public UpdateMojo() {
    }

    /* --- Concrete implementation methods --- */

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException, DependencyResolutionException {
        if (reactorProjects == null) {
            info("No Projects Found. Skipping Update");
            return;
        }

        // initialize
        init();

        // Collect OSS usage information
        Collection<AgentProjectInfo> projectInfos = extractProjectInfos();

        // send to white source
        if (projectInfos == null || projectInfos.isEmpty()) {
            info("No open source information found.");
        } else {
            sendUpdate(projectInfos);
        }
    }

    /* --- Private methods --- */

    protected void init() throws MojoFailureException {
        super.init();
        Properties systemProperties = session.getSystemProperties();
        checkPolicies = Boolean.parseBoolean(systemProperties.getProperty(Constants.CHECK_POLICIES, Boolean.toString(checkPolicies)));
    }

    private void sendUpdate(Collection<AgentProjectInfo> projectInfos) throws MojoFailureException, MojoExecutionException {
        try {
            UpdateInventoryResult updateResult;
            if (checkPolicies) {
                info("Checking Policies");
                CheckPolicyComplianceResult result = service.checkPolicyCompliance(
                        orgToken, userKey, product, productVersion, projectInfos, forceCheckAllDependencies);

                if (outputDirectory == null ||
                        (!outputDirectory.exists() && !outputDirectory.mkdirs())) {
                    warn("Output directory doesn't exist. Skipping policies check report.");
                } else {
                    generateReport(result);
                }

                boolean hasRejections = result.hasRejections();
                if (!hasRejections) {
                    info(NO_POLICY_VIOLATIONS);
                }

                if (!hasRejections || forceUpdate) {
                    info(forceUpdate ? SENDING_FORCE_UPDATE : SENDING_UPDATE);
                    updateResult = service.update(orgToken, requesterEmail, product, productVersion, projectInfos);
                    logResult(updateResult);
                }

                // check rejection last to support force update
                if (hasRejections) {
                    // this is handled in base class
                    throw new MojoExecutionException(POLICY_VIOLATIONS_FOUND);
                }
            } else {
                info(SENDING_UPDATE);
                updateResult = service.update(orgToken,userKey, requesterEmail, product, productVersion, projectInfos);
                logResult(updateResult);
            }
        } catch (WssServiceException e) {
            if (isConnectionError(e)) {
                // try to re-connect
                if (connectionRetries-- > 0) {
                    info(Constants.ATTEMPTING_TO_RECONNECT_MESSAGE);
                    try {
                        Thread.sleep(connectionRetryInterval);
                    } catch (InterruptedException e1) {
                        // do nothing
                    }
                    sendUpdate(projectInfos);
                } else {
                    throw new MojoExecutionException(Constants.ERROR_SERVICE_CONNECTION + Constants.ERROR_CONNECTION_REFUSED, e);
                }
            } else {
                throw new MojoExecutionException(Constants.ERROR_SERVICE_CONNECTION + e.getMessage(), e);
            }
        }
    }

    private void logResult(UpdateInventoryResult result) {
        info("");
        info("------------------------------------------------------------------------");
        info("Inventory Update Result for " + result.getOrganization());
        info("------------------------------------------------------------------------");

        // newly created projects
        Collection<String> createdProjects = result.getCreatedProjects();
        if (createdProjects.isEmpty()) {
//            info("No new projects found.");
        } else {
            info("");
            info("Newly Created Projects:");
            for (String projectName : createdProjects) {
                info("* " + projectName);
            }
        }

        // updated projects
        Collection<String> updatedProjects = result.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
//            info("No projects were updated.");
        } else {
            info("");
            info("Updated Projects:");
            for (String projectName : updatedProjects) {
                info("* " + projectName);
            }
        }

        // request token
        String requestToken = result.getRequestToken();
        if (StringUtils.isNotBlank(requestToken)) {
            info("");
            info("Support Token: " + requestToken);
        } else {
            info("");
        }
    }

}
