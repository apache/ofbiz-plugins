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

import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLScalarType;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelReader;
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
import static org.apache.ofbiz.graphql.Scalars.GraphQLDateTime;

@SuppressWarnings(value = "all")
public class GraphQLSchemaUtil {

    public static final Map<String, GraphQLScalarType> graphQLScalarTypes = new HashMap<String, GraphQLScalarType>();
    public static final Map<String, String> fieldTypeGraphQLMap = new HashMap<String, String>();
    public static final Map<String, String> javaTypeGraphQLMap = new HashMap<String, String>();

    public static final List<String> graphQLStringTypes = Arrays.asList("String", "ID", "Char");
    public static final List<String> graphQLDateTypes = Arrays.asList("Timestamp");
    public static final List<String> graphQLNumericTypes = Arrays.asList("Int", "Long", "Float", "BigInteger", "BigDecimal", "Short");
    public static final List<String> graphQLBoolTypes = Arrays.asList("Boolean");

    static {
        graphQLScalarTypes.put("Int", GraphQLInt);
        graphQLScalarTypes.put("Float", GraphQLFloat);
        graphQLScalarTypes.put("Boolean", GraphQLBoolean);
        graphQLScalarTypes.put("BigInteger", GraphQLBigInteger);
        graphQLScalarTypes.put("Byte", GraphQLByte);
        graphQLScalarTypes.put("Char", GraphQLChar);
        graphQLScalarTypes.put("String", GraphQLString);
        graphQLScalarTypes.put("ID", GraphQLID);
        graphQLScalarTypes.put("BigDecimal", GraphQLBigDecimal);
        graphQLScalarTypes.put("Short", GraphQLShort);
        graphQLScalarTypes.put("Long", GraphQLLong);
        graphQLScalarTypes.put("Timestamp", GraphQLDateTime);
        graphQLScalarTypes.put("DateTime", GraphQLDateTime);

        fieldTypeGraphQLMap.put("id", "ID");
        fieldTypeGraphQLMap.put("indicator", "String");
        fieldTypeGraphQLMap.put("date", "String");
        fieldTypeGraphQLMap.put("id-vlong", "String");
        fieldTypeGraphQLMap.put("description", "String");
        fieldTypeGraphQLMap.put("numeric", "Int"); //
        fieldTypeGraphQLMap.put("long-varchar", "String");
        fieldTypeGraphQLMap.put("id-long", "String");
        fieldTypeGraphQLMap.put("currency-amount", "BigDecimal");
        fieldTypeGraphQLMap.put("value", "value");
        fieldTypeGraphQLMap.put("email", "String");
        fieldTypeGraphQLMap.put("currency-precise", "BigDecimal");
        fieldTypeGraphQLMap.put("very-short", "String");
        fieldTypeGraphQLMap.put("date-time", "Timestamp");
        fieldTypeGraphQLMap.put("credit-card-date", "String");
        fieldTypeGraphQLMap.put("url", "String");
        fieldTypeGraphQLMap.put("credit-card-number", "String");
        fieldTypeGraphQLMap.put("fixed-point", "BigDecimal");
        fieldTypeGraphQLMap.put("name", "String");
        fieldTypeGraphQLMap.put("short-varchar", "String");
        fieldTypeGraphQLMap.put("comment", "String");
        fieldTypeGraphQLMap.put("time", "String");
        fieldTypeGraphQLMap.put("very-long", "String");
        fieldTypeGraphQLMap.put("floating-point", "Float");
        fieldTypeGraphQLMap.put("object", "Byte");
        fieldTypeGraphQLMap.put("byte-array", "Byte");
        fieldTypeGraphQLMap.put("blob", "Byte");

        javaTypeGraphQLMap.put("String", "String");
        javaTypeGraphQLMap.put("java.lang.String", "String");
        javaTypeGraphQLMap.put("CharSequence", "String");
        javaTypeGraphQLMap.put("java.lang.CharSequence", "String");
        javaTypeGraphQLMap.put("Date", "String");
        javaTypeGraphQLMap.put("java.sql.Date", "String");
        javaTypeGraphQLMap.put("Time", "String");
        javaTypeGraphQLMap.put("java.sql.Time", "String");
        javaTypeGraphQLMap.put("Timestamp", "Timestamp");
        javaTypeGraphQLMap.put("java.sql.Timestamp", "Timestamp");
        javaTypeGraphQLMap.put("Integer", "Int");
        javaTypeGraphQLMap.put("java.lang.Integer", "Int");
        javaTypeGraphQLMap.put("Long", "Long");
        javaTypeGraphQLMap.put("java.lang.Long", "Long");
        javaTypeGraphQLMap.put("BigInteger", "BigInteger");
        javaTypeGraphQLMap.put("java.math.BigInteger", "BigInteger");
        javaTypeGraphQLMap.put("Float", "Float");
        javaTypeGraphQLMap.put("java.lang.Float", "Float");
        javaTypeGraphQLMap.put("Double", "Float");
        javaTypeGraphQLMap.put("java.lang.Double", "Float");
        javaTypeGraphQLMap.put("BigDecimal", "BigDecimal");
        javaTypeGraphQLMap.put("java.math.BigDecimal", "BigDecimal");
        javaTypeGraphQLMap.put("Boolean", "Boolean");
        javaTypeGraphQLMap.put("java.lang.Boolean", "Boolean");

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
            String fieldScalarType = fieldTypeGraphQLMap.get(field.getType());
            Map<String, String> fieldPropertyMap = new HashMap<>();
            if (field.getIsPk() || field.getIsNotNull()) {
                fieldPropertyMap.put("nonNull", "true");
            }
            fieldPropertyMap.put("description",
                    UtilValidate.isEmpty(field.getDescription()) ? "" : field.getDescription());
            FieldDefinition fieldDef = GraphQLSchemaDefinition.getCachedFieldDefinition(fieldName, fieldScalarType,
                    fieldPropertyMap.get("nonNull"), "false", "false");
            if (fieldDef == null) {
                //System.out.println("fieldName "+fieldName+", fieldScalarType "+fieldScalarType);
                fieldDef = new FieldDefinition(objectTypeName, delegator, dispatcher, fieldName, fieldScalarType, fieldPropertyMap);
                GraphQLSchemaDefinition.putCachedFieldDefinition(fieldDef);
            }
            fieldDefMap.put(fieldName, fieldDef);

        }

