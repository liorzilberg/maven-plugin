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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DependencyResolutionException;
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.client.WssServiceException;

import java.util.Collection;

/**
 * Send check policies request of open source software usage information to WhiteSource.
 *
 * <p>
 *     Further documentation for the plugin and its usage can be found in the
 *     <a href="http://docs.whitesourcesoftware.com/display/serviceDocs/Maven+plugin">online documentation</a>.
 * </p>
 *
 * @author tom.shapira
 */
@Mojo(name = "checkPolicies",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.TEST,
        aggregator = true )
public class CheckPoliciesMojo extends AgentMojo {

    /* --- Constructors --- */

    public CheckPoliciesMojo() {
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
            sendCheckPolicies(projectInfos);
        }
    }

    @Override
    protected void init() {
        super.init();
    }

    /* --- Private methods --- */

    private void sendCheckPolicies(Collection<AgentProjectInfo> projectInfos) throws MojoFailureException, MojoExecutionException {
        try {
            info("Checking Policies");
            CheckPolicyComplianceResult result = service.checkPolicyCompliance(
                    orgToken, product, productVersion, projectInfos, forceCheckAllDependencies);

            if (outputDirectory == null || (!outputDirectory.exists() && !outputDirectory.mkdirs())) {
                warn("Output directory doesn't exist. Skipping policies check report.");
            } else {
                generateReport(result);
            }

            if (result.hasRejections()) {
                String msg = "Some dependencies were rejected by the organization's policies.";
                throw new MojoExecutionException(msg); // this is handled in base class
            } else {
                info("All dependencies conform with the organization's policies.");
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
                    sendCheckPolicies(projectInfos);
                } else {
                    throw new MojoExecutionException(Constants.ERROR_SERVICE_CONNECTION + Constants.ERROR_CONNECTION_REFUSED, e);
                }
            } else {
                throw new MojoExecutionException(Constants.ERROR_SERVICE_CONNECTION + e.getMessage(), e);
            }
        }
    }

}
