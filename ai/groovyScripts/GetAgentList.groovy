/*
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
 */
import org.apache.ofbiz.ai.container.AiContainer

def registry = AiContainer.getAgentRegistry()
if (!registry) {
    context.agents = []
    return
}

context.agents = registry.agentNames.collect { agentName ->
    def agentDef = registry.getAgent(agentName)
    [name         : agentDef.name,
     providerName : agentDef.providerName,
     modelDisplay : agentDef.modelOverride ?: '(default)',
     maxIterations: agentDef.maxIterations,
     toolCount    : agentDef.toolAllowList.size()]
}
