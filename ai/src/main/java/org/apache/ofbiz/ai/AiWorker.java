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
package org.apache.ofbiz.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.service.DispatchContext;

public final class AiWorker {

    private static final String MODULE = AiWorker.class.getName();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, Supplier<JsonSchemaElement>> TYPE_BUILDERS = new HashMap<>();
    static {
        TYPE_BUILDERS.put("string", JsonStringSchema::new);
        TYPE_BUILDERS.put("number", JsonNumberSchema::new);
        TYPE_BUILDERS.put("integer", JsonIntegerSchema::new);
        TYPE_BUILDERS.put("boolean", JsonBooleanSchema::new);
    }

    private AiWorker() { }

    public static String generate(DispatchContext dctx,
            List<Map<String, Object>> messages) throws GeneralException {
        try {
            List<ChatMessage> chatMessages = toChatMessages(messages);
            var chatModel = AiFactory.getChatModel();
            var request = ChatRequest.builder().messages(chatMessages).build();
            var response = chatModel.chat(request);
            return response.aiMessage().text();
        } catch (Exception e) {
            Debug.logError(e, "AI generate failed", MODULE);
            throw new GeneralException("AI generate failed: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> generateStructured(DispatchContext dctx,
            List<Map<String, Object>> messages,
            Map<String, Object> schema) throws GeneralException {
        try {
            List<ChatMessage> chatMessages = toChatMessages(messages);
            JsonObjectSchema jsonObjectSchema = buildJsonObjectSchema(schema);
            JsonSchema jsonSchema = JsonSchema.builder()
                    .name("response").rootElement(jsonObjectSchema).build();
            ResponseFormat responseFormat = ResponseFormat.builder()
                    .type(ResponseFormatType.JSON).jsonSchema(jsonSchema).build();
            var chatModel = AiFactory.getChatModel();
            var request = ChatRequest.builder()
                    .messages(chatMessages).responseFormat(responseFormat).build();
            var response = chatModel.chat(request);
            return OBJECT_MAPPER.readValue(response.aiMessage().text(),
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            Debug.logError(e, "AI generateStructured failed", MODULE);
            throw new GeneralException("AI generateStructured failed: " + e.getMessage(), e);
        }
    }

    private static List<ChatMessage> toChatMessages(List<Map<String, Object>> messages) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            if ("system".equals(role)) {
                chatMessages.add(SystemMessage.from(content));
            } else if ("assistant".equals(role)) {
                chatMessages.add(AiMessage.from(content));
            } else {
                chatMessages.add(UserMessage.from(content));
            }
        }
        return chatMessages;
    }

    private static JsonObjectSchema buildJsonObjectSchema(Map<String, Object> schemaMap) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            builder.addProperty(entry.getKey(), buildSchemaElement(entry.getValue()));
        }
        return builder.build();
    }

    private static JsonSchemaElement buildSchemaElement(Object descriptor) {
        if (descriptor instanceof String type) {
            if ("array".equals(type)) return JsonArraySchema.builder().build();
            if ("object".equals(type)) return JsonObjectSchema.builder().build();
            return TYPE_BUILDERS.getOrDefault(type, JsonStringSchema::new).get();
        }
        if (descriptor instanceof Map) {
            Map<String, Object> descMap = UtilGenerics.cast(descriptor);
            String type = (String) descMap.get("type");
            if ("array".equals(type)) {
                JsonArraySchema.Builder ab = JsonArraySchema.builder();
                if (descMap.containsKey("items")) ab.items(buildSchemaElement(descMap.get("items")));
                return ab.build();
            }
            if ("object".equals(type)) {
                Object props = descMap.get("properties");
                if (props instanceof Map) return buildJsonObjectSchema(UtilGenerics.cast(props));
                return JsonObjectSchema.builder().build();
            }
            if (type != null) return TYPE_BUILDERS.getOrDefault(type, JsonStringSchema::new).get();
        }
        return new JsonStringSchema();
    }
}
