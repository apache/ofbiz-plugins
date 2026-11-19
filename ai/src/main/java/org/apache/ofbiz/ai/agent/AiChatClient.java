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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.ofbiz.base.util.GeneralException;

/**
 * Seam interface for the LLM HTTP transport layer.
 * Implementations send a chat-completions request to the configured provider
 * and return a {@link ChatResponse}.  A stub implementation can be substituted
 * in Phase 2 unit tests without a live network connection.
 */
public interface AiChatClient {

    /**
     * Send a chat request to the LLM provider and return the parsed response.
     *
     * @param messages        ordered list of message objects; each map must contain
     *                        at minimum {@code "role"} and {@code "content"} keys
     * @param toolSchemas     list of pre-built JSON Schema {@link ObjectNode}s that
     *                        describe the tools available to the LLM in this turn;
     *                        may be empty but never {@code null}
     * @param model           model identifier to use for this request; when
     *                        {@code null} the implementation falls back to the model
     *                        declared on the {@code provider}
     * @param provider        provider configuration (base URL, API key, timeout, etc.)
     * @param responseSchema  JSON Schema string constraining the response structure;
     *                        {@code null} means free-text response (existing behaviour)
     * @return                a non-null {@link ChatResponse}
     * @throws GeneralException if the HTTP request fails or the response cannot
     *                          be parsed
     */
    ChatResponse chat(List<Map<String, Object>> messages,
                      List<ObjectNode> toolSchemas,
                      String model,
                      ProviderConfig provider,
                      String responseSchema) throws GeneralException;

    /**
     * Immutable value object returned by {@link AiChatClient#chat}.
     *
     * <p>When {@code finishReason} is {@code "stop"}, {@code content} is
     * populated and {@code toolCalls} is {@code null}.
     * When {@code finishReason} is {@code "tool_calls"}, {@code toolCalls} is
     * populated and {@code content} is {@code null}.
     */
    final class ChatResponse {

        private final String finishReason;
        private final String content;
        private final List<Map<String, Object>> toolCalls;
        private final int inputTokens;
        private final int outputTokens;
        private final Map<String, Object> structuredResult;

        public ChatResponse(String finishReason, String content,
                List<Map<String, Object>> toolCalls,
                int inputTokens, int outputTokens) {
            this(finishReason, content, toolCalls, inputTokens, outputTokens, null);
        }

        public ChatResponse(String finishReason, String content,
                List<Map<String, Object>> toolCalls,
                int inputTokens, int outputTokens,
                Map<String, Object> structuredResult) {
            this.finishReason = finishReason;
            this.content = content;
            this.toolCalls = toolCalls != null
                    ? Collections.unmodifiableList(new ArrayList<>(toolCalls))
                    : null;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.structuredResult = structuredResult != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(structuredResult))
                    : null;
        }

        /** Returns {@code "stop"} or {@code "tool_calls"}. */
        public String getFinishReason() {
            return finishReason;
        }

        /** Returns the assistant text when {@code finishReason} is {@code "stop"}; {@code null} otherwise. */
        public String getContent() {
            return content;
        }

        /** Returns the tool-call list when {@code finishReason} is {@code "tool_calls"}; {@code null} otherwise. */
        public List<Map<String, Object>> getToolCalls() {
            return toolCalls;
        }

        public int getInputTokens() {
            return inputTokens;
        }

        public int getOutputTokens() {
            return outputTokens;
        }

        /** Returns the parsed structured result when the agent ran in structured mode; {@code null} otherwise. */
        public Map<String, Object> getStructuredResult() {
            return structuredResult;
        }
    }
}
