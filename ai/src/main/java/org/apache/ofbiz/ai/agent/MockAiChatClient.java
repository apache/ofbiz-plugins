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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.ofbiz.base.util.GeneralException;

/**
 * Scripted test double for {@link AiChatClient}.
 *
 * <p>Responses are consumed in FIFO order.  An {@link IllegalStateException} is
 * thrown when {@link #chat} is called after all scripted responses have been consumed.
 * Use {@link #isExhausted()} to assert that all expected calls were made.
 */
public class MockAiChatClient implements AiChatClient {

    private static final String MODULE = MockAiChatClient.class.getName();

    private final Queue<AiChatClient.ChatResponse> responses;

    /**
     * Constructs a mock with one or more scripted responses.
     *
     * @param responses responses to return in FIFO order
     */
    public MockAiChatClient(AiChatClient.ChatResponse... responses) {
        this.responses = new ArrayDeque<>(Arrays.asList(responses));
    }

    @Override
    public AiChatClient.ChatResponse chat(List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas, String model, ProviderConfig provider)
            throws GeneralException {
        AiChatClient.ChatResponse next = responses.poll();
        if (next == null) {
            throw new IllegalStateException("MockAiChatClient: script exhausted — "
                    + "more chat() calls than scripted responses");
        }
        return next;
    }

    /**
     * Returns {@code true} when all scripted responses have been consumed.
     *
     * @return {@code true} if no more scripted responses remain
     */
    public boolean isExhausted() {
        return responses.isEmpty();
    }
}
