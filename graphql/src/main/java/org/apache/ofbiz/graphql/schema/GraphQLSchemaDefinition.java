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
 * under the License.aaaab
 *******************************************************************************/
package org.apache.ofbiz.graphql.schema;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLChar;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ofbiz.base.util.FileUtil;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.graphql.fetcher.BaseDataFetcher;
import org.apache.ofbiz.graphql.fetcher.EmptyDataFetcher;
import org.apache.ofbiz.graphql.fetcher.EntityDataFetcher;
import org.apache.ofbiz.graphql.fetcher.ServiceDataFetcher;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.w3c.dom.Element;
import graphql.TypeResolutionEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.StaticDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

@SuppressWarnings({ "unused", "rawtypes", "cast" })
public class GraphQLSchemaDefinition {

    private Delegator delegator;
    private LocalDispatcher dispatcher;
    private final Map<String, GraphQLInputType> schemaInputTypeMap = new HashMap<>();
    private static final Map<String, GraphQLInputType> GRAPH_QL_INPUT_TYPE_MAP = new HashMap<>();
    private static Map<String, FieldDefinition> fieldDefMap = new HashMap<>();
    private final ArrayList<String> schemaInputTypeNameList = new ArrayList<>();
    private final Map<String, String> queryRootFieldMap = new LinkedHashMap<>();
    private final Map<String, String> mutationRootFieldMap = new LinkedHashMap<>();
    private final String queryRootObjectTypeName = "QueryRootObjectType";
    private final String mutationRootObjectTypeName = "MutationRootObjectType";
    private Map<String, GraphQLTypeDefinition> allTypeDefMap = new LinkedHashMap<>();
    private static Map<String, ArgumentDefinition> argumentDefMap = new HashMap<>();
    private Map<String, ExtendObjectDefinition> extendObjectDefMap = new LinkedHashMap<>();
    private Map<String, InterfaceTypeDefinition> interfaceTypeDefMap = new LinkedHashMap<>();
    private static Map<String, Element> interfaceFetcherNodeMap = new HashMap<>();

    // Type Maps
    private static final Map<String, GraphQLInterfaceType> GRAPH_QL_INTERFACE_TYPE_MAP = new HashMap<>();
    private static final Map<String, GraphQLOutputType> GRAPH_QL_OUTPUT_TYPE_MAP = new HashMap<>();
    private static final Map<String, GraphQLObjectType> GRAPH_QL_OBJECT_TYPE_MAP = new HashMap<>();
    private static final Map<String, GraphQLFieldDefinition> GRAPH_QL_FIELD_MAP = new HashMap<>();
    private static final Map<String, GraphQLInputObjectType> GRAPH_QL_INPUT_OBJECT_TYPE_MAP = new HashMap<>();
    private static final Map<String, GraphQLInputObjectField> GRAPH_QL_INPUT_OBJECT_FIELD_MAP = new HashMap<>();
    private static final Map<String, GraphQLArgument> GRAPH_QL_ARGUMENT_MAP = new HashMap<>();
    private static final Map<String, GraphQLArgument> GRAPH_QL_DIRECTIVE_ARGUMENT_MAP = new LinkedHashMap<>();
    private static final Map<String, GraphQLTypeReference> GRAPH_QL_TYPE_REFERENCE_MAP = new HashMap<>();

    private LinkedList<GraphQLTypeDefinition> allTypeDefSortedList = new LinkedList<>();
    private Map<String, GraphQLTypeDefinition> requiredTypeDefMap = new LinkedHashMap<>();

    private static Set<String> interfaceResolverTypeSet = new HashSet<>();

    private static final String KEY_SPLITTER = "__";
    private static final String NON_NULL_SUFFIX = "_1";
    private static final String IS_LIST_SUFFIX = "_2";
    private static final String LIST_ITEM_NON_NULL_SUFFIX = "_3";
    private static final String REQUIRED_SUFFIX = "_a";

    private static GraphQLObjectType pageInfoType;
    private static GraphQLInputObjectType paginationInputType;
    private static GraphQLInputObjectType operationInputType;
    private static GraphQLInputObjectType dateRangeInputType;
    private static GraphQLFieldDefinition cursorField;
    private static GraphQLFieldDefinition clientMutationIdField;
    private static GraphQLArgument paginationArgument;
    private static GraphQLArgument ifArgument;
    private static GraphQLInputObjectField clientMutationIdInputField;
    private static GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

    static {
        createPredefinedGraphQLTypes();
    }

    private static void createPredefinedGraphQLTypes() {
        // Add default GraphQLScalarType
        for (Map.Entry<String, GraphQLScalarType> entry : GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.entrySet()) {
            GRAPH_QL_INPUT_TYPE_MAP.put(entry.getKey(), entry.getValue());
            GRAPH_QL_OUTPUT_TYPE_MAP.put(entry.getKey(), entry.getValue());
        }

        GraphQLFieldDefinition.Builder cursorFieldBuilder = GraphQLFieldDefinition.newFieldDefinition().name("cursor")
                .type(GraphQLString);
        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            cursorFieldBuilder.argument(entry.getValue());
        }
        cursorField = cursorFieldBuilder.build();
        GRAPH_QL_FIELD_MAP.put("cursor" + KEY_SPLITTER + "String", cursorField);

