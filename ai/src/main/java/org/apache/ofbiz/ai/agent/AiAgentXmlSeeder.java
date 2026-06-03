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
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Seeds {@code AiAgentDef} and {@code AiAgentToolGrant} database rows from
 * all {@code ai/*.agent.xml} files found across installed OFBiz components.
 *
 * <p>Seeding is idempotent: if a row already exists for a given
 * {@code agentName} it is left untouched, preserving any edits made by
 * administrators since the last boot.
 */
public final class AiAgentXmlSeeder {

    private static final String MODULE = AiAgentXmlSeeder.class.getName();
    private static final int DEFAULT_MAX_ITERATIONS = 6;

    private final ToolCatalog toolCatalog;
    private final ProviderRegistry providerRegistry;

    public AiAgentXmlSeeder(ToolCatalog toolCatalog, ProviderRegistry providerRegistry) {
        this.toolCatalog = toolCatalog;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Scans all component {@code ai/} directories for {@code *.agent.xml} files
     * and inserts DB rows for any agent that does not yet have an
     * {@code AiAgentDef} record.
     *
     * @param delegator the OFBiz delegator used for DB writes
     */
    public void seed(Delegator delegator) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            Debug.logWarning("AiAgentXmlSeeder: XML security feature setup failed: "
                    + e.getMessage(), MODULE);
        }
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(false);

        int seeded = 0;
        for (ComponentConfig cc : ComponentConfig.getAllComponents()) {
            String aiDirPath = cc.rootLocation().toString() + File.separator + "ai";
            File aiDir = new File(aiDirPath);
            if (!aiDir.isDirectory()) {
                continue;
            }
            File[] agentFiles = aiDir.listFiles(
                    f -> f.isFile() && f.getName().endsWith(".agent.xml"));
            if (agentFiles == null) {
                continue;
            }
            for (File agentFile : agentFiles) {
                seeded += seedFile(agentFile, dbf, delegator, cc.rootLocation());
            }
        }
        Debug.logInfo("AiAgentXmlSeeder: seeded " + seeded + " agent(s) into AiAgentDef.", MODULE);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private int seedFile(File file, DocumentBuilderFactory dbf,
            Delegator delegator, Path componentRoot) {
        Document doc;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Debug.logWarning("AiAgentXmlSeeder: cannot parse '" + file.getAbsolutePath()
                    + "': " + e.getMessage(), MODULE);
            return 0;
        }
        Element docRoot = doc.getDocumentElement();
        if (docRoot == null) {
            return 0;
        }
        docRoot.normalize();
        NodeList agentNodes = doc.getElementsByTagName("agent");
        int count = 0;
        for (int i = 0; i < agentNodes.getLength(); i++) {
            Element agentEl = (Element) agentNodes.item(i);
            if (seedAgent(agentEl, file.getAbsolutePath(), delegator, componentRoot)) {
                count++;
            }
        }
        return count;
    }

    private boolean seedAgent(Element agentEl, String sourceFile,
            Delegator delegator, Path componentRoot) {

        String name = agentEl.getAttribute("name").trim();
        String providerName = agentEl.getAttribute("provider").trim();
        String modelOverride = agentEl.getAttribute("model").trim();
        String maxIterStr = agentEl.getAttribute("max-iterations").trim();

        if (UtilValidate.isEmpty(name) || UtilValidate.isEmpty(providerName)) {
            Debug.logWarning("AiAgentXmlSeeder: agent in '" + sourceFile
                    + "' missing name or provider; skipping.", MODULE);
            return false;
        }

        // Skip unknown providers — warn but don't fail startup
        if (providerRegistry.getProvider(providerName) == null) {
            Debug.logWarning("AiAgentXmlSeeder: agent '" + name
                    + "' references unconfigured provider '" + providerName + "'; skipping.", MODULE);
            return false;
        }

        // Idempotent — skip if already in DB
        try {
            GenericValue existing = EntityQuery.use(delegator)
                    .from("AiAgentDef").where("agentName", name).queryOne();
            if (existing != null) {
                Debug.logInfo("AiAgentXmlSeeder: agent '" + name
                        + "' already in DB; skipping.", MODULE);
                return false;
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "AiAgentXmlSeeder: DB check failed for agent '" + name + "'", MODULE);
            return false;
        }

        int maxIterations = DEFAULT_MAX_ITERATIONS;
        if (UtilValidate.isNotEmpty(maxIterStr)) {
            try {
                maxIterations = Integer.parseInt(maxIterStr);
            } catch (NumberFormatException e) {
                Debug.logWarning("AiAgentXmlSeeder: invalid max-iterations for agent '"
                        + name + "'; using default.", MODULE);
            }
        }

        String systemPrompt = resolveSystemPrompt(agentEl, name, componentRoot, sourceFile);
        if (UtilValidate.isEmpty(modelOverride)) {
            modelOverride = null;
        }

        // Collect tool allow-list
        List<String> toolNames = new ArrayList<>();
        NodeList toolNodes = agentEl.getElementsByTagName("tool");
        for (int i = 0; i < toolNodes.getLength(); i++) {
            Element toolEl = (Element) toolNodes.item(i);
            String toolName = toolEl.getAttribute("name").trim();
            if (UtilValidate.isEmpty(toolName)) {
                continue;
            }
            if (!toolCatalog.hasTool(toolName)) {
                Debug.logWarning("AiAgentXmlSeeder: agent '" + name
                        + "' grants unknown tool '" + toolName + "'; skipping tool.", MODULE);
                continue;
            }
            toolNames.add(toolName);
        }

        // Write AiAgentDef row
        try {
            GenericValue agentDef = delegator.makeValue("AiAgentDef");
            agentDef.set("agentName", name);
            agentDef.set("providerName", providerName);
            agentDef.set("modelName", modelOverride);
            agentDef.set("systemPrompt", systemPrompt);
            agentDef.set("maxIterations", (long) maxIterations);
            agentDef.set("statusId", "AI_AGENT_ACTIVE");
            delegator.create(agentDef);

            for (String toolName : toolNames) {
                GenericValue grant = delegator.makeValue("AiAgentToolGrant");
                grant.set("agentName", name);
                grant.set("toolName", toolName);
                delegator.create(grant);
            }
            Debug.logInfo("AiAgentXmlSeeder: seeded agent '" + name + "' ("
                    + toolNames.size() + " tool(s)).", MODULE);
            return true;
        } catch (GenericEntityException e) {
            Debug.logError(e, "AiAgentXmlSeeder: failed to seed agent '" + name + "'", MODULE);
            return false;
        }
    }

    private String resolveSystemPrompt(Element agentEl, String agentName,
            Path componentRoot, String sourceFile) {
        NodeList locationNodes = agentEl.getElementsByTagName("system-prompt-location");
        if (locationNodes.getLength() > 0) {
            String location = locationNodes.item(0).getTextContent();
            if (UtilValidate.isNotEmpty(location)) {
                location = location.trim();
                Path promptPath = componentRoot.resolve(Paths.get(location)).normalize();
                if (!promptPath.startsWith(componentRoot.normalize())) {
                    Debug.logWarning("AiAgentXmlSeeder: system-prompt-location path traversal "
                            + "rejected for agent '" + agentName + "'.", MODULE);
                    return "";
                }
                try {
                    return new String(Files.readAllBytes(promptPath)).trim();
                } catch (IOException e) {
                    Debug.logWarning("AiAgentXmlSeeder: cannot read system-prompt-location for '"
                            + agentName + "': " + e.getMessage(), MODULE);
                    return "";
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
}
