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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Immutable value object describing one tool declared in agents.xml.
 * The {@code jsonSchema} field holds the pre-built JSON Schema ObjectNode
 * that is sent to the LLM to describe the tool's callable parameters.
 */
public final class ToolDescriptor {

    private final String name;
    private final String serviceName;
    private final String description;
    private final Set<String> hiddenParams;
    private final String requiredPermission;
    private final boolean requiresApproval;
    private final ObjectNode jsonSchema;

    public ToolDescriptor(String name, String serviceName, String description,
            Set<String> hiddenParams, String requiredPermission, boolean requiresApproval,
            ObjectNode jsonSchema) {
        this.name = name;
        this.serviceName = serviceName;
        this.description = description;
        this.hiddenParams = Collections.unmodifiableSet(
                new LinkedHashSet<>(hiddenParams != null ? hiddenParams : Collections.emptySet()));
        this.requiredPermission = requiredPermission;
        this.requiresApproval = requiresApproval;
        this.jsonSchema = jsonSchema;
    }

    public String getName() {
        return name;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getHiddenParams() {
        return hiddenParams;
    }

    /** Returns the required permission string, or {@code null} if none is required. */
    public String getRequiredPermission() {
        return requiredPermission;
    }

    /** Returns {@code true} if this tool requires human approval before execution. */
    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public ObjectNode getJsonSchema() {
        return jsonSchema;
    }
}
