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

import java.time.Duration;
import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import org.apache.ofbiz.ai.AiFactory;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;

public class AiContainer implements Container {

    private static final String MODULE = AiContainer.class.getName();

    private String name;
    private String configFile;

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.name = name;
        this.configFile = configFile;
    }

    @Override
    public boolean start() throws ContainerException {
        String provider = UtilProperties.getPropertyValue("ai", "ai.provider", "openai");
        String model = UtilProperties.getPropertyValue("ai", "ai.model", "gpt-4o-mini");
        String apiKey = UtilProperties.getPropertyValue("ai", "ai.apiKey");
        String baseUrl = UtilProperties.getPropertyValue("ai", "ai.baseUrl", "");
        int timeoutSecs;
        try {
            timeoutSecs = Integer.parseInt(
                    UtilProperties.getPropertyValue("ai", "ai.timeout", "60"));
        } catch (NumberFormatException e) {
            timeoutSecs = 60;
        }

        if (UtilValidate.isEmpty(apiKey) || "REPLACE_WITH_YOUR_API_KEY".equals(apiKey)) {
            Debug.logError("AI plugin: ai.apiKey is not configured in ai.properties", MODULE);
            return false;
        }

        ChatModel chatModel;
        // Additional providers (anthropic, ollama native, bedrock)
        // can be added here with their respective LangChain4j builders
        switch (provider) {
            case "openai":
            default:
                var builder = OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model)
                        .timeout(Duration.ofSeconds(timeoutSecs));
                if (UtilValidate.isNotEmpty(baseUrl)) {
                    builder.baseUrl(baseUrl);
                }
                chatModel = builder.build();
        }

        AiFactory.setChatModel(chatModel);
        Debug.logInfo("AI plugin initialized: provider=" + provider + " model=" + model, MODULE);
        return true;
    }

    @Override
    public void stop() throws ContainerException {
        AiFactory.destroy();
    }

    @Override
    public String getName() {
        return name;
    }
}