        //System.out.println("objectTypeName "+objectTypeName);
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
        Map<String, Object> arguments = (Map) environment.getArguments();
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
        System.out.println("Trying to merge here: " + fieldNode.getAttribute("name") + ", fieldDef is null ? " + (fieldDef == null));
        System.out.println("fieldDef " + fieldDef);
        if (fieldDef != null) {
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("type"))) {
                fieldDef.type = fieldNode.getAttribute("type");
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("non-null"))) {
                fieldDef.nonNull = fieldNode.getAttribute("non-null");
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("is-list"))) {
                fieldDef.isList = fieldNode.getAttribute("is-list");
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("list-item-non-null"))) {
                fieldDef.listItemNonNull = fieldNode.getAttribute("list-item-non-null");
            }
            if (UtilValidate.isNotEmpty(fieldNode.getAttribute("require-authentication"))) {
                fieldDef.requireAuthentication = fieldNode.getAttribute("require-authentication");
            }
            List<? extends Element> elements = UtilXml.childElementList(fieldNode);
            for (Element childNode : elements) {
                switch (childNode.getNodeName()) {
                case "description":
                    fieldDef.description = childNode.getTextContent();
                    break;
                case "depreciation-reason":
                    fieldDef.depreciationReason = childNode.getTextContent();
                    break;
                case "auto-arguments":
                    //fieldDef.mergeArgument(new AutoArgumentsDefinition(childNode));
                    break;
                case "argument":
                    String argTypeName = GraphQLSchemaDefinition.getArgumentTypeName(childNode.getAttribute("type"),
                            fieldDef.isList);
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
            System.out.println("YAYYY!! " + parent);
            fieldDef = new FieldDefinition(parent, delegator, dispatcher, fieldNode);
            fieldDefMap.put(fieldDef.name, fieldDef);
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
//		if (entity == null) {
//			throw new IllegalArgumentException("Entity Definition " + entityName + " not found");
//		}

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
        return javaTypeGraphQLMap.get(getShortJavaType(javaType));
    }

    public static String getGraphQLTypeNameBySQLType(String sqlType) {
        if (sqlType == null) return null;
        return fieldTypeGraphQLMap.get(sqlType);
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
