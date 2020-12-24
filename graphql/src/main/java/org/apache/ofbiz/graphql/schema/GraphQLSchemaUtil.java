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

import static graphql.Scalars.GraphQLBigDecimal;
import static graphql.Scalars.GraphQLBigInteger;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLByte;
import static graphql.Scalars.GraphQLChar;
import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLLong;
import static graphql.Scalars.GraphQLShort;
import static graphql.Scalars.GraphQLString;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelReader;
import org.apache.ofbiz.graphql.Scalars;
import org.apache.ofbiz.graphql.fetcher.EntityDataFetcher;
import org.apache.ofbiz.graphql.fetcher.ServiceDataFetcher;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.ArgumentDefinition;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.FieldDefinition;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.GraphQLTypeDefinition;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.ObjectTypeDefinition;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.w3c.dom.Element;

import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLScalarType;

@SuppressWarnings({"unchecked", "rawtypes", "cast", "deprecation"})
public class GraphQLSchemaUtil {

    public static final Map<String, GraphQLScalarType> GRAPH_QL_SCALAR_TYPE_MAP = new HashMap<String, GraphQLScalarType>();
    public static final Map<String, String> FIELD_TYPE_GRAPH_QL_MAP = new HashMap<String, String>();
    public static final Map<String, String> JAVA_TYPE_GRAPH_QL_MAP = new HashMap<String, String>();

    public static final List<String> GRAPH_QL_STRING_TYPES = Arrays.asList("String", "ID", "Char");
    public static final List<String> GRAPHS_QL_DATE_TYPES = Arrays.asList("Timestamp");
    public static final List<String> GRAPHS_QL_NUMERIC_TYPES = Arrays.asList("Int", "Long", "Float", "BigInteger", "BigDecimal", "Short");
    public static final List<String> GRAPHS_QL_BOOL_TYPES = Arrays.asList("Boolean");