        GraphQLFieldDefinition.Builder clientMutationIdFieldBuilder = GraphQLFieldDefinition.newFieldDefinition()
                .name("clientMutationId").type(GraphQLString);
        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            clientMutationIdFieldBuilder.argument(entry.getValue());
        }
        clientMutationIdField = clientMutationIdFieldBuilder.build();
        GRAPH_QL_FIELD_MAP.put("clientMutationId" + KEY_SPLITTER + "String", clientMutationIdField);

        ifArgument = GraphQLArgument.newArgument().name("if").type(GraphQLBoolean).description("Directive @if").build();
        GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.put("if", ifArgument);

        // Predefined GraphQLObject
        pageInfoType = GraphQLObjectType.newObject().name("GraphQLPageInfo")
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "pageIndex", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "pageSize", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "totalCount", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "pageMaxIndex", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "pageRangeLow", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "pageRangeHigh", GraphQLInt, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "hasPreviousPage", GraphQLBoolean,
                        "hasPreviousPage will be false if the client is not paginating with last, or "
                                + "if the client is paginating with last, and the server has determined that the client has reached the end of"
                                + " the set of edges defined by their cursors."))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "hasNextPage", GraphQLBoolean,
                        "hasNextPage will be false if the client is not paginating with first, or "
                                + "if the client is paginating with first, and the server has determined that the client has reached the end of"
                                + " the set of edges defined by their cursors"))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "startCursor", GraphQLString, ""))
                .field(getGraphQLFieldWithNoArgs("GraphQLPageInfo", "endCursor", GraphQLString, "")).build();
        GRAPH_QL_OBJECT_TYPE_MAP.put("GraphQLPageInfo", pageInfoType);
        GRAPH_QL_OUTPUT_TYPE_MAP.put("GraphQLPageInfo", pageInfoType);

        // Predefined GraphQLInputObject
        paginationInputType = GraphQLInputObjectType.newInputObject().name("PaginationInputType")
                .field(createPredefinedInputField("pageIndex", GraphQLInt, 0, "Page index for pagination, default 0"))
                .field(createPredefinedInputField("pageSize", GraphQLInt, 20, "Page size for pagination, default 20"))
                .field(createPredefinedInputField("pageNoLimit", GraphQLBoolean, false,
                        "Page no limit for pagination, default false"))
                .field(createPredefinedInputField("orderByField", GraphQLString, null,
                        "OrderBy field for pagination. \ne.g. \n" + "productName \n" + "productName,statusId \n"
                                + "-statusId,productName"))
                .field(createPredefinedInputField("first", GraphQLInt, 20,
                        "Forward pagination argument takes a non‐negative integer, default 20"))
                .field(createPredefinedInputField("after", GraphQLString, null,
                        "Forward pagination argument takes the cursor, default null"))
                .field(createPredefinedInputField("last", GraphQLInt, 20,
                        "Backward pagination argument takes a non‐negative integer, default 20"))
                .field(createPredefinedInputField("before", GraphQLString, null,
                        "Backward pagination argument takes the cursor, default null"))
                .field(createPredefinedInputField("type", GraphQLString, null,
                        "Pagination type either 'offset' or 'cursor'"))
                .build();
        GRAPH_QL_INPUT_TYPE_MAP.put("PaginationInputType", paginationInputType);

        operationInputType = GraphQLInputObjectType.newInputObject().name("OperationInputType")
                .field(createPredefinedInputField("op", GraphQLString, null,
                        "Operation on field, one of [ equals | like | contains | begins | empty | in ]"))
                .field(createPredefinedInputField("value", GraphQLString, null, "Argument value"))
                .field(createPredefinedInputField("not", GraphQLString, null,
                        "Not operation, one of [ Y | true ] represents true"))
                .field(createPredefinedInputField("ic", GraphQLString, null,
                        "Case insensitive, one of [ Y | true ] represents true"))
                .build();
        GRAPH_QL_INPUT_TYPE_MAP.put("OperationInputType", operationInputType);

        dateRangeInputType = GraphQLInputObjectType.newInputObject().name("DateRangeInputType")
                .field(createPredefinedInputField("period", GraphQLChar, null, ""))
                .field(createPredefinedInputField("poffset", GraphQLChar, null, ""))
                .field(createPredefinedInputField("from", GraphQLChar, null, ""))
                .field(createPredefinedInputField("thru", GraphQLChar, null, "")).build();
        GRAPH_QL_INPUT_TYPE_MAP.put("DateRangeInputType", dateRangeInputType);

        paginationArgument = GraphQLArgument.newArgument().name("pagination").type(paginationInputType)
                .description("pagination").build();
        GRAPH_QL_ARGUMENT_MAP.put(getArgumentKey("pagination", paginationInputType.getName()), paginationArgument);

        clientMutationIdInputField = GraphQLInputObjectField.newInputObjectField().name("clientMutationId")
                .type(GraphQLString).description("A unique identifier for the client performing the mutation.").build();
        GRAPH_QL_INPUT_OBJECT_FIELD_MAP.put("clientMutationId", clientMutationIdInputField);
    }

    private static GraphQLInputObjectField createPredefinedInputField(String name, GraphQLInputType type,
            Object defaultValue, String description) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField().name(name)
                .type(type).defaultValue(defaultValue).description(description);
        return fieldBuilder.build();
    }

    static class TreeNode<T> {
        private T data;
        private final List<TreeNode<T>> children = new LinkedList<TreeNode<T>>();

        TreeNode(T data) {
            this.data = data;
        }
    }

    static class EnumValue {
        private String name;
        private String value;
        private String description;
        private String depreciationReason;

        EnumValue(Element node) {
            this.name = node.getAttribute("node");
            this.value = node.getAttribute("value");
            List<? extends Element> elements = UtilXml.childElementList(node);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    this.description = childNode.getTextContent();
                    break;
                case "depreciation-reason":
                    this.depreciationReason = childNode.getTextContent();
                    break;
                }
            }
        }
    }

    static class EnumTypeDefinition extends GraphQLTypeDefinition {
        private List<EnumValue> valueList = new LinkedList<>();

        EnumTypeDefinition(Element node) {
            setName(node.getAttribute("name"));
            setType("enum");
            List<? extends Element> elements = UtilXml.childElementList(node);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    setDescription(childNode.getTextContent());
                    break;
                case "enum-value":
                    valueList.add(new EnumValue(childNode));
                    break;
                }
            }
        }

        @Override
        List<String> getDependentTypes() {
            return new LinkedList<String>();
        }
    }

    static class ExtendObjectDefinition {
        private final Delegator delegator;
        private final LocalDispatcher dispatcher;
        private List<Element> extendObjectNodeList = new ArrayList<Element>();
        private String name;
        private String resolverField;

        private List<String> interfaceList = new LinkedList<>();
        private Map<String, FieldDefinition> fieldDefMap = new LinkedHashMap<>();
        private List<String> excludeFields = new ArrayList<>();
        private Map<String, String> resolverMap = new LinkedHashMap<>();

        private boolean convertToInterface = false;

        ExtendObjectDefinition(Element node, Delegator delegator, LocalDispatcher dispatcher) {
            this.delegator = delegator;
            this.dispatcher = dispatcher;
            this.extendObjectNodeList.add(node);
            this.name = node.getAttribute("name");
            List<? extends Element> elements = UtilXml.childElementList(node);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "interface":
                    interfaceList.add(childNode.getAttribute("name"));
                    break;
                case "field":
                    fieldDefMap.put(childNode.getAttribute("name"), new FieldDefinition(this.name, delegator, dispatcher, childNode));
                    break;
                case "exclude-field":
                    excludeFields.add(childNode.getAttribute("name"));
                    break;
                case "convert-to-interface":
                    convertToInterface = true;
                    resolverField = childNode.getAttribute("resolver-field");
                    break;
                }
            }
        }

        ExtendObjectDefinition merge(ExtendObjectDefinition other) {
            extendObjectNodeList.addAll(other.extendObjectNodeList);
            resolverField = resolverField != null ? resolverField : other.resolverField;
            interfaceList.addAll(other.interfaceList);
            fieldDefMap.putAll(other.fieldDefMap);
            excludeFields.addAll(other.excludeFields);
            resolverMap.putAll(other.resolverMap);
            convertToInterface = convertToInterface ? convertToInterface : other.convertToInterface;
            return this;
        }

    }

    static class UnionTypeDefinition extends GraphQLTypeDefinition {
        private String typeResolver;
        private List<String> typeList = new LinkedList<>();

        UnionTypeDefinition(Element node) {
            setName(node.getAttribute("name"));
            setType("union");
            this.typeResolver = node.getAttribute("type-resolver");
            List<? extends Element> elements = UtilXml.childElementList(node);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    setDescription(childNode.getTextContent());
                    break;
                case "type":
                    typeList.add(childNode.getAttribute("name"));
                    break;
                }
            }
        }

        @Override
        List<String> getDependentTypes() {
            return typeList;
        }
    }

    public GraphQLSchemaDefinition(Delegator delegator, LocalDispatcher dispatcher, Map<String, Element> schemaMap) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        GraphQLSchemaUtil.createObjectTypeNodeForAllEntities(delegator, dispatcher, allTypeDefMap);
        schemaMap.forEach((k, v) -> {
            Element schemaElement = v;
            List<? extends Element> elements = UtilXml.childElementList(schemaElement, "interface-fetcher");
            for (Element interfaceFetcherNode : elements) {
                interfaceFetcherNodeMap.put(interfaceFetcherNode.getAttribute("name"), interfaceFetcherNode);
            }
        });

        schemaMap.forEach((k, v) -> {
            Element schemaElement = v;
            String rootFieldName = schemaElement.getAttribute("name");
            String rootQueryTypeName = schemaElement.getAttribute("query");
            String rootMutationTypeName = schemaElement.getAttribute("mutation");
            if (!rootQueryTypeName.isEmpty()) {
                queryRootFieldMap.put(rootFieldName, rootQueryTypeName);
            }
            if (!rootMutationTypeName.isEmpty()) {
                mutationRootFieldMap.put(rootFieldName, rootMutationTypeName);
            }

            List<? extends Element> elements = UtilXml.childElementList(schemaElement);
            for (Element element : elements) {
                String nodeName = element.getNodeName();
                switch (nodeName) {
                case "input-type":
                    schemaInputTypeNameList.add(element.getAttribute("name"));
                    break;
                case "interface":
                    InterfaceTypeDefinition interfaceTypeDef = new InterfaceTypeDefinition(element, delegator,
                            dispatcher);
                    allTypeDefMap.put(element.getAttribute("name"), interfaceTypeDef);
                    interfaceTypeDefMap.put(element.getAttribute("name"), interfaceTypeDef);
                    break;
                case "object":
                    allTypeDefMap.put(element.getAttribute("name"),
                            new ObjectTypeDefinition(element, delegator, dispatcher));
                    break;
                case "union":
                    allTypeDefMap.put(element.getAttribute("name"), new UnionTypeDefinition(element));
                    break;
                case "enum":
                    allTypeDefMap.put(element.getAttribute("name"), new EnumTypeDefinition(element));
                    break;
                case "extend-object":
                    extendObjectDefMap.put(element.getAttribute("name"), mergeExtendObjectDef(extendObjectDefMap,
                            new ExtendObjectDefinition(element, delegator, dispatcher)));
                    break;
                }
            }
        });
        createRootObjectTypeDef(queryRootObjectTypeName, queryRootFieldMap);
        createRootObjectTypeDef(mutationRootObjectTypeName, mutationRootFieldMap);
        updateAllTypeDefMap();
    }

    private void updateAllTypeDefMap() {

        // Extend object which convert to interface first
        for (Map.Entry<String, ExtendObjectDefinition> entry : extendObjectDefMap.entrySet()) {
            ExtendObjectDefinition extendObjectDef = (ExtendObjectDefinition) entry.getValue();
            if (!extendObjectDef.convertToInterface) {
                continue;
            }

            String name = entry.getKey();
            ObjectTypeDefinition objectTypeDef = (ObjectTypeDefinition) allTypeDefMap.get(name);
            if (objectTypeDef == null) {
                throw new IllegalArgumentException("ObjectTypeDefinition [${name}] not found to extend");
            }

            if (interfaceTypeDefMap.containsKey(name)) {
                throw new IllegalArgumentException("Interface [${name}] to be extended already exists");
            }

            InterfaceTypeDefinition interfaceTypeDef = new InterfaceTypeDefinition(objectTypeDef, extendObjectDef,
                    delegator);
            allTypeDefMap.put(interfaceTypeDef.getName(), interfaceTypeDef);
            interfaceTypeDefMap.put(interfaceTypeDef.getName(), interfaceTypeDef);

            objectTypeDef.extend(extendObjectDef, allTypeDefMap);
            // Interface need the object to do resolve
            requiredTypeDefMap.put(objectTypeDef.getName(), objectTypeDef);
        }

        // Extend object
        for (Map.Entry<String, ExtendObjectDefinition> entry : extendObjectDefMap.entrySet()) {
            ExtendObjectDefinition extendObjectDef = (ExtendObjectDefinition) entry.getValue();
            if (extendObjectDef.convertToInterface) {
                continue;
            }

            String name = entry.getKey();

            ObjectTypeDefinition objectTypeDef = (ObjectTypeDefinition) allTypeDefMap.get(name);
            if (objectTypeDef == null) {
                throw new IllegalArgumentException("ObjectTypeDefinition [" + name + "] not found to extend");
            }

            if (name.equals("Product")) {
                System.out.println(
                        "Categories field def parent:  " + extendObjectDef.fieldDefMap.get("categories").parent);
                System.out.println("from objectTypeDef " + objectTypeDef.fieldDefMap.get("categories"));
            }

            objectTypeDef.extend(extendObjectDef, allTypeDefMap);
        }

    }

    private static ExtendObjectDefinition mergeExtendObjectDef(Map<String, ExtendObjectDefinition> extendObjectDefMap,
            ExtendObjectDefinition extendObjectDef) {
        ExtendObjectDefinition eoDef = extendObjectDefMap.get(extendObjectDef.name);
        if (eoDef == null) {
            return extendObjectDef;
        }
        return eoDef.merge(extendObjectDef);
    }

    static FieldDefinition getCachedFieldDefinition(String name, String rawTypeName, String nonNull, String isList,
            String listItemNonNull) {
        return fieldDefMap.get(getFieldKey(name, rawTypeName, nonNull, isList, listItemNonNull));
    }

    private static String getFieldKey(String name, String rawTypeName, String nonNull, String isList,
            String listItemNonNull) {
        String fieldKey = name + KEY_SPLITTER + rawTypeName;
        if ("true".equals(nonNull)) {
            fieldKey = fieldKey + NON_NULL_SUFFIX;
        }
        if ("true".equals(isList)) {
            fieldKey = fieldKey + IS_LIST_SUFFIX;
            if ("true".equals(listItemNonNull)) {
                fieldKey = fieldKey + LIST_ITEM_NON_NULL_SUFFIX;
            }
        }
        return fieldKey;
    }

    private void createRootObjectTypeDef(String rootObjectTypeName, Map<String, String> rootFieldMap) {
        Map<String, FieldDefinition> fieldDefMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rootFieldMap.entrySet()) {
            String fieldName = entry.getKey();
            String fieldTypeName = entry.getValue();
            // Map<String, String> fieldPropertyMap = [nonNull: "true"]
            Map<String, String> fieldPropertyMap = new HashMap<>();
            fieldPropertyMap.put("nonNull", "true");
            FieldDefinition fieldDef = getCachedFieldDefinition(fieldName, fieldTypeName,
                    fieldPropertyMap.get("nonNull"), "false", "false");
            if (fieldDef == null) {
                fieldDef = new FieldDefinition(rootObjectTypeName, delegator, dispatcher, fieldName, fieldTypeName,
                        fieldPropertyMap);
                fieldDef.setDataFetcher(new EmptyDataFetcher(fieldDef));
                putCachedFieldDefinition(fieldDef);
            }
            fieldDefMap.put(fieldName, fieldDef);
        }

        if (fieldDefMap.size() == 0) {
            Map<String, String> fieldPropertyMap = new HashMap<>();
            fieldPropertyMap.put("nonNull", "false");
            FieldDefinition fieldDef = new FieldDefinition(rootObjectTypeName, delegator, dispatcher, "empty", "String",
                    fieldPropertyMap);
            fieldDefMap.put("empty", fieldDef);
        }
        ObjectTypeDefinition objectTypeDef = new ObjectTypeDefinition(delegator, dispatcher, rootObjectTypeName, "",
                new ArrayList<String>(), fieldDefMap);
        allTypeDefMap.put(rootObjectTypeName, objectTypeDef);
    }

    protected static void putCachedFieldDefinition(FieldDefinition fieldDef) {
        String fieldKey = getFieldKey(fieldDef.name, fieldDef.type, fieldDef.nonNull, fieldDef.isList,
                fieldDef.listItemNonNull);
        if (fieldDefMap.get(fieldKey) != null) {
            throw new IllegalArgumentException(
                    "FieldDefinition [${fieldDef.name} - ${fieldDef.type}] already exists in cache");
        }
        fieldDefMap.put(fieldKey, fieldDef);
    }

    public GraphQLSchemaDefinition() {

    }

    static class InterfaceTypeDefinition extends GraphQLTypeDefinition {
        private Delegator delegator;
        private LocalDispatcher dispatcher;
        private String convertFromObjectTypeName;
        private String typeResolver;
        private Map<String, FieldDefinition> fieldDefMap = new LinkedHashMap<>();
        private String resolverField;
        private Map<String, String> resolverMap = new LinkedHashMap<>();
        private String defaultResolvedTypeName;

        InterfaceTypeDefinition(Element node, Delegator delegator, LocalDispatcher dispatcher) {
            this.delegator = delegator;
            this.dispatcher = dispatcher;
            setName(node.getAttribute("name"));
            setType("interface");
            this.typeResolver = node.getAttribute("type-resolver");
            List<? extends Element> elements = UtilXml.childElementList(node);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    setDescription(childNode.getTextContent());
                    break;
                case "field":
                    fieldDefMap.put(childNode.getAttribute("name"), new FieldDefinition(this.getName(), delegator, dispatcher, childNode));
                    break;
                }
            }
        }

        InterfaceTypeDefinition(ObjectTypeDefinition objectTypeDef, ExtendObjectDefinition extendObjectDef,
                Delegator delegator) {
            this.convertFromObjectTypeName = objectTypeDef.getName();
            this.delegator = delegator;
            setName(objectTypeDef.getName() + "Interface");
            setType("interface");
            this.defaultResolvedTypeName = objectTypeDef.getName();
            this.resolverField = extendObjectDef.resolverField;
            this.resolverMap.putAll(extendObjectDef.resolverMap);

            fieldDefMap.putAll(objectTypeDef.fieldDefMap);

            for (Element extendObjectNode : extendObjectDef.extendObjectNodeList) {
                List<? extends Element> elements = UtilXml.childElementList(extendObjectNode, "field");
                for (Element fieldNode : elements) {
                    GraphQLSchemaUtil.mergeFieldDefinition(fieldNode, fieldDefMap, delegator, dispatcher);
                }
            }

            for (String excludeFieldName : extendObjectDef.excludeFields) {
                fieldDefMap.remove(excludeFieldName);
            }

            // Make object type that interface convert from extends interface automatically.
            objectTypeDef.interfaceList.add(getName());
            resolverMap.put(objectTypeDef.getName().toUpperCase(), objectTypeDef.getName()); // Hack to avoid error if the
                                                                                    // resolved type is
            // the one interface was extended from
        }

        public void addResolver(String resolverValue, String resolverType) {
            resolverMap.put(resolverValue, resolverType);
        }

        public List<FieldDefinition> getFieldList() {
            List<FieldDefinition> fieldList = new LinkedList<>();
            for (Map.Entry<String, FieldDefinition> entry : fieldDefMap.entrySet()) {
                fieldList.add(entry.getValue());
            }

            return fieldList;
        }

        @Override
        List<String> getDependentTypes() {
            List<String> typeList = new LinkedList<>();
            for (Map.Entry<String, FieldDefinition> entry : fieldDefMap.entrySet()) {
                typeList.add(((FieldDefinition) entry.getValue()).type);
            }
            return typeList;
        }
    }

    abstract static class GraphQLTypeDefinition {
        /**
         * @return the name
         */
        protected String getName() {
            return name;
        }
        /**
         * @param name the name to set
         */
        protected void setName(String name) {
            this.name = name;
        }
        /**
         * @return the description
         */
        protected String getDescription() {
            return description;
        }
        /**
         * @param description the description to set
         */
        protected void setDescription(String description) {
            this.description = description;
        }
        /**
         * @return the type
         */
        protected String getType() {
            return type;
        }
        /**
         * @param type the type to set
         */
        protected void setType(String type) {
            this.type = type;
        }

        private String name;
        private String description;
        private String type;
        abstract List<String> getDependentTypes();
    }

    static class ObjectTypeDefinition extends GraphQLTypeDefinition {
        private Map<String, FieldDefinition> fieldDefMap = new LinkedHashMap<>();
        private Delegator delegator;
        private LocalDispatcher dispatcher;
        private List<String> interfaceList = new LinkedList<>();
        private Map<String, InterfaceTypeDefinition> interfacesMap;

        ObjectTypeDefinition(Element element, Delegator delegator, LocalDispatcher dispatcher) {
            this.delegator = delegator;
            this.dispatcher = dispatcher;
            setName(element.getAttribute("name"));
            setType("object");
            //this.name = element.getAttribute("name");
            //this.type = "object";
            List<? extends Element> objectElements = UtilXml.childElementList(element);
            for (Element childNode : objectElements) {
                switch (childNode.getNodeName()) {
                case "description":
                    //this.description = childNode.getTextContent();
                    setDescription(childNode.getTextContent());
                    break;
                case "interface":
                    interfaceList.add(childNode.getAttribute("name"));
                    break;
                case "field":
                    fieldDefMap.put(childNode.getAttribute("name"), new FieldDefinition(getName(), delegator, dispatcher, childNode));
                    //fieldDefMap.put(childNode.getAttribute("name"), new FieldDefinition(this.name, delegator, dispatcher, childNode));
                    break;
                }
            }
        }

        ObjectTypeDefinition(Delegator delegator, LocalDispatcher dispatcher, String name, String description,
                List<String> interfaceList, Map<String, FieldDefinition> fieldDefMap) {
            setName(name);
            setDescription(description);
            setType("object");
            this.fieldDefMap.putAll(fieldDefMap);
            this.interfaceList.addAll(interfaceList);
            this.delegator = delegator;
            this.dispatcher = dispatcher;
        }

        List<FieldDefinition> getFieldList() {
            List<FieldDefinition> fieldList = new LinkedList<>();
            for (Map.Entry<String, FieldDefinition> entry : fieldDefMap.entrySet()) {
                fieldList.add((FieldDefinition) entry.getValue());
            }
            return fieldList;
        }

        @Override
        List<String> getDependentTypes() {
            List<String> typeList = new LinkedList<>();
            for (String interfaceTypeName : interfaceList) {
                typeList.add(interfaceTypeName);
            }
            for (Map.Entry<String, FieldDefinition> entry : fieldDefMap.entrySet()) {
                typeList.add(((FieldDefinition) entry.getValue()).type);
            }

            return typeList;
        }

        void extend(ExtendObjectDefinition extendObjectDef, Map<String, GraphQLTypeDefinition> allTypeDefMap) {
            for (Element extendObjectNode : extendObjectDef.extendObjectNodeList) {
                List<? extends Element> objectElements = UtilXml.childElementList(extendObjectNode, "interface");
                for (Element childNode : objectElements) {
                    String interfaceDef = childNode.getAttribute("name");
                    GraphQLTypeDefinition interfaceTypeDef = allTypeDefMap.get(interfaceDef);
                    if (interfaceTypeDef == null) {
                        throw new IllegalArgumentException("Extend object " + extendObjectDef.name
                                + ", but interface definition [" + interfaceDef + "] not found");
                    }
                    if (!(interfaceTypeDef instanceof InterfaceTypeDefinition)) {
                        throw new IllegalArgumentException("Extend object " + extendObjectDef.name
                                + ", but interface definition " + childNode.getAttribute("name")
                                + " is not instance of InterfaceTypeDefinition");
                    }
                    extendInterface((InterfaceTypeDefinition) interfaceTypeDef, childNode);
                }
            }
            for (Element extendObjectNode : extendObjectDef.extendObjectNodeList) {
                List<? extends Element> objectElements = UtilXml.childElementList(extendObjectNode, "field");
                for (Element childNode : objectElements) {
                    GraphQLSchemaUtil.mergeFieldDefinition(childNode, fieldDefMap, delegator, dispatcher);
                }
            }
            for (String excludeFieldName : extendObjectDef.excludeFields) {
                fieldDefMap.remove(excludeFieldName);
            }
        }

        private void extendInterface(InterfaceTypeDefinition interfaceTypeDefinition, Element interfaceNode) {
            for (Map.Entry<String, FieldDefinition> entry : interfaceTypeDefinition.fieldDefMap.entrySet()) {
                // Already use interface field.
                fieldDefMap.put(entry.getKey(), entry.getValue());
            }
            interfaceTypeDefinition.addResolver(interfaceNode.getAttribute("resolver-value"), getName());
            if (!interfaceList.contains(interfaceTypeDefinition.getName())) {
                interfaceList.add(interfaceTypeDefinition.getName());
            }
        }

    }

    static String getArgumentTypeName(String type, String fieldIsList) {
        if (!"true".equals(fieldIsList)) {
            return type;
        }
        if (GraphQLSchemaUtil.GRAPH_QL_STRING_TYPES.contains(type)
                || GraphQLSchemaUtil.GRAPHS_QL_NUMERIC_TYPES.contains(type)
                || GraphQLSchemaUtil.GRAPHS_QL_DATE_TYPES.contains(type)) {
            return operationInputType.getName();
        }
        if (GraphQLSchemaUtil.GRAPHS_QL_DATE_TYPES.contains(type)) {
            return dateRangeInputType.getName();
        }

        return type;
    }

    static String getArgumentKey(String name, String type) {
        return getArgumentKey(name, type, null);
    }

    static String getArgumentKey(String name, String type, String required) {
        String argumentKey = name + KEY_SPLITTER + type;
        if ("true".equals(required)) {
            argumentKey = argumentKey + REQUIRED_SUFFIX;
        }
        return argumentKey;
    }

    static void putCachedArgumentDefinition(ArgumentDefinition argDef) {
        if (!(GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.containsKey(argDef.getType())
                || dateRangeInputType.getName().equals(argDef.getType())
                || operationInputType.getName().equals(argDef.getType()))) {
            return;
        }

        String argumentKey = getArgumentKey(argDef.name, argDef.getType(), argDef.getRequired());
        if (argumentDefMap.get(argumentKey) != null) {
            throw new IllegalArgumentException(
                    "ArgumentDefinition [" + argDef.name + " - " + argDef.getType() + "] already exists in cache");
        }
        argumentDefMap.put(argumentKey, argDef);
    }

    public static final class FieldDefinition implements Cloneable {

        public String toString() {
            return "FieldDefinition{name=" + this.name + ", parent=" + this.parent + ", type=" + this.type
                    + ", nonNull=" + this.nonNull + ", isList=" + this.isList + ", " + "listItemNonNull="
                    + this.listItemNonNull + ", " + "isMutation=" + this.isMutation + ", argumentDefMap="
                    + this.argumentDefMap + "}";
        }

        private String name;
        private String type;
        private String description;
        private String depreciationReason;
        private String parent;
        private String nonNull;
        private String isList;
        private String listItemNonNull;
        private BaseDataFetcher dataFetcher;
        private Delegator delegator;
        private LocalDispatcher dispatcher;
        private String requireAuthentication;

        public String getRequireAuthentication() {
            return requireAuthentication;
        }

        public String getType() {
            return type;
        }

        public void setDataFetcher(BaseDataFetcher dataFetcher) {
            this.dataFetcher = dataFetcher;
        }

        public String getNonNull() {
            return nonNull;
        }

        public String getIsList() {
            return isList;
        }

        private boolean isMutation = false;

        public boolean isMutation() {
            return isMutation;
        }

        private String preDataFetcher;
        private String postDataFetcher;
        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the description
         */
        public String getDescription() {
            return description;
        }

        /**
         * @param description the description to set
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * @return the argumentDefMap
         */
        public Map<String, ArgumentDefinition> getArgumentDefMap() {
            return argumentDefMap;
        }

        /**
         * @param argumentDefMap the argumentDefMap to set
         */
        public void setArgumentDefMap(Map<String, ArgumentDefinition> argumentDefMap) {
            this.argumentDefMap = argumentDefMap;
        }

        /**
         * @return the dataFetcher
         */
        public BaseDataFetcher getDataFetcher() {
            return dataFetcher;
        }

        /**
         * @param type the type to set
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * @param nonNull the nonNull to set
         */
        public void setNonNull(String nonNull) {
            this.nonNull = nonNull;
        }

        /**
         * @param isList the isList to set
         */
        public void setIsList(String isList) {
            this.isList = isList;
        }

        /**
         * @param requireAuthentication the requireAuthentication to set
         */
        public void setRequireAuthentication(String requireAuthentication) {
            this.requireAuthentication = requireAuthentication;
        }

        /**
         * @param isMutation the isMutation to set
         */
        public void setMutation(boolean isMutation) {
            this.isMutation = isMutation;
        }

        /**
         * @return the listItemNonNull
         */
        public String getListItemNonNull() {
            return listItemNonNull;
        }

        /**
         * @param listItemNonNull the listItemNonNull to set
         */
        public void setListItemNonNull(String listItemNonNull) {
            this.listItemNonNull = listItemNonNull;
        }

        /**
         * @return the depreciationReason
         */
        public String getDepreciationReason() {
            return depreciationReason;
        }

        /**
         * @param depreciationReason the depreciationReason to set
         */
        public void setDepreciationReason(String depreciationReason) {
            this.depreciationReason = depreciationReason;
        }

        private Map<String, ArgumentDefinition> argumentDefMap = new LinkedHashMap<>();

        FieldDefinition(String parent, Delegator delegator, LocalDispatcher dispatcher, String name, String type) {
            this(parent, delegator, dispatcher, name, type, new HashMap<>(), null, new ArrayList<>());
        }

        FieldDefinition(String parent, Delegator delegator, LocalDispatcher dispatcher, String name, String type,
                Map<String, String> fieldPropertyMap) {
            this(parent, delegator, dispatcher, name, type, fieldPropertyMap, null, new ArrayList<>());
        }

        // This constructor used by auto creation of master-detail field
        FieldDefinition(String parent, Delegator delegator, LocalDispatcher dispatcher, String name, String type,
                Map<String, String> fieldPropertyMap, List<String> excludedFields) {
            this(parent, delegator, dispatcher, name, type, fieldPropertyMap, null, excludedFields);
        }

        FieldDefinition(String parent, Delegator delegator, LocalDispatcher dispatcher, String name, String type,
                Map<String, String> fieldPropertyMap, BaseDataFetcher dataFetcher, List<String> excludedArguments) {
            this.parent = parent;
            this.delegator = delegator;
            this.name = name;
            this.type = type;
            this.dataFetcher = dataFetcher;
            this.nonNull = fieldPropertyMap.get("nonNull") != null ? fieldPropertyMap.get("nonNull") : "false";
            this.isList = fieldPropertyMap.get("isList") != null ? fieldPropertyMap.get("isList") : "false";
            this.listItemNonNull = fieldPropertyMap.get("listItemNonNull") != null
                    ? fieldPropertyMap.get("listItemNonNull")
                    : "false";
            this.description = fieldPropertyMap.get("description");
            addEntityAutoArguments(excludedArguments, new HashMap<String, String>());
            // updateArgumentDefs();
            addPeriodValidArguments();
        }

        FieldDefinition(String parent, Delegator delegator, LocalDispatcher dispatcher, Element node) {
            this.parent = parent;
            this.delegator = delegator;
            this.dispatcher = dispatcher;
            this.name = node.getAttribute("name");
            this.type = node.getAttribute("type");
            this.description = node.getAttribute("description");
            this.nonNull = node.getAttribute("non-null") != null ? node.getAttribute("non-null") : "false";
            this.isList = node.getAttribute("is-list") != null ? node.getAttribute("is-list") : "false";
            this.listItemNonNull = node.getAttribute("list-item-non-null") != null
                    ? node.getAttribute("list-item-non-null")
                    : "false";
            this.isMutation = "mutation".equals(node.getAttribute("for"));

            String dataFetcherType = "";
            Element dataFetcherNode = null;
            List<? extends Element> objectElements = UtilXml.childElementList(node);
            for (Element childNode : objectElements) {
                switch (childNode.getNodeName()) {
                case "description":
                    this.description = childNode.getTextContent();
                    break;
                case "argument":
                    String argTypeName = getArgumentTypeName(childNode.getAttribute("type"), this.isList);
                    ArgumentDefinition argDef = getCachedArgumentDefinition(childNode.getAttribute("name"), argTypeName,
                            childNode.getAttribute("required"));
                    if (argDef == null) {
                        argDef = new ArgumentDefinition(childNode, this);
                        putCachedArgumentDefinition(argDef);
                    }
                    mergeArgument(argDef);
                    break;
                case "service-fetcher":
                    dataFetcherType = "service";
                    dataFetcherNode = childNode;
                    this.dataFetcher = new ServiceDataFetcher(childNode, this, delegator, dispatcher);
                    break;
                case "entity-fetcher":
                    dataFetcherType = "entity";
                    dataFetcherNode = childNode;
                    this.dataFetcher = new EntityDataFetcher(delegator, childNode, this);
                    break;
                case "empty-fetcher":
                    dataFetcherType = "empty";
                    dataFetcherNode = childNode;
                    this.dataFetcher = new EmptyDataFetcher(childNode, this);
                    break;
                }
            }

            Map<String, String> keyMap = getDataFetcherKeyMap(dataFetcherNode, delegator);
            switch (dataFetcherType) {
            case "entity":
            case "interface":
                addEntityAutoArguments(new ArrayList<String>(), keyMap);
                addPeriodValidArguments();
                // updateArgumentDefs(); TODO
                break;
            case "service":
                if (isMutation) {
                    addInputArgument();
                } else {
                    addQueryAutoArguments(dataFetcherNode, keyMap, dispatcher);
                }
                break;
            }
        }

        private void addPeriodValidArguments() {
            if (!"true".equals(isList)) {
                return;
            }

            List<String> allArguments = new ArrayList<String>(argumentDefMap.keySet());
            List<String> fromDateArguments = allArguments.stream()
                    .filter((argument) -> argument.equals("fromDate") || argument.endsWith("FromDate"))
                    .collect(Collectors.toList());
            List<String> pairedFromDateArguments = fromDateArguments.stream()
                    .filter((argument) -> (argument.equals("fromDate") && allArguments.contains("thruDate"))
                            || allArguments.contains(argument.replace("FromDate", "ThruDate")))
                    .collect(Collectors.toList());
            for (String argument : pairedFromDateArguments) {
                String periodValidArgName = argument == "fromDate" ? "periodValid_"
                        : argument.replace("FromDate", "PeriodValid_");
                ArgumentDefinition argumentDef = getCachedArgumentDefinition(periodValidArgName, "Boolean", null);
                if (argumentDef == null) {
                    argumentDef = new ArgumentDefinition(this, periodValidArgName, "Boolean", null, null, "");
                    putCachedArgumentDefinition(argumentDef);
                }
                argumentDefMap.put(periodValidArgName, argumentDef);
            }
        }

        void mergeArgument(ArgumentDefinition argumentDef) {
            mergeArgument(argumentDef.name, argumentDef.attributeMap);
        }

        ArgumentDefinition mergeArgument(final String argumentName, Map<String, String> attributeMap) {
            ArgumentDefinition baseArgumentDef = argumentDefMap.get(argumentName);
            if (baseArgumentDef == null) {
                baseArgumentDef = getCachedArgumentDefinition(argumentName, attributeMap.get("type"),
                        attributeMap.get("required"));
                if (baseArgumentDef == null) {
                    baseArgumentDef = new ArgumentDefinition(this, argumentName, attributeMap);
                    putCachedArgumentDefinition(baseArgumentDef);
                }
                argumentDefMap.put(argumentName, baseArgumentDef);
            } else {
                baseArgumentDef.attributeMap.putAll(attributeMap);
            }
            return baseArgumentDef;
        }

        private static Map<String, String> getDataFetcherKeyMap(Element fetcherNode, Delegator delegator) {
            Map<String, String> keyMap = new HashMap<>(1);
            if (fetcherNode == null) {
                return keyMap;
            }

            List<? extends Element> elements = UtilXml.childElementList(fetcherNode, "key-map");
            if (fetcherNode.getNodeName().equals("entity-fetcher")) {
                String entityName = fetcherNode.getAttribute("entity-name");
                ModelEntity entity = delegator.getModelEntity(entityName);
                for (Element keyMapNode : elements) {
                    String fieldName = keyMapNode.getAttribute("field-name");
                    String relFn = keyMapNode.getAttribute("related");
                    if (relFn == null) {
                        if (entity.isField(fieldName)) {
                            relFn = fieldName;
                        } else {
                            if (entity.getPkFieldNames().size() == 1) {
                                relFn = entity.getPkFieldNames().get(0);
                            }
                        }
                    }
                    if (relFn == null) {
                        throw new IllegalArgumentException(
                                "The key-map.@related of Entity ${entityName} should be specified");
                    }
                    keyMap.put(fieldName, relFn);
                }
            } else {
                for (Element keyMapNode : elements) {
                    keyMap.put(keyMapNode.getAttribute("field-name"),
                            keyMapNode.getAttribute("related") != null ? keyMapNode.getAttribute("related")
                                    : keyMapNode.getAttribute("field-name"));
                }
            }
            return keyMap;
        }

        private void addQueryAutoArguments(Element serviceFetcherNode, Map<String, String> keyMap,
                LocalDispatcher dispatcher) {
            if (isMutation) {
                return;
            }
            String serviceName = serviceFetcherNode.getAttribute("service");

            try {
                ModelService service = dispatcher.getDispatchContext().getModelService(serviceName);
                if (service == null) {
                    throw new IllegalArgumentException(
                            "Service [" + serviceName + "] for field [" + name + "] not found");
                }

                for (ModelParam modelParam : service.getInModelParamList()) {

                    String paramName = modelParam.getName();
                    String paramType = modelParam.getType();
                    boolean optional = modelParam.isOptional();
                    if (modelParam.isInternal()) {
                        continue;
                    }
                    if (keyMap.values().contains(paramName)) {
                        continue;
                    }
                    if (paramType.equals("graphql.schema.DataFetchingEnvironment")) {
                        continue; // ignored
                    }
                    // TODO: get description from parameter description node
                    String paramDescription = "";
                    boolean argIsList = false;
                    String argType;
                    switch (paramType) {
                    case "org.apache.ofbiz.graphql.schema.OperationInputType":
                        argType = "OperationInputType";
                        break;
                    case "org.apache.ofbiz.graphql.schema.DateRangeInputType":
                        argType = "DateRangeInputType";
                        break;
                    case "org.apache.ofbiz.graphql.schema.PaginationInputType":
                        argType = "PaginationInputType";
                        break;
                    case "List":
                        argIsList = true;
                        argType = GraphQLSchemaUtil.camelCaseToUpperCamel(this.name) + "_" + paramName;
                        break;
                    case "Map":
                        argType = GraphQLSchemaUtil.camelCaseToUpperCamel(this.name) + "_" + paramName;
                        break;
                    default:
                        argType = GraphQLSchemaUtil.JAVA_TYPE_GRAPH_QL_MAP.get(paramType);
                        break;
                    }
                    if (argType == null) {
                        throw new IllegalArgumentException(
                                "Parameter [" + paramName + "] and paramType [" + paramType + "] can't be mapped");
                    }

                    ArgumentDefinition argumentDef = getCachedArgumentDefinition(paramName, argType,
                            Boolean.toString(!optional));
                    if (argumentDef == null) {
                        argumentDef = new ArgumentDefinition(this, paramName, argType, Boolean.toString(!optional),
                                argIsList, null, paramDescription);
                        putCachedArgumentDefinition(argumentDef);
                    }
                    argumentDefMap.put(paramName, argumentDef);
                }
            } catch (GenericServiceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        private void addEntityAutoArguments(List<String> excludedFields, Map<String, String> explicitKeyMap) {
            if (isMutation) {
                return;
            }
            if (GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.keySet().contains(type)
                    || GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.keySet().contains(type)) {
                return;
            }
            ModelEntity entity = GraphQLSchemaUtil.getEntityDefinition(type, delegator);

            if (UtilValidate.isEmpty(entity)) {
                return;
            }
            List<String> fieldNames = new ArrayList<>();
            if ("true".equals(isList)) {
                fieldNames.addAll(entity.getAllFieldNames());
            } else {
                fieldNames.addAll(entity.getPkFieldNames());
            }

            fieldNames.removeAll(explicitKeyMap.values());

            for (String fieldName : fieldNames) {
                if (excludedFields.contains(fieldName)) {
                    continue;
                }
                ModelField fi = entity.getField(fieldName);
                String fieldDescription = fi.getDescription();
                // Add fields in entity as argument
                String argType = getArgumentTypeName(GraphQLSchemaUtil.FIELD_TYPE_GRAPH_QL_MAP.get(fi.getType()),
                        isList);

                ArgumentDefinition argumentDef = getCachedArgumentDefinition(fi.getName(), argType, null);
                if (argumentDef == null) {
                    argumentDef = new ArgumentDefinition(this, fi.getName(), argType, null, null, fieldDescription);
                    putCachedArgumentDefinition(argumentDef);
                }
                argumentDefMap.put(fi.getName(), argumentDef);
            }
        }

        List<ArgumentDefinition> getArgumentList() {
            List<ArgumentDefinition> argumentList = new LinkedList<>();
            for (Map.Entry<String, ArgumentDefinition> entry : argumentDefMap.entrySet()) {
                argumentList.add(entry.getValue());
            }
            return argumentList;
        }

        private void addInputArgument() {
            if (!isMutation) {
                return;
            }

            String inputTypeName = GraphQLSchemaUtil.camelCaseToUpperCamel(this.name) + "Input";
            ArgumentDefinition inputArgDef = new ArgumentDefinition(this, "input", inputTypeName, "true", null, "");
            argumentDefMap.put("input", inputArgDef);
        }
    }

    static ArgumentDefinition getCachedArgumentDefinition(String name, String type, String required) {
        return argumentDefMap.get(getArgumentKey(name, type, required));
    }

    static class AutoArgumentsDefinition {
        private String entityName;
        private String include;
        private String required;
        private List<String> excludes = new LinkedList<>();

        AutoArgumentsDefinition(Element node) {
            this.entityName = node.getAttribute("entity-name");
            this.include = node.getAttribute("include") != null ? node.getAttribute("include") : "all";
            this.required = node.getAttribute("required") != null ? node.getAttribute("required") : "false";
            List<? extends Element> elements = UtilXml.childElementList(node, "exclude");
            for (Element childNode : elements) {
                excludes.add(childNode.getAttribute("field-name"));
            }
        }
    }

    static class ArgumentDefinition implements Cloneable {
        private String name;
        private boolean isList = false;
        private Map<String, String> attributeMap = new LinkedHashMap<>();;

        ArgumentDefinition(Element ele, FieldDefinition fieldDef) {
            this.name = ele.getAttribute("name");
            if (ele.getAttribute("type") == "List") {
                this.isList = true;
            }
            attributeMap.put("type", ele.getAttribute("type"));
            attributeMap.put("required", ele.getAttribute("required") != null ? ele.getAttribute("required") : "false");
            attributeMap.put("defaultValue", ele.getAttribute("default-value"));
            List<? extends Element> elements = UtilXml.childElementList(ele);
            for (Element childNode : elements) {
                if ("description".equals(childNode.getNodeName())) {
                    attributeMap.put("description", childNode.getAttribute("description"));
                }
            }
        }

        ArgumentDefinition(FieldDefinition fieldDef, String name, Map<String, String> attributeMap) {
            this.name = name;
            this.attributeMap.putAll(attributeMap);
        }

        ArgumentDefinition(FieldDefinition fieldDef, String name, String type, String required, String defaultValue,
                String description) {
            this(fieldDef, name, type, required, false, defaultValue, description);
        }

        ArgumentDefinition(FieldDefinition fieldDef, String name, String type, String required, boolean isList,
                String defaultValue, String description) {
            this.name = name;
            this.isList = isList;
            attributeMap.put("type", type);
            attributeMap.put("required", required);
            attributeMap.put("defaultValue", defaultValue);
            attributeMap.put("description", description);
        }

        String getName() {
            return name;
        }

        String getType() {
            return attributeMap.get("type");
        }

        String getRequired() {
            return attributeMap.get("required");
        }

        String getDefaultValue() {
            return attributeMap.get("defaultValue");
        }

        String getDescription() {
            return attributeMap.get("description");
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            // TODO Auto-generated method stub
            return super.clone();
        }

    }

    static class InputObjectFieldDefinition {
        private String name;
        private String type;
        private String description;
        private boolean nonNull;

        public boolean isNonNull() {
            return nonNull;
        }

        public boolean isList() {
            return list;
        }

        public boolean isListItemNonNull() {
            return listItemNonNull;
        }

        private boolean list;
        private boolean listItemNonNull;
        private Object defaultValue;

        InputObjectFieldDefinition(String name, String type, Object defaultValue, String description) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.nonNull = false;
            this.list = false;
            this.listItemNonNull = false;
            this.description = description;
        }

        InputObjectFieldDefinition(String name, String type, Object defaultValue, String description, boolean nonNull,
                boolean list, boolean listItemNonNull) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.nonNull = nonNull;
            this.list = list;
            this.listItemNonNull = listItemNonNull;
            this.description = description;
        }
    }

    /**
     * Creates a new GraphQLSchema using SDL
     *
     * @return
     */
    public GraphQLSchema newSDLSchema() {
        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        Reader cdpSchemaReader = getSchemaReader("component://graphql/graphql-schema/schema.graphqls");
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        typeRegistry.merge(schemaParser.parse(cdpSchemaReader));
        RuntimeWiring runtimeWiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
        return graphQLSchema;
    }

    /**
     * Creates a new GraphQLSchema dynamically
     *
     * @return
     */
    public GraphQLSchema newDynamicSchema() {
        GraphQLObjectType productType = newObject().name("Product")
                .field(newFieldDefinition().name("productId").type(GraphQLString))
                .field(newFieldDefinition().name("productId").type(GraphQLString))
                .field(newFieldDefinition().name("productName").type(GraphQLString))
                .field(newFieldDefinition().name("description").type(GraphQLString))
                .field(newFieldDefinition().name("productTypeId").type(GraphQLString))
                .field(newFieldDefinition().name("primaryProductCategoryId").type(GraphQLString))
                .field(newFieldDefinition().name("isVirtual").type(GraphQLString)).build();
        GraphQLObjectType queryType = newObject().name("QueryRootObjectType").field(newFieldDefinition().name("product")
                .type(productType).argument(GraphQLArgument.newArgument().name("id").type(GraphQLString))).build();
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("Query", "product"),
                        new StaticDataFetcher("Test Static Response"))
                .typeResolver("PartyInterface", new TypeResolver() {

                    @Override
                    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                        Object object = env.getObject();
                        System.out.println("object " + object);
                        return null;
                    }
                }).build();
        GraphQLSchema schema = GraphQLSchema.newSchema().query(queryType).codeRegistry(codeRegistry).build();
        return schema;

    }

    private static Reader getSchemaReader(String resourceUrl) {
        File schemaFile = FileUtil.getFile(resourceUrl);
        try {
            return new InputStreamReader(new FileInputStream(schemaFile), StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Builds Runtime Wiring for the schema types defined
     *
     * @return
     */
    private RuntimeWiring buildRuntimeWiring() {
        RuntimeWiring.Builder build = RuntimeWiring.newRuntimeWiring();
        build.type(newTypeWiring("Query").dataFetcher("product", new EntityDataFetcher()));
        return build.build();
    }

    private void addSchemaInputTypes() {
        for (Map.Entry<String, GraphQLScalarType> entry : GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.entrySet()) {
            schemaInputTypeMap.put(entry.getKey(), entry.getValue());
        }

        schemaInputTypeMap.put(paginationInputType.getName(), paginationInputType);
        schemaInputTypeMap.put(operationInputType.getName(), operationInputType);
        schemaInputTypeMap.put(dateRangeInputType.getName(), dateRangeInputType);

        // Add explicitly defined input types from *.graphql.xml
        for (String inputTypeName : schemaInputTypeNameList) {
            GraphQLInputType type = GRAPH_QL_INPUT_TYPE_MAP.get(inputTypeName);
            if (type == null) {
                throw new IllegalArgumentException("GraphQLInputType [" + inputTypeName + "] for schema not found");
            }
            schemaInputTypeMap.put(inputTypeName, type);
        }

        addSchemaInputObjectTypes();
    }

    private GraphQLTypeDefinition getTypeDef(String name) {
        return allTypeDefMap.get(name);
    }

    private void populateSortedTypes() {
        allTypeDefSortedList.clear();
        GraphQLTypeDefinition queryTypeDef = getTypeDef(queryRootObjectTypeName);
        GraphQLTypeDefinition mutationTypeDef = getTypeDef(mutationRootObjectTypeName);

        TreeNode<GraphQLTypeDefinition> rootNode = new TreeNode<>(null);
        TreeNode<GraphQLTypeDefinition> interfaceNode = new TreeNode<>(null);

        for (Map.Entry<String, InterfaceTypeDefinition> entry : interfaceTypeDefMap.entrySet()) {
            interfaceNode.children.add(new TreeNode<GraphQLTypeDefinition>((InterfaceTypeDefinition) entry.getValue()));
        }

        TreeNode<GraphQLTypeDefinition> queryTypeNode = new TreeNode<GraphQLTypeDefinition>(queryTypeDef);
        rootNode.children.add(queryTypeNode);

        List<String> objectTypeNames = new ArrayList<>(
                Arrays.asList(queryRootObjectTypeName, mutationRootObjectTypeName));
        createTreeNodeRecursive(interfaceNode, objectTypeNames, true);
        traverseByPostOrder(interfaceNode, allTypeDefSortedList);

        createTreeNodeRecursive(queryTypeNode, objectTypeNames, false);
        traverseByPostOrder(queryTypeNode, allTypeDefSortedList);

        if (mutationTypeDef != null) {
            TreeNode<GraphQLTypeDefinition> mutationTypeNode = new TreeNode<GraphQLTypeDefinition>(mutationTypeDef);
            rootNode.children.add(mutationTypeNode);
            createTreeNodeRecursive(mutationTypeNode, objectTypeNames, false);
            traverseByPostOrder(mutationTypeNode, allTypeDefSortedList);
        }

        for (Map.Entry<String, GraphQLTypeDefinition> entry : requiredTypeDefMap.entrySet()) {
            if (allTypeDefSortedList.contains(entry.getValue())) {
                continue;
            }
            allTypeDefSortedList.add((GraphQLTypeDefinition) entry.getValue());
        }
    }

    private void createTreeNodeRecursive(TreeNode<GraphQLTypeDefinition> node, List<String> objectTypeNames,
            boolean includeInterface) {
        if (node.data != null) {
            for (String type : node.data.getDependentTypes()) {
                // If type is GraphQL Scalar types, skip.
                if (GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.containsKey(type)) {
                    continue;
                }
                // If type is GraphQLObjectType which already added in Tree, skip.
                if (objectTypeNames.contains(type)) {
                    continue;
                }
                if (!includeInterface && "interface".equals(type)) {
                    continue;
                }

                GraphQLTypeDefinition typeDef = getTypeDef(type);
                if (typeDef != null) {
                    TreeNode<GraphQLTypeDefinition> typeTreeNode = new TreeNode<>(typeDef);
                    node.children.add(typeTreeNode);
                    objectTypeNames.add(type);
                    createTreeNodeRecursive(typeTreeNode, objectTypeNames, includeInterface);
                } else {
                    System.err.println("No GraphQL Type " + type + " defined");
                }
            }
        } else {
            for (TreeNode<GraphQLTypeDefinition> childTreeNode : node.children) {
                createTreeNodeRecursive(childTreeNode, objectTypeNames, includeInterface);
            }
        }
    }

    /**
     * Generates native GraphQL Schema
     * @return
     */
    public GraphQLSchema generateSchema() {
        addSchemaInputTypes();
        populateSortedTypes();

        for (GraphQLTypeDefinition typeDef : allTypeDefSortedList) {
            switch (typeDef.type) {
            case "interface":
                addGraphQLInterfaceType((InterfaceTypeDefinition) typeDef);
                break;
            }
        }
        for (GraphQLTypeDefinition typeDef : allTypeDefSortedList) {
            switch (typeDef.type) {
            case "object":
                addGraphQLObjectType((ObjectTypeDefinition) typeDef);
                break;
            }
        }
        rebuildQueryObjectType();
        GraphQLObjectType schemaQueryType = GRAPH_QL_OBJECT_TYPE_MAP.get(this.queryRootObjectTypeName);
        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema().query(schemaQueryType);

        if (mutationRootFieldMap.size() > 0) {
            GraphQLObjectType schemaMutationType = GRAPH_QL_OBJECT_TYPE_MAP.get(this.mutationRootObjectTypeName);
            schemaBuilder = schemaBuilder.mutation(schemaMutationType);
        }

        schemaBuilder.codeRegistry(codeRegistryBuilder.build());

        return schemaBuilder.build();
    }

    private static void addGraphQLInterfaceType(InterfaceTypeDefinition interfaceTypeDef) {
        String interfaceTypeName = interfaceTypeDef.getName();
        GraphQLInterfaceType interfaceType = GRAPH_QL_INTERFACE_TYPE_MAP.get(interfaceTypeName);
        if (interfaceType != null) {
            return;
        }

        interfaceResolverTypeSet.addAll(interfaceTypeDef.resolverMap.values());

        GraphQLInterfaceType.Builder interfaceTypeBuilder = GraphQLInterfaceType.newInterface().name(interfaceTypeName)
                .description(interfaceTypeDef.getDescription());

        for (FieldDefinition fieldDef : interfaceTypeDef.getFieldList()) {
            interfaceTypeBuilder.field(buildSchemaField(fieldDef));
        }

        // TODO: Add typeResolver for type, one way is to add a service as resolver
        if (!interfaceTypeDef.convertFromObjectTypeName.isEmpty()) {
            if (interfaceTypeDef.resolverField == null || interfaceTypeDef.resolverField.isEmpty()) {
                throw new IllegalArgumentException(
                        "Interface definition of ${interfaceTypeName} resolverField not set");
            }

            codeRegistryBuilder.typeResolver(interfaceTypeName, (env) -> {
                Object object = env.getObject();
                String resolverFieldValue = (String) ((Map) object).get(interfaceTypeDef.resolverField);
                String resolvedTypeName = interfaceTypeDef.resolverMap.get(resolverFieldValue);
                GraphQLObjectType resolvedType = GRAPH_QL_OBJECT_TYPE_MAP.get(resolvedTypeName);
                if (resolvedType == null) {
                    resolvedType = GRAPH_QL_OBJECT_TYPE_MAP.get(interfaceTypeDef.defaultResolvedTypeName);
                }
                return resolvedType;
            });
        }

        interfaceType = interfaceTypeBuilder.build();
        GRAPH_QL_INTERFACE_TYPE_MAP.put(interfaceTypeName, interfaceType);
        GRAPH_QL_OUTPUT_TYPE_MAP.put(interfaceTypeName, interfaceType);
    }

    private void traverseByPostOrder(TreeNode<GraphQLTypeDefinition> startNode,
            LinkedList<GraphQLTypeDefinition> sortedList) {
        if (startNode == null) {
            return;
        }

        for (TreeNode<GraphQLTypeDefinition> childNode : startNode.children) {
            traverseByPostOrder(childNode, sortedList);
        }

        if (startNode.data == null) {
            return;
        }

        if (!sortedList.contains(startNode.data)) {
            sortedList.add(startNode.data);
        }
    }

    private void rebuildQueryObjectType() {
        ObjectTypeDefinition queryObjectTypeDef = (ObjectTypeDefinition) allTypeDefMap.get(queryRootObjectTypeName);

        GraphQLObjectType.Builder queryObjectTypeBuilder = GraphQLObjectType.newObject().name(queryRootObjectTypeName)
                .description(queryObjectTypeDef.getDescription());

        for (FieldDefinition fieldDef : queryObjectTypeDef.getFieldList()) {
            queryObjectTypeBuilder = queryObjectTypeBuilder.field(buildSchemaField(fieldDef));
        }

        // create a fake object type
        GraphQLObjectType.Builder graphQLObjectTypeBuilder = GraphQLObjectType.newObject()
                .name("TypeReferenceContainer").description(
                        "This is only for contain GraphQLTypeReference so GraphQLSchema includes all of GraphQLTypeReference.");

        boolean hasFakeField = false;
        List<String> fakeFieldNameList = new ArrayList<>();
        // fields for GraphQLTypeReference
        for (Map.Entry<String, GraphQLTypeReference> entry : GRAPH_QL_TYPE_REFERENCE_MAP.entrySet()) {
            if (fakeFieldNameList.contains(entry.getKey())) {
                continue;
            }

            FieldDefinition fieldDef = new FieldDefinition("TypeReferenceContainer", delegator, dispatcher,
                    entry.getKey(), entry.getKey());
            graphQLObjectTypeBuilder.field(buildSchemaField(fieldDef));
            fakeFieldNameList.add(entry.getKey());
            hasFakeField = true;
        }

        // fields for resolver type of interface
        for (String resolverType : interfaceResolverTypeSet) {
            if (fakeFieldNameList.contains(resolverType)) {
                continue;
            }

            GraphQLTypeDefinition typeDef = getTypeDef(resolverType);
            if (typeDef == null) {
                throw new IllegalArgumentException("GraphQLTypeDefinition [" + resolverType + "] not found");
            }
            addGraphQLObjectType((ObjectTypeDefinition) typeDef);

            FieldDefinition fieldDef = new FieldDefinition("TypeReferenceContainer", delegator, dispatcher,
                    resolverType, resolverType);
            graphQLObjectTypeBuilder.field(buildSchemaField(fieldDef));
            fakeFieldNameList.add(resolverType);
            hasFakeField = true;
        }

        if (hasFakeField) {
            GraphQLObjectType fakeObjectType = graphQLObjectTypeBuilder.build();
            GraphQLFieldDefinition fakeField = GraphQLFieldDefinition.newFieldDefinition()
                    .name("typeReferenceContainer").type(fakeObjectType).build();
            queryObjectTypeBuilder.field(fakeField);
        }

        GraphQLObjectType queryObjectType = queryObjectTypeBuilder.build();
        GRAPH_QL_OBJECT_TYPE_MAP.put(queryRootObjectTypeName, queryObjectType);
        GRAPH_QL_OUTPUT_TYPE_MAP.put(queryRootObjectTypeName, queryObjectType);
    }

    private static void addGraphQLObjectType(ObjectTypeDefinition objectTypeDef) {
        String objectTypeName = objectTypeDef.getName();
        System.out.println("objectTypeName " + objectTypeName);
        GraphQLObjectType objectType = GRAPH_QL_OBJECT_TYPE_MAP.get(objectTypeName);
        // System.out.println("objectType "+objectType);
        if (objectType != null) {
            return;
        }

        GraphQLObjectType.Builder objectTypeBuilder = GraphQLObjectType.newObject().name(objectTypeName)
                .description(objectTypeDef.getDescription());

        for (String interfaceName : objectTypeDef.interfaceList) {
            GraphQLInterfaceType interfaceType = GRAPH_QL_INTERFACE_TYPE_MAP.get(interfaceName);
            if (interfaceType == null) {
                throw new IllegalArgumentException("GraphQLInterfaceType [" + interfaceName
                        + "] for GraphQLObjectType [" + objectTypeName + "] not found.");
            }

            objectTypeBuilder = objectTypeBuilder.withInterface(interfaceType);
        }

        for (FieldDefinition fieldDef : objectTypeDef.getFieldList()) {
            objectTypeBuilder = objectTypeBuilder.field(buildSchemaField(fieldDef));
        }

        objectType = objectTypeBuilder.build();
        GRAPH_QL_OBJECT_TYPE_MAP.put(objectTypeName, objectType);
        GRAPH_QL_OUTPUT_TYPE_MAP.put(objectTypeName, objectType);
    }

    private static GraphQLFieldDefinition buildSchemaField(FieldDefinition fieldDef) {
        GraphQLFieldDefinition graphQLFieldDef;
        if (fieldDef.getArgumentList().size() == 0
                && GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.containsKey(fieldDef.type)) {
            return getGraphQLFieldWithNoArgs(fieldDef);
        }

        GraphQLOutputType fieldType;
        if ("true".equals(fieldDef.isList)) {
            fieldType = getConnectionObjectType(fieldDef.type, fieldDef.nonNull, fieldDef.listItemNonNull);
        } else {
            fieldType = getGraphQLOutputType(fieldDef);
        }
        GraphQLFieldDefinition.Builder graphQLFieldDefBuilder = GraphQLFieldDefinition.newFieldDefinition()
                .name(fieldDef.name).type(fieldType).description(fieldDef.description);

        // build arguments for field
        for (ArgumentDefinition argNode : fieldDef.getArgumentList()) {
            graphQLFieldDefBuilder.argument(buildSchemaArgument(argNode));
        }

        // Add pagination argument
        if ("true".equals(fieldDef.isList)) {
            graphQLFieldDefBuilder.argument(paginationArgument);
        }
        // Add directive arguments
        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            graphQLFieldDefBuilder.argument(entry.getValue());
        }

        // TO-DO - Use of method is deprecated. Need to replace it with coderegistry
        // implementation.
        if (fieldDef.dataFetcher != null) {
            // System.out.println("fieldDef.parent "+fieldDef.parent+", fieldDef.name
            // "+fieldDef.name);
            // System.out.println("fieldDef name "+fieldDef.name+", field "+fieldDef);
            codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates(fieldDef.parent, fieldDef.name),
                    fieldDef.dataFetcher);
        }
        graphQLFieldDef = graphQLFieldDefBuilder.build();
        return graphQLFieldDef;
    }

    private static GraphQLOutputType getGraphQLOutputType(FieldDefinition fieldDef) {
        return getGraphQLOutputType(fieldDef.type, fieldDef.nonNull, fieldDef.isList, fieldDef.listItemNonNull);
    }

    private static GraphQLOutputType getGraphQLOutputType(String rawTypeName, String nonNull, String isList,
            String listItemNonNull) {
        GraphQLOutputType rawType = GRAPH_QL_OUTPUT_TYPE_MAP.get(rawTypeName);
        if (rawType == null) {
            rawType = GRAPH_QL_TYPE_REFERENCE_MAP.get(rawTypeName);
            if (rawType == null) {
                rawType = new GraphQLTypeReference(rawTypeName);
                GRAPH_QL_TYPE_REFERENCE_MAP.put(rawTypeName, (GraphQLTypeReference) rawType);
            }
        }
        return getGraphQLOutputType(rawType, nonNull, isList, listItemNonNull);
    }

    private static GraphQLOutputType getGraphQLOutputType(GraphQLOutputType rawType, String nonNull, String isList,
            String listItemNonNull) {
        String outputTypeKey = rawType.getName();
        if ("true".equals(nonNull)) {
            outputTypeKey = outputTypeKey + NON_NULL_SUFFIX;
        }
        if ("true".equals(isList)) {
            outputTypeKey = outputTypeKey + IS_LIST_SUFFIX;
            if ("true".equals(listItemNonNull)) {
                outputTypeKey = outputTypeKey + LIST_ITEM_NON_NULL_SUFFIX;
            }
        }

        GraphQLOutputType wrappedType = GRAPH_QL_OUTPUT_TYPE_MAP.get(outputTypeKey);
        if (wrappedType != null) {
            return wrappedType;
        }

        wrappedType = rawType;
        if ("true".equals(isList)) {
            if ("true".equals(listItemNonNull)) {
                wrappedType = new GraphQLNonNull(wrappedType);
            }
            wrappedType = new GraphQLList(wrappedType);
        }
        if ("true".equals(nonNull)) {
            wrappedType = new GraphQLNonNull(wrappedType);
        }

        if (!outputTypeKey.equals(rawType.getName())) {
            GRAPH_QL_OUTPUT_TYPE_MAP.put(outputTypeKey, wrappedType);
        }

        return wrappedType;
    }

    private static GraphQLArgument buildSchemaArgument(ArgumentDefinition argumentDef) {
        String argumentName = argumentDef.getName();
        GraphQLArgument.Builder argument = GraphQLArgument.newArgument().name(argumentName)
                .description(argumentDef.getDescription());

        if (UtilValidate.isNotEmpty(argumentDef.getDefaultValue())) {
            argument.defaultValue(argumentDef.getDefaultValue());
        }

        GraphQLInputType argType = GRAPH_QL_INPUT_TYPE_MAP.get(argumentDef.getType());
        if (argType == null) {
            throw new IllegalArgumentException(
                    "GraphQLInputType [" + argumentDef.getType() + "] for argument [" + argumentName + "] not found");
        }

        if (argumentDef.isList) {
            argType = new GraphQLList(argType);
        }

        if (argumentDef.getRequired() != null && argumentDef.getRequired().equalsIgnoreCase("true")) {
            argument = argument.type(new GraphQLNonNull(argType));
        } else {
            argument = argument.type(argType);
        }
        return argument.build();
    }

    private static GraphQLFieldDefinition getGraphQLFieldWithNoArgs(FieldDefinition fieldDef) {
        if (fieldDef.getArgumentList().size() > 0) {
            throw new IllegalArgumentException("FieldDefinition [" + fieldDef.name + " ] with type [" + fieldDef.type
                    + "] has arguments, which should not be cached");
        }
        return getGraphQLFieldWithNoArgs(fieldDef.parent, fieldDef.name, fieldDef.type, fieldDef.nonNull,
                fieldDef.isList, fieldDef.listItemNonNull, fieldDef.description, fieldDef.dataFetcher);
    }

    private static GraphQLFieldDefinition getGraphQLFieldWithNoArgs(String parent, String name,
            GraphQLOutputType rawType, String description) {
        return getGraphQLFieldWithNoArgs(parent, name, rawType, "false", "false", "false", description, null);
    }

    private static GraphQLFieldDefinition getGraphQLFieldWithNoArgs(String parent, String name, String rawTypeName,
            String nonNull, String isList, String listItemNonNull, String description, BaseDataFetcher dataFetcher) {
        GraphQLOutputType rawType = GRAPH_QL_OUTPUT_TYPE_MAP.get(rawTypeName);
        if (rawType == null) {
            rawType = GRAPH_QL_TYPE_REFERENCE_MAP.get(rawTypeName);
            if (rawType == null) {
                rawType = new GraphQLTypeReference(rawTypeName);
                GRAPH_QL_TYPE_REFERENCE_MAP.put(rawTypeName, (GraphQLTypeReference) rawType);
            }
        }
        return getGraphQLFieldWithNoArgs(parent, name, rawType, nonNull, isList, listItemNonNull, description,
                dataFetcher);
    }

    private static GraphQLFieldDefinition getGraphQLFieldWithNoArgs(String parent, String name,
            GraphQLOutputType rawType, String nonNull, String isList, String listItemNonNull,
            BaseDataFetcher dataFetcher) {
        return getGraphQLFieldWithNoArgs(parent, name, rawType, nonNull, isList, listItemNonNull, "", dataFetcher);
    }

    private static GraphQLFieldDefinition getGraphQLFieldWithNoArgs(String parent, String name,
            GraphQLOutputType rawType, String nonNull, String isList, String listItemNonNull, String description,
            BaseDataFetcher dataFetcher) {
        String fieldKey = getFieldKey(name, rawType.getName(), nonNull, isList, listItemNonNull);

        GraphQLFieldDefinition field = GRAPH_QL_FIELD_MAP.get(fieldKey);
        if (field != null) {
            return field;
        }

        GraphQLOutputType fieldType = null;

        if ("true".equals(isList)) {
            fieldType = getConnectionObjectType(rawType, nonNull, listItemNonNull);
        } else {
            fieldType = getGraphQLOutputType(rawType, nonNull, "false", listItemNonNull);
        }

        GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition().name(name)
                .description(description);
        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            fieldBuilder.argument(entry.getValue());
        }

        fieldBuilder.type(fieldType);

        if ("true".equals(isList)) {
            fieldBuilder.argument(paginationArgument);
        }
        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            fieldBuilder.argument((GraphQLArgument) entry.getValue());
        }

        if (dataFetcher != null) {
            codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates(parent, name), dataFetcher);
        }
        field = fieldBuilder.build();
        GRAPH_QL_FIELD_MAP.put(fieldKey, field);
        return field;
    }

    private static GraphQLOutputType getConnectionObjectType(String rawTypeName, String nonNull,
            String listItemNonNull) {
        GraphQLOutputType rawType = GRAPH_QL_OUTPUT_TYPE_MAP.get(rawTypeName);
        if (rawType == null) {
            rawType = GRAPH_QL_TYPE_REFERENCE_MAP.get(rawTypeName);
            if (rawType == null) {
                rawType = new GraphQLTypeReference(rawTypeName);
                System.out.println("Adding GRAPH_QL_TYPE_REFERENCE_MAP: ${rawTypeName}");
                GRAPH_QL_TYPE_REFERENCE_MAP.put(rawTypeName, (GraphQLTypeReference) rawType);

            }
        }
        return getConnectionObjectType(rawType, nonNull, listItemNonNull);
    }

    private static GraphQLOutputType getConnectionObjectType(GraphQLOutputType rawType, String nonNull,
            String listItemNonNull) {
        String connectionTypeName = rawType.getName() + "Connection";
        String connectionTypeKey = rawType.getName();
        if ("true".equals(nonNull)) {
            connectionTypeKey = connectionTypeKey + NON_NULL_SUFFIX;
        }
        connectionTypeKey = connectionTypeKey + IS_LIST_SUFFIX;
        if ("true".equals(listItemNonNull)) {
            connectionTypeKey = connectionTypeKey + LIST_ITEM_NON_NULL_SUFFIX;
        }
        GraphQLOutputType wrappedConnectionType = GRAPH_QL_OUTPUT_TYPE_MAP.get(connectionTypeKey);
        if (wrappedConnectionType != null) {
            return wrappedConnectionType;
        }
        GraphQLOutputType connectionType = GRAPH_QL_OUTPUT_TYPE_MAP.get(connectionTypeName);
        if (connectionType == null) {
            connectionType = GraphQLObjectType.newObject().name(connectionTypeName)
                    .field(getEdgesField(rawType, nonNull, listItemNonNull))
                    .field(getGraphQLFieldWithNoArgs(connectionTypeName, "pageInfo", pageInfoType, "false", "false",
                            "false", null))
                    .build();
            GRAPH_QL_OUTPUT_TYPE_MAP.put(connectionTypeName, connectionType);
        }

        wrappedConnectionType = connectionType;
        if ("true".equals(nonNull)) {
            wrappedConnectionType = new GraphQLNonNull(connectionType);
        }

        if (!connectionTypeKey.equals(connectionTypeName)) {
            GRAPH_QL_OUTPUT_TYPE_MAP.put(connectionTypeKey, wrappedConnectionType);
        }

        return wrappedConnectionType;
    }

    private static GraphQLFieldDefinition getEdgesField(GraphQLOutputType rawType, String nonNull,
            String listItemNonNull) {
        String edgesFieldName = "edges";
        String edgeFieldKey = edgesFieldName + KEY_SPLITTER + rawType.getName() + "Edge";
        if ("true".equals(nonNull)) {
            edgeFieldKey = edgeFieldKey + NON_NULL_SUFFIX;
        }
        edgeFieldKey = edgeFieldKey + IS_LIST_SUFFIX;
        if ("true".equals(listItemNonNull)) {
            edgeFieldKey = edgeFieldKey + LIST_ITEM_NON_NULL_SUFFIX;
        }
        GraphQLFieldDefinition edgesField = GRAPH_QL_FIELD_MAP.get(edgeFieldKey);
        if (edgesField != null) {
            return edgesField;
        }
        GraphQLFieldDefinition.Builder edgesFieldBuilder = GraphQLFieldDefinition.newFieldDefinition()
                .name(edgesFieldName).type(getEdgesObjectType(rawType, nonNull, listItemNonNull));

        for (Map.Entry<String, GraphQLArgument> entry : GRAPH_QL_DIRECTIVE_ARGUMENT_MAP.entrySet()) {
            edgesFieldBuilder.argument(entry.getValue());
        }
        edgesField = edgesFieldBuilder.build();
        GRAPH_QL_FIELD_MAP.put(edgeFieldKey, edgesField);

        return edgesField;
    }

    private static GraphQLOutputType getEdgesObjectType(GraphQLOutputType rawType, String nonNull,
            String listItemNonNull) {
        String edgeRawTypeName = rawType.getName() + "Edge";
        String edgesTypeKey = edgeRawTypeName;
        if ("true".equals(nonNull)) {
            edgesTypeKey = edgesTypeKey + NON_NULL_SUFFIX;
        }
        edgesTypeKey = edgesTypeKey + IS_LIST_SUFFIX;
        if ("true".equals(listItemNonNull)) {
            edgesTypeKey = edgesTypeKey + LIST_ITEM_NON_NULL_SUFFIX;
        }
        GraphQLOutputType edgesType = GRAPH_QL_OUTPUT_TYPE_MAP.get(edgesTypeKey);
        if (edgesType != null) {
            return edgesType;
        }
        GraphQLObjectType edgeRawType = GRAPH_QL_OBJECT_TYPE_MAP.get(edgeRawTypeName);
        if (edgeRawType == null) {
            GraphQLFieldDefinition nodeField = getGraphQLFieldWithNoArgs(edgeRawTypeName, "node", rawType, nonNull,
                    "false", listItemNonNull, null);

            edgeRawType = GraphQLObjectType.newObject().name(edgeRawTypeName).field(cursorField).field(nodeField)
                    .build();
            GRAPH_QL_OBJECT_TYPE_MAP.put(edgeRawTypeName, edgeRawType);
            GRAPH_QL_OUTPUT_TYPE_MAP.put(edgeRawTypeName, edgeRawType);
        }

        edgesType = edgeRawType;

        if ("true".equals(listItemNonNull)) {
            edgesType = new GraphQLNonNull(edgesType);
        }
        edgesType = new GraphQLList(edgesType);
        if ("true".equals(nonNull)) {
            edgesType = new GraphQLNonNull(edgesType);
        }
        if (!edgesTypeKey.equals(edgeRawTypeName)) {
            GRAPH_QL_OUTPUT_TYPE_MAP.put(edgesTypeKey, edgesType);
        }
        return edgesType;
    }

    // Create InputObjectType (Input) for mutation fields
    private void addSchemaInputObjectTypes() {
        for (Map.Entry<String, GraphQLTypeDefinition> entry : allTypeDefMap.entrySet()) {
            if (!(entry.getValue() instanceof ObjectTypeDefinition)) {
                continue;
            }
            for (FieldDefinition fieldDef : ((ObjectTypeDefinition) entry.getValue()).getFieldList()) {
                if (fieldDef.isMutation) {
                    if (fieldDef.dataFetcher == null) {
                        throw new IllegalArgumentException("FieldDefinition [" + fieldDef.name + "] - [" + fieldDef.type
                                + "] as mutation must have a data fetcher");
                    }
                    if (fieldDef.dataFetcher instanceof EmptyDataFetcher) {
                        throw new IllegalArgumentException("FieldDefinition [" + fieldDef.name + "] - [" + fieldDef.type
                                + "] as mutation can't have empty data fetcher");
                    }
                }

                if (fieldDef.dataFetcher instanceof ServiceDataFetcher && fieldDef.isMutation) {
                    String serviceName = ((ServiceDataFetcher) fieldDef.dataFetcher).getServiceName();
                    String inputTypeName = GraphQLSchemaUtil.camelCaseToUpperCamel(fieldDef.name) + "Input";

                    boolean isEntityAutoService = ((ServiceDataFetcher) fieldDef.dataFetcher).isEntityAutoService();

                    Map<String, InputObjectFieldDefinition> inputFieldMap;
                    if (isEntityAutoService) {
                        // Entity Auto Service only works for mutation which is checked in
                        // ServiceDataFetcher initialization.
                        String verb = GraphQLSchemaUtil.getVerbFromName(serviceName, dispatcher);
                        String entityName = GraphQLSchemaUtil.getDefaultEntityName(serviceName, dispatcher);
                        ModelEntity entity = GraphQLSchemaUtil.getEntityDefinition(entityName, delegator);
                        List<String> allFields = verb.equals("delete") ? entity.getPkFieldNames()
                                : entity.getAllFieldNames();
                        inputFieldMap = new LinkedHashMap<>(allFields.size());
                        for (int i = 0; i < allFields.size(); i++) {
                            ModelField fi = entity.getField(allFields.get(i));
                            String inputFieldType = GraphQLSchemaUtil.FIELD_TYPE_GRAPH_QL_MAP.get(fi.getType());
                            Object defaultValue = null;
                            InputObjectFieldDefinition inputFieldDef = new InputObjectFieldDefinition(fi.getName(),
                                    inputFieldType, defaultValue, "");
                            inputFieldMap.put(fi.getName(), inputFieldDef);
                        }

                    } else {
                        ModelService sd = GraphQLSchemaUtil.getServiceDefinition(serviceName, dispatcher);
                        inputFieldMap = new LinkedHashMap<>(sd.getInParamNames().size());
                        for (String parmName : sd.getInParamNames()) {
                            ModelParam parmNode = sd.getParam(parmName);
                            boolean isInternal = parmNode.getInternal();
                            String entityName = parmNode.getEntityName();
                            if (isInternal || ((parmNode.getType().equals("List") || parmNode.getType().equals("Map")
                                    || parmNode.getType().equals("Set")) && UtilValidate.isEmpty(entityName))) {
                                continue;
                            }
                            Object defaultValue = null;
                            boolean inputFieldNonNull = !parmNode.isOptional();
                            boolean inputFieldIsList = GraphQLSchemaUtil.getShortJavaType(parmNode.getType())
                                    .equals("List") ? true : false;
                            GraphQLInputType fieldInputType = getInputTypeRecursiveInSD(parmNode, inputTypeName);
                            InputObjectFieldDefinition inputFieldDef = new InputObjectFieldDefinition(parmName,
                                    fieldInputType.getName(), defaultValue, "", inputFieldNonNull, inputFieldIsList,
                                    false);
                            inputFieldMap.put(parmName, inputFieldDef);
                        }
                    }

                    GraphQLInputObjectType.Builder inputObjectTypeBuilder = GraphQLInputObjectType.newInputObject()
                            .name(inputTypeName).description("Autogenerated input type of " + inputTypeName);

                    for (Map.Entry<String, InputObjectFieldDefinition> inputFieldEntry : inputFieldMap.entrySet()) {
                        InputObjectFieldDefinition inputFieldDef = inputFieldEntry.getValue();
                        if ("clientMutationId".equals(inputFieldDef.name)) {
                            continue;
                        }

                        inputObjectTypeBuilder.field(buildSchemaInputField(inputFieldDef));
                    }
                    inputObjectTypeBuilder.field(clientMutationIdInputField);
                    GraphQLInputObjectType inputObjectType = inputObjectTypeBuilder.build();
                    GRAPH_QL_INPUT_TYPE_MAP.put(inputTypeName, inputObjectType);

                }

                if (fieldDef.dataFetcher instanceof ServiceDataFetcher && !fieldDef.isMutation) {
                    String serviceName = ((ServiceDataFetcher) fieldDef.dataFetcher).getServiceName();
                    String inputTypeName = GraphQLSchemaUtil.camelCaseToUpperCamel(fieldDef.name);
                    boolean isEntityAutoService = ((ServiceDataFetcher) fieldDef.dataFetcher).isEntityAutoService();
                    if (isEntityAutoService) {
                        throw new IllegalArgumentException("Entity auto service is not supported for query field");
                    } else {
                        ModelService sd = GraphQLSchemaUtil.getServiceDefinition(serviceName, dispatcher);
                        for (String parmName : sd.getParameterNames("IN", true, false)) {
                            ModelParam parmNode = sd.getParam(parmName);
                            getInputTypeRecursiveInSD(parmNode, inputTypeName);
                        }
                    }
                }

            }

        }

    }

    private GraphQLInputType getInputTypeRecursiveInSD(ModelField field, String inputTypeNamePrefix) {

        if (field == null) {
            return GraphQLString;
        }

        String parmType = field.getType();
        String inputTypeName = GraphQLSchemaUtil.getGraphQLTypeNameBySQLType(parmType);
        GraphQLScalarType scalarType = GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.get(inputTypeName);
        if (scalarType != null) {
            GRAPH_QL_INPUT_TYPE_MAP.put(inputTypeName, scalarType);
            return scalarType;
        }
        return null;
    }

    private GraphQLInputType getInputTypeRecursiveInSD(ModelParam node, String inputTypeNamePrefix) {
        // default to String
        if (node == null) {
            return GraphQLString;
        }

        String parmName = node.getName();
        String parmType = node.getType();
        String entityName = node.getEntityName();
        String inputTypeName = GraphQLSchemaUtil.getGraphQLTypeNameByJava(parmType);
        GraphQLScalarType scalarType = GraphQLSchemaUtil.GRAPH_QL_SCALAR_TYPE_MAP.get(inputTypeName);
        if (scalarType != null) {
            return scalarType;
        }

        inputTypeName = inputTypeNamePrefix + '_' + parmName;
        GraphQLInputType inputType = GRAPH_QL_INPUT_TYPE_MAP.get(inputTypeName);
        if (inputType != null) {
            return inputType;
        }

        switch (parmType) {
        case "List":
            if (entityName != null) {
                GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(inputTypeName);
                ModelEntity entity = GraphQLSchemaUtil.getEntityDefinition(entityName, delegator);
                if (entity != null) {
                    for (String fieldName : entity.getAllFieldNames()) {
                        ModelField field = entity.getField(fieldName);
                        GraphQLInputType mapEntryRawType = getInputTypeRecursiveInSD(field, inputTypeName);
                        GraphQLInputObjectField inputObjectField = GraphQLInputObjectField.newInputObjectField()
                                .name(fieldName)
                                .type(getGraphQLInputType(mapEntryRawType, field.getIsNotNull(), false, false)).build();
                        builder.field(inputObjectField);
                    }
                    inputType = builder.build();
                }

            }
            break;
        case "Map":
            if (entityName != null) {
                GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject().name(inputTypeName);
                ModelEntity entity = GraphQLSchemaUtil.getEntityDefinition(entityName, delegator);
                if (entity != null) {
                    for (String fieldName : entity.getAllFieldNames()) {
                        ModelField field = entity.getField(fieldName);
                        GraphQLInputType mapEntryRawType = getInputTypeRecursiveInSD(field, inputTypeName);
                        GraphQLInputObjectField inputObjectField = GraphQLInputObjectField.newInputObjectField()
                                .name(fieldName)
                                .type(getGraphQLInputType(mapEntryRawType, field.getIsNotNull(), false, false)).build();
                        builder.field(inputObjectField);
                    }
                    inputType = builder.build();
                }

            }
            break;
        case "org.apache.ofbiz.graphql.schema.PaginationInputType":
            return paginationInputType;
        case "org.apache.ofbiz.graphql.schema.OperationInputType":
            return operationInputType;
        case "org.apache.ofbiz.graphql.schema.DateRangeInputType":
            return dateRangeInputType;
        case "graphql.schema.DataFetchingEnvironment":
            return null;
        default:
            throw new IllegalArgumentException(
                    "Type " + inputTypeName + " - " + parmType + " for input field is not supported");
        }

        GRAPH_QL_INPUT_TYPE_MAP.put(inputTypeName, inputType);
        return inputType;
    }

    private static GraphQLInputType getGraphQLInputType(InputObjectFieldDefinition inputFieldDef) {
        return getGraphQLInputType(inputFieldDef.type, inputFieldDef.nonNull, inputFieldDef.isList(),
                inputFieldDef.listItemNonNull);
    }

    private static GraphQLInputType getGraphQLInputType(String rawTypeName, boolean nonNull, boolean isList,
            boolean listItemNonNull) {
        GraphQLInputType rawType = GRAPH_QL_INPUT_TYPE_MAP.get(rawTypeName);
        if (rawType == null) {
            rawType = GRAPH_QL_TYPE_REFERENCE_MAP.get(rawTypeName);
            if (rawType == null) {
                rawType = new GraphQLTypeReference(rawTypeName);
                GRAPH_QL_TYPE_REFERENCE_MAP.put(rawTypeName, (GraphQLTypeReference) rawType);
            }
        }
        return getGraphQLInputType(rawType, nonNull, isList, listItemNonNull);
    }

    private static GraphQLInputType getGraphQLInputType(GraphQLInputType rawType, boolean nonNull, boolean isList,
            boolean listItemNonNull) {
        String inputTypeKey = rawType.getName();
        if (nonNull) {
            inputTypeKey = inputTypeKey + NON_NULL_SUFFIX;
        }
        if (isList) {
            inputTypeKey = inputTypeKey + IS_LIST_SUFFIX;
            if (listItemNonNull) {
                inputTypeKey = inputTypeKey + LIST_ITEM_NON_NULL_SUFFIX;
            }

        }

        GraphQLInputType wrappedType = GRAPH_QL_INPUT_TYPE_MAP.get(inputTypeKey);
        if (wrappedType != null) {
            return wrappedType;
        }
        wrappedType = rawType;
        if (isList) {
            if (listItemNonNull) {
                wrappedType = new GraphQLNonNull(wrappedType);
            }
            wrappedType = new GraphQLList(wrappedType);
        }
        if (nonNull) {
            wrappedType = new GraphQLNonNull(wrappedType);
        }
        if (!inputTypeKey.equals(rawType.getName())) {
            GRAPH_QL_INPUT_TYPE_MAP.put(inputTypeKey, wrappedType);
        }
        return wrappedType;
    }

    private static GraphQLInputObjectField buildSchemaInputField(InputObjectFieldDefinition inputFieldDef) {
        String inputFieldKey = getInputFieldKey(inputFieldDef);
        GraphQLInputObjectField inputObjectField = GRAPH_QL_INPUT_OBJECT_FIELD_MAP.get(inputFieldKey);
        if (inputObjectField != null) {
            return inputObjectField;
        }

        GraphQLInputType rawType = GRAPH_QL_INPUT_TYPE_MAP.get(inputFieldDef.type);

        GraphQLInputType wrapperType = rawType;
        if (inputFieldDef.isList()) {
            if (inputFieldDef.listItemNonNull) {
                wrapperType = new GraphQLNonNull(wrapperType);
            }
            wrapperType = new GraphQLList(wrapperType);
        }
        if (inputFieldDef.nonNull) {
            wrapperType = new GraphQLNonNull(wrapperType);
        }

        GraphQLInputObjectField inputField = GraphQLInputObjectField.newInputObjectField().name(inputFieldDef.name)
                .type(wrapperType).defaultValue(inputFieldDef.defaultValue).description(inputFieldDef.description)
                .build();

        GRAPH_QL_INPUT_OBJECT_FIELD_MAP.put(inputFieldKey, inputField);
        return inputField;
    }

    private static int unknownInputDefaultValueNum = 0;

    private static String getInputFieldKey(InputObjectFieldDefinition inputFieldDef) {
        return getInputFieldKey(inputFieldDef.name, inputFieldDef.type, inputFieldDef.defaultValue,
                inputFieldDef.isNonNull(), inputFieldDef.isList(), inputFieldDef.listItemNonNull);
    }

    private static String getInputFieldKey(String name, String type, Object defaultValue) {
        return getInputFieldKey(name, type, defaultValue, false, false, false);
    }

    private static String getInputFieldKey(String name, String type, Object defaultValue, boolean nonNull,
            boolean isList, boolean listItemNonNull) {
        String defaultValueKey;
        if (defaultValue == null) {
            defaultValueKey = "NULL";
        } else {
            // TODO: generate a unique key based on defaultValue
            defaultValueKey = "UNKNOWN" + Integer.toString(unknownInputDefaultValueNum);
            unknownInputDefaultValueNum++;
        }

        String inputFieldKey = name + KEY_SPLITTER + type + KEY_SPLITTER + defaultValueKey;
        if (nonNull) {
            inputFieldKey = inputFieldKey + NON_NULL_SUFFIX;
        }
        if (isList) {
            inputFieldKey = inputFieldKey + IS_LIST_SUFFIX;
            if (listItemNonNull) {
                inputFieldKey = inputFieldKey + LIST_ITEM_NON_NULL_SUFFIX;
            }
        }

        return inputFieldKey;
    }

}
