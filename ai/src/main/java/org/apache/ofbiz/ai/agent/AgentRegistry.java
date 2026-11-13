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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Scans all installed OFBiz components for {@code ai/*.agent.xml} files and
 * builds an in-memory index of {@link AgentDefinition} instances.
 *
 * <p>Each agent must reference a provider that exists in the supplied
 * {@link ProviderRegistry}, and each tool in its allow-list must exist in the
 * supplied {@link ToolCatalog}.  Missing references cause an
 * {@link IllegalStateException} to surface at startup.
 */
public final class AgentRegistry {

    private static final String MODULE = AgentRegistry.class.getName();
    private static final int DEFAULT_MAX_ITERATIONS = 6;

    private final Map<String, AgentDefinition> agents;

    /**
     * Constructs the registry by scanning every OFBiz component's {@code ai/}
     * directory for files whose name ends with {@code .agent.xml}.
     *
     * @param toolCatalog      catalog used to validate tool references
     * @param providerRegistry registry used to validate provider references
     * @param dctx             the dispatch context (unused directly, retained for
     *                         symmetry with other registry constructors)
     */
    public AgentRegistry(ToolCatalog toolCatalog, ProviderRegistry providerRegistry,
            DispatchContext dctx) {
        Map<String, AgentDefinition> loaded = new LinkedHashMap<>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            Debug.logWarning("AgentRegistry: could not set XML security features: " + e.getMessage(), MODULE);
        }
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(false);

        for (ComponentConfig cc : ComponentConfig.getAllComponents()) {
            String aiDirPath = cc.rootLocation().toString() + File.separator + "ai";
            File aiDir = new File(aiDirPath);
            if (!aiDir.isDirectory()) {
                continue;
            }

            File[] agentFiles = aiDir.listFiles(
                    f -> f.isFile() && f.getName().endsWith(".agent.xml"));
            if (agentFiles == null || agentFiles.length == 0) {
                continue;
            }

            for (File agentFile : agentFiles) {
                parseAgentsFile(agentFile, dbf, toolCatalog, providerRegistry,
                        cc.rootLocation(), loaded);
            }
        }

        this.agents = Collections.unmodifiableMap(loaded);
        Debug.logInfo("AgentRegistry loaded " + this.agents.size() + " agent(s).", MODULE);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private void parseAgentsFile(File file, DocumentBuilderFactory dbf,
            ToolCatalog toolCatalog, ProviderRegistry providerRegistry,
            Path componentRoot, Map<String, AgentDefinition> loaded) {
        Document doc;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Debug.logWarning("AgentRegistry: could not parse '" + file.getAbsolutePath()
                    + "': " + e.getMessage(), MODULE);
            return;
        }

        Element docRoot = doc.getDocumentElement();
        if (docRoot == null) {
            Debug.logWarning("AgentRegistry: file '" + file.getAbsolutePath()
                    + "' has no root element, skipping.", MODULE);
            return;
        }
        docRoot.normalize();
        NodeList agentNodes = doc.getElementsByTagName("agent");

        for (int i = 0; i < agentNodes.getLength(); i++) {
            Element agentEl = (Element) agentNodes.item(i);
            parseAgent(agentEl, file.getAbsolutePath(), toolCatalog, providerRegistry,
                    componentRoot, loaded);
        }
    }

    private void parseAgent(Element agentEl, String sourceFile,
            ToolCatalog toolCatalog, ProviderRegistry providerRegistry,
            Path componentRoot, Map<String, AgentDefinition> loaded) {

        String name = agentEl.getAttribute("name").trim();
        String providerName = agentEl.getAttribute("provider").trim();
        String modelOverride = agentEl.getAttribute("model").trim();
        String maxIterStr = agentEl.getAttribute("max-iterations").trim();

        if (UtilValidate.isEmpty(name)) {
            Debug.logWarning("AgentRegistry: <agent> in '" + sourceFile
                    + "' has no name attribute; skipping.", MODULE);
            return;
        }
        if (loaded.containsKey(name)) {
            throw new IllegalStateException("AgentRegistry: duplicate agent name '"
                    + name + "' found in '" + sourceFile + "'.");
        }
        if (UtilValidate.isEmpty(providerName)) {
            throw new IllegalStateException("AgentRegistry: agent '" + name
                    + "' in '" + sourceFile + "' has no provider attribute.");
        }
        if (providerRegistry.getProvider(providerName) == null) {
            throw new IllegalStateException("AgentRegistry: agent '" + name
                    + "' references unknown provider '" + providerName + "'.");
        }

        if (UtilValidate.isEmpty(modelOverride)) {
            modelOverride = null;
        }

        int maxIterations = DEFAULT_MAX_ITERATIONS;
        if (UtilValidate.isNotEmpty(maxIterStr)) {
            try {
                maxIterations = Integer.parseInt(maxIterStr);
            } catch (NumberFormatException e) {
                Debug.logWarning("AgentRegistry: agent '" + name
                        + "' has invalid max-iterations '" + maxIterStr
                        + "'; using default " + DEFAULT_MAX_ITERATIONS + ".", MODULE);
            }
        }

        // System prompt: inline CDATA or external file
        String systemPrompt = resolveSystemPrompt(agentEl, name, componentRoot, sourceFile);

        // Tool allow-list
        List<String> toolAllowList = new ArrayList<>();
        NodeList toolNodes = agentEl.getElementsByTagName("tool");
        for (int i = 0; i < toolNodes.getLength(); i++) {
            Element toolEl = (Element) toolNodes.item(i);
            String toolName = toolEl.getAttribute("name").trim();
            if (UtilValidate.isEmpty(toolName)) {
                Debug.logWarning("AgentRegistry: agent '" + name
                        + "' has a <tool> element with no name; skipping entry.", MODULE);
                continue;
            }
            if (!toolCatalog.hasTool(toolName)) {
                throw new IllegalStateException("AgentRegistry: agent '" + name
                        + "' references unknown tool '" + toolName + "'.");
            }
            toolAllowList.add(toolName);
        }

        loaded.put(name, new AgentDefinition(
                name, providerName, modelOverride, maxIterations, systemPrompt, toolAllowList, null));
        Debug.logInfo("AgentRegistry: registered agent '" + name
                + "' (provider=" + providerName + ", tools=" + toolAllowList.size() + ").", MODULE);
    }

    /**
     * Resolves the system prompt for an agent element.  If a
     * {@code <system-prompt-location>} child element is present the file it
     * points to (relative to the component root) is read; otherwise the text
     * content of the {@code <system-prompt>} element is used.
     */
    private String resolveSystemPrompt(Element agentEl, String agentName,
            Path componentRoot, String sourceFile) {

        NodeList locationNodes = agentEl.getElementsByTagName("system-prompt-location");
        if (locationNodes.getLength() > 0) {
            String location = locationNodes.item(0).getTextContent();
            if (UtilValidate.isNotEmpty(location)) {
                location = location.trim();
                Path promptPath = componentRoot.resolve(Paths.get(location)).normalize();
                if (!promptPath.startsWith(componentRoot.normalize())) {
                    throw new IllegalStateException("AgentRegistry: system-prompt-location '"
                            + location + "' attempts to traverse outside component root");
                }
                try {
                    return new String(Files.readAllBytes(promptPath)).trim();
                } catch (IOException e) {
                    throw new IllegalStateException("AgentRegistry: agent '" + agentName
                            + "' could not read system-prompt-location '"
                            + promptPath + "': " + e.getMessage(), e);
                }
            }
        }

        NodeList promptNodes = agentEl.getElementsByTagName("system-prompt");
        if (promptNodes.getLength() > 0) {
            String text = promptNodes.item(0).getTextContent();
            return text != null ? text.trim() : "";
        }

        return "";
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns the {@link AgentDefinition} for the given name, or {@code null}
     * if no such agent is registered.
     *
     * @param name the agent name
     * @return the agent definition, or {@code null}
     */
    public AgentDefinition getAgent(String name) {
        return agents.get(name);
    }

    /**
     * Returns an unmodifiable view of all registered agent names.
     *
     * @return set of agent names
     */
    public Set<String> getAgentNames() {
        return agents.keySet();
    }
}
