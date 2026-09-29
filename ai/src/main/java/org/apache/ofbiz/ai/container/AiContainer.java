/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.ai.container;

import java.util.List;

import org.apache.ofbiz.ai.agent.AgentRegistry;
import org.apache.ofbiz.ai.agent.AiAgentXmlSeeder;
import org.apache.ofbiz.ai.agent.ProviderRegistry;
import org.apache.ofbiz.ai.agent.ToolCatalog;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceContainer;

/**
 * OFBiz container that bootstraps the AI agent framework at server startup.
 *
 * <p>On {@link #start()} this container:
 * <ol>
 *   <li>Obtains the default OFBiz {@link LocalDispatcher}.</li>
 *   <li>Constructs a {@link ProviderRegistry} from {@code ai.properties}.</li>
 *   <li>Constructs a {@link ToolCatalog} by scanning component {@code ai/} directories.</li>
 *   <li>Constructs an {@link AgentRegistry} by scanning component {@code ai/} directories.</li>
 * </ol>
 *
 * <p>The three registries are held as static fields so that service Groovy scripts and
 * {@link org.apache.ofbiz.ai.agent.AgentRunner} can access them via the static getters
 * without requiring a container reference.
 */
public class AiContainer implements Container {

    private static final String MODULE = AiContainer.class.getName();

    private static ToolCatalog toolCatalog;
    private static AgentRegistry agentRegistry;
    private static ProviderRegistry providerRegistry;

    private String name;

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile)
            throws ContainerException {
        this.name = name;
    }

    @Override
    public boolean start() throws ContainerException {
        Delegator delegator = DelegatorFactory.getDelegator("default");
        if (delegator == null) {
            Debug.logWarning("AiContainer: delegator not available, AI plugin disabled.", MODULE);
            return true;
        }
        LocalDispatcher dispatcher = ServiceContainer.getLocalDispatcher("default", delegator);
        if (dispatcher == null) {
            Debug.logWarning("AiContainer: dispatcher not available, AI plugin disabled.", MODULE);
            return true;
        }
        var dctx = dispatcher.getDispatchContext();
        try {
            providerRegistry = new ProviderRegistry(dctx);
            toolCatalog = new ToolCatalog(dctx);
            agentRegistry = new AgentRegistry(toolCatalog, providerRegistry, dctx);
            new AiAgentXmlSeeder(toolCatalog, providerRegistry).seed(delegator);
        } catch (Exception e) {
            throw new ContainerException("AiContainer failed to start: " + e.getMessage(), e);
        }
        Debug.logInfo("AiContainer started: providers=" + providerRegistry.getProviderNames()
                + " agents=" + agentRegistry.getAgentNames(), MODULE);
        return true;
    }

    @Override
    public void stop() throws ContainerException {
        toolCatalog = null;
        agentRegistry = null;
        providerRegistry = null;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the {@link ToolCatalog} built at startup, or {@code null} if the
     * container has not been started yet.
     *
     * @return tool catalog
     */
    public static ToolCatalog getToolCatalog() {
        return toolCatalog;
    }

    /**
     * Returns the {@link AgentRegistry} built at startup, or {@code null} if the
     * container has not been started yet.
     *
     * @return agent registry
     */
    public static AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    /**
     * Returns the {@link ProviderRegistry} built at startup, or {@code null} if
     * the container has not been started yet.
     *
     * @return provider registry
     */
    public static ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }
}
