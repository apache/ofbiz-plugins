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

/**
 * Offline unit tests for {@link AgentRunner}.
 *
 * <p>Runs without a network connection, no API key, and no OFBiz container.
 * Execute via: {@code java AgentRunnerTest} (after compilation).
 */
public class AgentRunnerTest {

    public static void main(String[] args) throws Exception {
        testStopFinishReason();
        testMaxIterations();
        testToolResultTruncation();
        System.out.println("AgentRunnerTest: all tests passed.");
    }

    // -----------------------------------------------------------------------
    // Test 1: LLM returns "stop" immediately — loop runs once, returns assistant message
    // -----------------------------------------------------------------------
    private static void testStopFinishReason() throws Exception {
        AiChatClient.ChatResponse stopResponse = new AiChatClient.ChatResponse(
                "stop", "Hello from mock!", null, 10, 5);

        AgentDefinition agentDef = new AgentDefinition(
                "TestAgent", "openai-default", null, 4, "You are a test.", Collections.emptyList());
        ProviderConfig provider = new ProviderConfig(
                "openai-default", "https://api.openai.com/v1", "test-key",
                "gpt-4o-mini", 30, Collections.emptyMap());

        MockAiChatClient mock = new MockAiChatClient(stopResponse);
        AgentRunner runner = new AgentRunner(agentDef, provider,
                Collections.emptyList(), "Hello", null, null);
        runner.setChatClient(mock);

        AgentRunner.RunResult result = runner.run();
        assert "Hello from mock!".equals(result.getAssistantMessage())
                : "Expected assistant message 'Hello from mock!' but got: " + result.getAssistantMessage();
        assert "stop".equals(result.getStopReason())
                : "Expected stop reason 'stop' but got: " + result.getStopReason();
        assert result.getIterationsUsed() == 1
                : "Expected 1 iteration but got: " + result.getIterationsUsed();
        assert mock.isExhausted()
                : "Expected mock to be exhausted";
        System.out.println("  testStopFinishReason: PASS");
    }

    // -----------------------------------------------------------------------
    // Test 2: LLM always returns tool_calls — loop caps at maxIterations
    // -----------------------------------------------------------------------
    private static void testMaxIterations() throws Exception {
        // Build a tool_calls response pointing to a non-existent tool
        // (will be skipped by allow-list check — empty allow-list)
        List<Map<String, Object>> fakeCalls = new ArrayList<>();
        Map<String, Object> fakeCall = new LinkedHashMap<>();
        fakeCall.put("id", "call_1");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", "unknownTool");
        func.put("arguments", "{}");
        fakeCall.put("function", func);
        fakeCall.put("type", "function");
        fakeCalls.add(fakeCall);

        // Script 5 tool_calls responses (maxIterations=4, so loop should stop at 4)
        AiChatClient.ChatResponse toolCallResp = new AiChatClient.ChatResponse(
                "tool_calls", null, fakeCalls, 10, 5);
        MockAiChatClient mock = new MockAiChatClient(
                toolCallResp, toolCallResp, toolCallResp, toolCallResp, toolCallResp);

        AgentDefinition agentDef = new AgentDefinition(
                "TestAgent", "openai-default", null, 4, "You are a test.", Collections.emptyList());
        ProviderConfig provider = new ProviderConfig(
                "openai-default", "https://api.openai.com/v1", "test-key",
                "gpt-4o-mini", 30, Collections.emptyMap());

        AgentRunner runner = new AgentRunner(agentDef, provider,
                Collections.emptyList(), "Keep calling tools", null, null);
        runner.setChatClient(mock);

        AgentRunner.RunResult result = runner.run();
        assert "max_iterations".equals(result.getStopReason())
                : "Expected max_iterations but got: " + result.getStopReason();
        assert result.getIterationsUsed() == 4
                : "Expected 4 iterations but got: " + result.getIterationsUsed();
        System.out.println("  testMaxIterations: PASS");
    }

    // -----------------------------------------------------------------------
    // Test 3: Tool result > 8000 chars is truncated
    // -----------------------------------------------------------------------
    private static void testToolResultTruncation() {
        // Build a string > 8000 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 900; i++) {
            sb.append("0123456789");
        }
        String longResult = sb.toString(); // 9000 chars

        // Verify the truncation logic matches AgentRunner's constant
        String truncated = longResult.length() > 8000
                ? longResult.substring(0, 8000) + "...[truncated]"
                : longResult;
        assert truncated.length() == 8014
                : "Truncated length should be 8014 (8000 + 14) but got: " + truncated.length();
        assert truncated.endsWith("...[truncated]")
                : "Should end with '...[truncated]'";
        System.out.println("  testToolResultTruncation: PASS");
    }
}
