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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Scans all installed OFBiz components for {@code ai/*.tools.xml} files and
 * builds an in-memory index of {@link ToolDescriptor} instances.
 *
 * <p>The catalog is built once at container startup; it is not reloaded while
 * the server is running.
 */
public final class ToolCatalog {

    private static final String MODULE = ToolCatalog.class.getName();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolDescriptor> tools;

    /**
     * Constructs the catalog by scanning every OFBiz component's {@code ai/}
     * directory for files whose name ends with {@code .tools.xml}.
     *
     * @param dctx the dispatch context used to validate service references
     */
    public ToolCatalog(DispatchContext dctx) {
        Map<String, ToolDescriptor> loaded = new LinkedHashMap<>();
        int componentCount = 0;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            Debug.logWarning("ToolCatalog: could not set XML security features: " + e.getMessage(), MODULE);
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

            File[] toolFiles = aiDir.listFiles(
                    f -> f.isFile() && f.getName().endsWith(".tools.xml"));
            if (toolFiles == null || toolFiles.length == 0) {
                continue;
            }

            componentCount++;
            for (File toolFile : toolFiles) {
                parseToolsFile(toolFile, dbf, dctx, loaded);
            }
        }

        this.tools = Collections.unmodifiableMap(loaded);
        Debug.logInfo("ToolCatalog loaded " + this.tools.size()
                + " tool(s) from " + componentCount + " component(s).", MODULE);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private void parseToolsFile(File file, DocumentBuilderFactory dbf,
            DispatchContext dctx, Map<String, ToolDescriptor> loaded) {
        Document doc;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(file);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Debug.logWarning("ToolCatalog: could not parse '" + file.getAbsolutePath()
                    + "': " + e.getMessage(), MODULE);
            return;
        }

        Element docRoot = doc.getDocumentElement();
        if (docRoot == null) {
            Debug.logWarning("ToolCatalog: file '" + file.getAbsolutePath()
                    + "' has no root element, skipping.", MODULE);
            return;
        }
        docRoot.normalize();
        NodeList toolNodes = doc.getElementsByTagName("tool");

        for (int i = 0; i < toolNodes.getLength(); i++) {
            Element toolEl = (Element) toolNodes.item(i);
            parseTool(toolEl, file.getAbsolutePath(), dctx, loaded);
        }
    }

    private void parseTool(Element toolEl, String sourceFile,
            DispatchContext dctx, Map<String, ToolDescriptor> loaded) {

        String name = toolEl.getAttribute("name").trim();
        String serviceName = toolEl.getAttribute("service").trim();
        String requiredPermission = toolEl.getAttribute("required-permission").trim();
        if (UtilValidate.isEmpty(requiredPermission)) {
            requiredPermission = null;
        }

        if (UtilValidate.isEmpty(name)) {
            Debug.logWarning("ToolCatalog: <tool> in '" + sourceFile
                    + "' has no name attribute; skipping.", MODULE);
            return;
        }
        if (loaded.containsKey(name)) {
            throw new IllegalStateException("ToolCatalog: duplicate tool name '"
                    + name + "' found in '" + sourceFile + "'.");
        }
        if (UtilValidate.isEmpty(serviceName)) {
            throw new IllegalStateException("ToolCatalog: tool '" + name
                    + "' in '" + sourceFile + "' has no service attribute.");
        }

        ModelService modelService;
        try {
            modelService = dctx.getModelService(serviceName);
        } catch (Exception e) {
            throw new IllegalStateException("ToolCatalog: tool '" + name
                    + "' references unknown service '" + serviceName + "'.", e);
        }

        // Description from <description> child, optionally appended with <example>
        String description = getElementText(toolEl, "description");
        String example = getElementText(toolEl, "example");
        if (UtilValidate.isNotEmpty(example)) {
            description = UtilValidate.isEmpty(description)
                    ? example
                    : description + " Example: " + example;
        }

        // Hidden params from <parameter name="x" hidden="true"/>
        Set<String> hiddenParams = new LinkedHashSet<>();
        NodeList paramNodes = toolEl.getElementsByTagName("parameter");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element paramEl = (Element) paramNodes.item(i);
            if ("true".equalsIgnoreCase(paramEl.getAttribute("hidden"))) {
                String pName = paramEl.getAttribute("name").trim();
                if (UtilValidate.isNotEmpty(pName)) {
                    hiddenParams.add(pName);
                }
            }
        }

        ObjectNode jsonSchema = buildJsonSchema(name, description, modelService, hiddenParams);

        loaded.put(name, new ToolDescriptor(
                name, serviceName, description, hiddenParams, requiredPermission, jsonSchema));
        Debug.logInfo("ToolCatalog: registered tool '" + name
                + "' -> service '" + serviceName + "'.", MODULE);
    }

    private ObjectNode buildJsonSchema(String toolName, String description,
            ModelService modelService, Set<String> hiddenParams) {

        ObjectNode root = MAPPER.createObjectNode();
        root.put("name", toolName);
        root.put("description", UtilValidate.isEmpty(description) ? toolName : description);

        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();
        List<String> requiredList = new ArrayList<>();

        for (ModelParam param : modelService.getInModelParamList()) {
            if (param.getInternal()) {
                continue;
            }
            if (hiddenParams.contains(param.getName())) {
                continue;
            }

            ObjectNode propNode = MAPPER.createObjectNode();
            propNode.put("type", ofbizTypeToJsonType(param.getType()));

            String paramDesc = param.getShortDisplayDescription();
            if (UtilValidate.isNotEmpty(paramDesc)) {
                propNode.put("description", paramDesc);
            }

            properties.set(param.getName(), propNode);

            if (!param.isOptional()) {
                requiredList.add(param.getName());
            }
        }

        inputSchema.set("properties", properties);

        if (!requiredList.isEmpty()) {
            ArrayNode reqArray = MAPPER.createArrayNode();
            for (String r : requiredList) {
                reqArray.add(r);
            }
            inputSchema.set("required", reqArray);
        }

        root.set("input_schema", inputSchema);
        return root;
    }

    /** Maps an OFBiz service parameter type to a JSON Schema type string. */
    private String ofbizTypeToJsonType(String ofbizType) {
        if (UtilValidate.isEmpty(ofbizType)) {
            return "string";
        }
        switch (ofbizType) {
            case "String":
            case "java.lang.String":
                return "string";
            case "Integer":
            case "java.lang.Integer":
            case "Long":
            case "java.lang.Long":
                return "integer";
            case "Double":
            case "java.lang.Double":
            case "Float":
            case "java.lang.Float":
            case "BigDecimal":
            case "java.math.BigDecimal":
                return "number";
            case "Boolean":
            case "java.lang.Boolean":
                return "boolean";
            default:
                return "string";
        }
    }

    /** Returns the trimmed text content of the first child element with the given tag, or empty string. */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text != null ? text.trim() : "";
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns the {@link ToolDescriptor} for the given name, or {@code null}
     * if no such tool is registered.
     *
     * @param name the tool name
     * @return the tool descriptor, or {@code null}
     */
    public ToolDescriptor getTool(String name) {
        return tools.get(name);
    }

    /**
     * Returns an unmodifiable view of all registered tools.
     *
     * @return collection of tool descriptors
     */
    public Collection<ToolDescriptor> getAllTools() {
        return tools.values();
    }

    /**
     * Returns {@code true} if a tool with the given name is registered.
     *
     * @param name the tool name
     * @return whether the tool exists
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