    static {
        GRAPH_QL_SCALAR_TYPE_MAP.put("Int", GraphQLInt);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Float", GraphQLFloat);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Boolean", GraphQLBoolean);
        GRAPH_QL_SCALAR_TYPE_MAP.put("BigInteger", GraphQLBigInteger);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Byte", GraphQLByte);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Char", GraphQLChar);
        GRAPH_QL_SCALAR_TYPE_MAP.put("String", GraphQLString);
        GRAPH_QL_SCALAR_TYPE_MAP.put("ID", GraphQLID);
        GRAPH_QL_SCALAR_TYPE_MAP.put("BigDecimal", GraphQLBigDecimal);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Short", GraphQLShort);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Long", GraphQLLong);
        GRAPH_QL_SCALAR_TYPE_MAP.put("Timestamp", Scalars.getGraphQLDateTime());
        GRAPH_QL_SCALAR_TYPE_MAP.put("DateTime", Scalars.getGraphQLDateTime());

        FIELD_TYPE_GRAPH_QL_MAP.put("id", "ID");
        FIELD_TYPE_GRAPH_QL_MAP.put("indicator", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("date", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("id-vlong", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("description", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("numeric", "Int"); //
        FIELD_TYPE_GRAPH_QL_MAP.put("long-varchar", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("id-long", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("currency-amount", "BigDecimal");
        FIELD_TYPE_GRAPH_QL_MAP.put("value", "value");
        FIELD_TYPE_GRAPH_QL_MAP.put("email", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("currency-precise", "BigDecimal");
        FIELD_TYPE_GRAPH_QL_MAP.put("very-short", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("date-time", "Timestamp");
        FIELD_TYPE_GRAPH_QL_MAP.put("credit-card-date", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("url", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("credit-card-number", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("fixed-point", "BigDecimal");
        FIELD_TYPE_GRAPH_QL_MAP.put("name", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("short-varchar", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("comment", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("time", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("very-long", "String");
        FIELD_TYPE_GRAPH_QL_MAP.put("floating-point", "Float");
        FIELD_TYPE_GRAPH_QL_MAP.put("object", "Byte");
        FIELD_TYPE_GRAPH_QL_MAP.put("byte-array", "Byte");
        FIELD_TYPE_GRAPH_QL_MAP.put("blob", "Byte");

        JAVA_TYPE_GRAPH_QL_MAP.put("String", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.String", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("CharSequence", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.CharSequence", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("Date", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.sql.Date", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("Time", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.sql.Time", "String");
        JAVA_TYPE_GRAPH_QL_MAP.put("Timestamp", "Timestamp");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.sql.Timestamp", "Timestamp");
        JAVA_TYPE_GRAPH_QL_MAP.put("Integer", "Int");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.Integer", "Int");
        JAVA_TYPE_GRAPH_QL_MAP.put("Long", "Long");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.Long", "Long");
        JAVA_TYPE_GRAPH_QL_MAP.put("BigInteger", "BigInteger");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.math.BigInteger", "BigInteger");
        JAVA_TYPE_GRAPH_QL_MAP.put("Float", "Float");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.Float", "Float");
        JAVA_TYPE_GRAPH_QL_MAP.put("Double", "Float");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.Double", "Float");
        JAVA_TYPE_GRAPH_QL_MAP.put("BigDecimal", "BigDecimal");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.math.BigDecimal", "BigDecimal");
        JAVA_TYPE_GRAPH_QL_MAP.put("Boolean", "Boolean");
        JAVA_TYPE_GRAPH_QL_MAP.put("java.lang.Boolean", "Boolean");

    }

    public static String camelCaseToUpperCamel(String camelCase) {
        if (camelCase == null || camelCase.length() == 0) {
            return "";
        }
        return Character.toString(Character.toUpperCase(camelCase.charAt(0))) + camelCase.substring(1);
    }

    static void createObjectTypeNodeForAllEntities(Delegator delegator, LocalDispatcher dispatcher,
                                                   Map<String, GraphQLTypeDefinition> allTypeNodeMap) {

        List<ModelEntity> entities = getAllEntities(delegator, "org.apache.ofbiz", true);
        for (ModelEntity entity : entities) {
            addObjectTypeNode(delegator, dispatcher, entity, true, allTypeNodeMap);
        }
    }

    private static void addObjectTypeNode(Delegator delegator, LocalDispatcher dispatcher, ModelEntity ed,
                                          boolean standalone, Map<String, GraphQLTypeDefinition> allTypeDefMap) {
        String objectTypeName = ed.getEntityName();
        if (allTypeDefMap.containsKey(objectTypeName)) {
            return;
        }
        Map<String, FieldDefinition> fieldDefMap = new LinkedHashMap<>();
        List<String> allFields = ed.getAllFieldNames();

        if (!allFields.contains("id")) {
            // Add a id field to all entity Object Type
            GraphQLSchemaDefinition.FieldDefinition idFieldDef = GraphQLSchemaDefinition.getCachedFieldDefinition("id",
                    "ID", "false", "false", "false");
            if (idFieldDef == null) {
                idFieldDef = new GraphQLSchemaDefinition.FieldDefinition(null, delegator, dispatcher, "id", "ID",
                        new HashMap<String, String>());
                GraphQLSchemaDefinition.putCachedFieldDefinition(idFieldDef);
            }
            fieldDefMap.put("id", idFieldDef);
        }

        for (String fieldName : allFields) {
            ModelField field = ed.getField(fieldName);
            String fieldScalarType = FIELD_TYPE_GRAPH_QL_MAP.get(field.getType());
            Map<String, String> fieldPropertyMap = new HashMap<>();
            if (field.getIsPk() || field.getIsNotNull()) {
                fieldPropertyMap.put("nonNull", "true");
            }
            fieldPropertyMap.put("description",
                    UtilValidate.isEmpty(field.getDescription()) ? "" : field.getDescription());
            FieldDefinition fieldDef = GraphQLSchemaDefinition.getCachedFieldDefinition(fieldName, fieldScalarType,
                    fieldPropertyMap.get("nonNull"), "false", "false");
            if (fieldDef == null) {
                fieldDef = new FieldDefinition(objectTypeName, delegator, dispatcher, fieldName, fieldScalarType, fieldPropertyMap);
                GraphQLSchemaDefinition.putCachedFieldDefinition(fieldDef);
            }
            fieldDefMap.put(fieldName, fieldDef);

        }

        ObjectTypeDefinition objectTypeDef = new ObjectTypeDefinition(delegator, dispatcher, objectTypeName,
                ed.getDescription(), new ArrayList<String>(), fieldDefMap);
        allTypeDefMap.put(objectTypeName, objectTypeDef);

    }

    private static List<ModelEntity> getAllEntities(Delegator delegator, String groupName,
                                                    boolean excludeViewEntities) {
        List<ModelEntity> entities = new ArrayList<ModelEntity>();
        ModelReader reader = delegator.getModelReader();
        TreeSet<String> entityNames = null;
        try {
            entityNames = new TreeSet<String>(reader.getEntityNames());
        } catch (GenericEntityException e) {
        }
        entityNames.forEach(entityName -> {
            try {
                final ModelEntity entity = reader.getModelEntity(entityName);
                entities.add(entity);
            } catch (Exception e) {

            }
        });

        return entities;
    }

    public static void transformArguments(Map<String, Object> arguments, Map<String, Object> inputFieldsMap, Map<String, Object> operationMap) {
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String argName = entry.getKey();
            // Ignore if argument which is used for directive @include and @skip
            if ("if".equals(argName)) {
                continue;
            }
            Object argValue = entry.getValue();
            if (argValue == null) {
                continue;
            }

            if (argValue instanceof LinkedHashMap) {
                Map argValueMap = (LinkedHashMap) argValue;
                if ("input".equals(argName)) {
                    argValueMap.forEach((k, v) -> {
                        inputFieldsMap.put((String) k, v);
                    });
                    continue;
                }

                if (argValueMap.get("value") != null) {
                    operationMap.put(argName, argValueMap.get("value"));
                }
                if (argValueMap.get("op") != null) {
                    operationMap.put(argName + "_op", argValueMap.get("op"));
                }
                if (argValueMap.get("not") != null) {
                    operationMap.put(argName + "_not", argValueMap.get("not"));
                }
                if (argValueMap.get("ic") != null) {
                    operationMap.put(argName + "_ic", argValueMap.get("ic"));
                }
                operationMap.put("pageIndex", argValueMap.get("pageIndex") != null ? argValueMap.get("pageIndex") : 0);
                operationMap.put("pageSize", argValueMap.get("pageSize") != null ? argValueMap.get("pageSize") : 20);
                if (argValueMap.get("pageNoLimit") != null) {
                    operationMap.put("pageNoLimit", argValueMap.get("pageNoLimit"));
                }
                if (argValueMap.get("orderByField") != null) {
                    operationMap.put("orderByField", argValueMap.get("orderByField"));
                }

                if (argValueMap.get("period") != null) {
                    operationMap.put(argName + "_period", argValueMap.get("period"));
                }
                if (argValueMap.get("poffset") != null) {
                    operationMap.put(argName + "_poffset", argValueMap.get("poffset"));
                }
                if (argValueMap.get("from") != null) {
                    operationMap.put(argName + "_from", argValueMap.get("from"));
                }
                if (argValueMap.get("thru") != null) {
                    operationMap.put(argName + "_thru", argValueMap.get("thru"));
                }

            } else {
                // periodValid_ type argument is handled specially
                if (!(argName == "periodValid_" || argName.endsWith("PeriodValid_"))) {
                    inputFieldsMap.put(argName, argValue);
                }
            }
        }
    }

    public static void transformQueryServiceRelArguments(Map<String, Object> source, Map<String, String> relKeyMap,
                                                         Map<String, Object> inParameterMap) {
        for (Map.Entry<String, String> keyMapEntry : relKeyMap.entrySet()) {
            inParameterMap.put((String) keyMapEntry.getValue(), source.get(keyMapEntry.getKey()));
        }

    }

    public static void transformQueryServiceArguments(ModelService sd, Map<String, Object> arguments,
                                                      Map<String, Object> inParameterMap) {
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String paramName = entry.getKey();
            if ("if".equals(paramName)) {
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            ModelParam paramNode = sd.getParam(paramName);
            if (paramNode == null) {
                throw new IllegalArgumentException("Service " + sd.getName() + " missing in parameter " + paramName);
            }
            if (!paramNode.isIn()) {
                throw new IllegalArgumentException("The Param Was not IN");
            }
            String paramType = paramNode.getType();
            Object paramJavaTypeValue;
            switch (paramType) {
            case "org.apache.ofbiz.graphql.schema.OperationInputType":
                paramJavaTypeValue = new OperationInputType((Map) entry.getValue());
                break;
            case "org.apache.ofbiz.graphql.schema.DateRangeInputType":
                paramJavaTypeValue = new DateRangeInputType((Map) entry.getValue());
                break;
            case "org.apache.ofbiz.graphql.schema.PaginationInputType":
                paramJavaTypeValue = new PaginationInputType((Map) entry.getValue());
                break;
            default:
                paramJavaTypeValue = castValueToJavaType(entry.getValue(), paramType);
                break;
            }
            inParameterMap.put(paramName, paramJavaTypeValue);
        }

    }

    public static boolean requirePagination(DataFetchingEnvironment environment) {
        Map<String, Object> arguments = environment.getArguments();
        List<Field> fields = (List) environment.getFields();
        Map paginationArg = (Map) arguments.get("pagination");
        if (paginationArg != null && (Boolean) paginationArg.get("pageNoLimit")) {
            return false;
        }
        if (paginationArg != null) {
            return true;
        }
        int count = (int) fields.stream().filter((field) -> field.getName().equals("pageInfo")).count();
        if (count != 0) {
            return true;
        }
        return false;
    }


    static Object castValueToJavaType(Object value, String javaType) {
        switch (javaType) {
        case "String":
            return value;
        case "CharSequence":
            return value;
        case "Date":
            break; //TODO
        case "Time":
            break;  //TODO
        case "Timestamp":
            return (Timestamp) value;
        case "Integer":
            return (Integer) value;
        case "Long":
            return (Long) value;
        case "BigInteger":
            return (BigInteger) value;
        case "Float":
            return (Float) value;
        case "Double":
            return (Double) value;
        case "BigDecimal":
            return (BigDecimal) value;
        case "Boolean":
            return (Boolean) value;
        case "List":
            return (List) value;
        case "Map":
            return (Map) value;
        default:
            throw new IllegalArgumentException("Can't cast value [${value}] to Java type ${javaType}");
        }
        return null;
    }

    public static void mergeFieldDefinition(Element fieldNode, Map<String, FieldDefinition> fieldDefMap,
                                            Delegator delegator, LocalDispatcher dispatcher) {
        FieldDefinition fieldDef = fieldDefMap.get(fieldNode.getAttribute("name"));
        if (fieldDef != null) {
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("type"))) {
                fieldDef.setType(fieldNode.getAttribute("type"));
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("non-null"))) {
                fieldDef.setNonNull(fieldNode.getAttribute("non-null"));
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("is-list"))) {
                fieldDef.setIsList(fieldNode.getAttribute("is-list"));
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("list-item-non-null"))) {
                fieldDef.setListItemNonNull(fieldNode.getAttribute("list-item-non-null"));
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("require-authentication"))) {
                fieldDef.setRequireAuthentication(fieldNode.getAttribute("require-authentication"));
            }
            List<? extends Element> elements = UtilXml.childElementList(fieldNode);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    fieldDef.setDescription(childNode.getTextContent());
                    break;
                case "depreciation-reason":
                    fieldDef.setDepreciationReason(childNode.getTextContent());
                    break;
                case "auto-arguments":
                    //fieldDef.mergeArgument(new AutoArgumentsDefinition(childNode));
                    break;
                case "argument":
                    String argTypeName = GraphQLSchemaDefinition.getArgumentTypeName(childNode.getAttribute("type"), fieldDef.getIsList());
                    ArgumentDefinition argDef = GraphQLSchemaDefinition.getCachedArgumentDefinition(
                            childNode.getAttribute("name"), argTypeName, childNode.getAttribute("required"));
                    if (argDef == null) {
                        argDef = new ArgumentDefinition(childNode, fieldDef);
                        GraphQLSchemaDefinition.putCachedArgumentDefinition(argDef);
                    }
                    fieldDef.mergeArgument(argDef);
                    break;
                case "entity-fetcher":
                    fieldDef.setDataFetcher(new EntityDataFetcher(delegator, childNode, fieldDef));
                    break;
                case "service-fetcher":
                    fieldDef.setDataFetcher(new ServiceDataFetcher(childNode, fieldDef, delegator, dispatcher));
                }
            }
        } else {
            Element parentEle = (Element) fieldNode.getParentNode();
            String parent = parentEle.getAttribute("name");
            fieldDef = new FieldDefinition(parent, delegator, dispatcher, fieldNode);
            fieldDefMap.put(fieldDef.getName(), fieldDef);
        }
    }

    public static String getDefaultEntityName(String serviceName, LocalDispatcher dispatcher) {
        String defaultEntityName = null;
        try {
            ModelService service = dispatcher.getDispatchContext().getModelService(serviceName);
            if (service == null) {
                throw new IllegalArgumentException("Service " + serviceName + " not found");
            }
            defaultEntityName = service.getDefaultEntityName();
        } catch (GenericServiceException e) {
            e.printStackTrace();
        }
        return defaultEntityName;
    }

    public static ModelEntity getEntityDefinition(String entityName, Delegator delegator) {
        ModelEntity entity = null;
        try {
            entity = delegator.getModelReader().getModelEntity(entityName);
        } catch (GenericEntityException e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
        return entity;
    }

    public static String getVerbFromName(String serviceName, LocalDispatcher dispatcher) {
        String verb = null;
        ModelService service = getServiceDefinition(serviceName, dispatcher);
        if (service.getEngineName().equalsIgnoreCase("entity-auto")) {
            verb = service.getInvoke();
        }
        return verb;
    }

    public static ModelService getServiceDefinition(String serviceName, LocalDispatcher dispatcher) {
        ModelService service = null;
        try {
            service = dispatcher.getDispatchContext().getModelService(serviceName);
            if (service == null) {
                throw new IllegalArgumentException("Service " + serviceName + " not found");
            }

        } catch (GenericServiceException e) {
            e.printStackTrace();
        }

        return service;
    }

    public static String getShortJavaType(String javaType) {
        if (javaType == null) {
            return "";
        }
        String shortJavaType = javaType;
        if (javaType.contains(".")) {
            shortJavaType = javaType.substring(javaType.lastIndexOf(".") + 1);
        }
        return shortJavaType;
    }

    public static String getGraphQLTypeNameByJava(String javaType) {
        if (javaType == null) return "String";
        return JAVA_TYPE_GRAPH_QL_MAP.get(getShortJavaType(javaType));
    }

    public static String getGraphQLTypeNameBySQLType(String sqlType) {
        if (sqlType == null) return null;
        return FIELD_TYPE_GRAPH_QL_MAP.get(sqlType);
    }


    public static String encodeRelayCursor(Map<String, Object> ev, List<String> pkFieldNames) {
        return encodeRelayId(ev, pkFieldNames);
    }

    public static String encodeRelayId(Map<String, Object> ev, List<String> pkFieldNames) {
        if (pkFieldNames.size() == 0) throw new IllegalArgumentException("Entity value must have primary keys to generate id");
        Object pkFieldValue0 = ev.get(pkFieldNames.get(0));
        if (pkFieldValue0 instanceof Timestamp) pkFieldValue0 = ((Timestamp) pkFieldValue0).getTime();
        String id = (String) pkFieldValue0;
        for (int i = 1; i < pkFieldNames.size(); i++) {
            Object pkFieldValue = ev.get(pkFieldNames.get(i));
            if (pkFieldValue instanceof Timestamp) pkFieldValue = ((Timestamp) pkFieldValue).getTime();
            id = id + '|' + pkFieldValue;
        }
        return id;
    }

}
