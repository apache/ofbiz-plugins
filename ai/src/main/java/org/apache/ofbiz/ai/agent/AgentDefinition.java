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
package org.apache.ofbiz.ai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable value object describing one agent declared in agents.xml.
 * The {@code modelOverride} field is nullable; a {@code null} value means the
 * agent uses the default model configured on its provider.
 */
public final class AgentDefinition {

    private final String name;
    private final String providerName;
    private final String modelOverride;
    private final int maxIterations;
    private final String systemPrompt;
    private final List<String> toolAllowList;
    private final String responseSchema;

    public AgentDefinition(String name, String providerName, String modelOverride,
            int maxIterations, String systemPrompt, List<String> toolAllowList,
            String responseSchema) {
        this.name = name;
        this.providerName = providerName;
        this.modelOverride = modelOverride;
        this.maxIterations = maxIterations;
        this.systemPrompt = systemPrompt;
        this.toolAllowList = Collections.unmodifiableList(
                new ArrayList<>(toolAllowList != null ? toolAllowList : Collections.emptyList()));
        this.responseSchema = responseSchema;
    }

    public String getName() {
        return name;
    }

    public String getProviderName() {
        return providerName;
    }

    /** Returns the model override, or {@code null} to use the provider's default model. */
    public String getModelOverride() {
        return modelOverride;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<String> getToolAllowList() {
        return toolAllowList;
    }

    public String getResponseSchema() {
        return responseSchema;
    }
}
