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
package org.apache.ofbiz.graphql.fetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.FieldDefinition;
import org.w3c.dom.Element;

class BaseEntityDataFetcher extends BaseDataFetcher {
    private String entityName;
    private String interfaceEntityName;
    private String operation;
    private String requireAuthentication;
    private String interfaceEntityPkField;
    private List<String> pkFieldNames = new ArrayList<>(1);
    private String fieldRawType;
    private Map<String, String> relKeyMap = new HashMap<>();
    private List<String> localizeFields = new ArrayList<>();
    private boolean useCache = false;

    /**
     * @return the entityName
     */
    protected String getEntityName() {
        return entityName;
    }

    /**
     * @return the interfaceEntityName
     */
    protected String getInterfaceEntityName() {
        return interfaceEntityName;
    }

    /**
     * @return the operation
     */
    protected String getOperation() {
        return operation;
    }

    /**
     * @return the requireAuthentication
     */
    protected String getRequireAuthentication() {
        return requireAuthentication;
    }

    /**
     * @return the interfaceEntityPkField
     */
    protected String getInterfaceEntityPkField() {
        return interfaceEntityPkField;
    }

    /**
     * @return the pkFieldNames
     */
    protected List<String> getPkFieldNames() {
        return pkFieldNames;
    }

    /**
     * @return the fieldRawType
     */
    protected String getFieldRawType() {
        return fieldRawType;
    }

    /**
     * @return the relKeyMap
     */
    protected Map<String, String> getRelKeyMap() {
        return relKeyMap;
    }

    /**
     * @return the localizeFields
     */
    protected List<String> getLocalizeFields() {
        return localizeFields;
    }

    /**
     * @return the useCache
     */
    protected boolean isUseCache() {
        return useCache;
    }

    BaseEntityDataFetcher(Delegator delegator, Element element, FieldDefinition fieldDef) {
        super(fieldDef, delegator);
        String entityName = element.getAttribute("entity-name");
        ModelEntity entity = null;
        try {
            entity = delegator.getModelReader().getModelEntity(entityName);
        } catch (GenericEntityException e) {
            throw new IllegalArgumentException("Entity [" + entityName + "] does not exist.");
        }

        if (element.getAttribute("cache") != null) {
            useCache = "true".equals(element.getAttribute("cache")) && !entity.getNeverCache();
        } else {
            useCache = !entity.getNeverCache();
        }

        Map<String, String> keyMap = new HashMap<>();
        List<? extends Element> keyMapElements = UtilXml.childElementList(element, "key-map");
        for (Element keyMapElement : keyMapElements) {
            String fieldName = keyMapElement.getAttribute("field-name");
            String relFn = keyMapElement.getAttribute("related");
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
                        "The key-map.@related of Entity " + entityName + " should be specified");
            }

            keyMap.put(fieldName, relFn);
        }

        List<? extends Element> localizeFieldElements = UtilXml.childElementList(element, "localize-field");

        for (Element keyMapElement : localizeFieldElements) {
            if (!localizeFields.contains(keyMapElement.getAttribute("name"))) {
                localizeFields.add(keyMapElement.getAttribute("name"));
            }
        }
        initializeFields(entityName, element.getAttribute("interface-entity-name"), keyMap);
    }

    BaseEntityDataFetcher(Delegator delegator, FieldDefinition fieldDef, String entityName,
            Map<String, String> relKeyMap) {
        this(delegator, fieldDef, entityName, null, relKeyMap);
    }

    BaseEntityDataFetcher(Delegator delegator, FieldDefinition fieldDef, String entityName, String interfaceEntityName,
            Map<String, String> relKeyMap) {
        super(fieldDef, delegator);
        ModelEntity entity = null;
        try {
            entity = delegator.getModelReader().getModelEntity(entityName);
        } catch (GenericEntityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        useCache = !entity.getNeverCache();
        initializeFields(entityName, interfaceEntityName, relKeyMap);
    }

    private void initializeFields(String entityName, String interfaceEntityName, Map<String, String> relKeyMap) {
        this.requireAuthentication = getFieldDef().getRequireAuthentication() != null
                ? getFieldDef().getRequireAuthentication()
                : "true";
        this.entityName = entityName;
        this.interfaceEntityName = interfaceEntityName;
        this.fieldRawType = getFieldDef().getType();
        this.relKeyMap.putAll(relKeyMap);
        if ("true".equals(getFieldDef().getIsList())) {
            this.operation = "list";
        } else {
            this.operation = "one";
        }
        if (UtilValidate.isNotEmpty(interfaceEntityName)) {
            ModelEntity entity = null;
            try {
                entity = getDelegator().getModelReader().getModelEntity(entityName);
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
            if (entity == null) {
                throw new IllegalArgumentException("Interface entity " + interfaceEntityName + " not found");
            }
            if (entity.getPkFieldNames().size() != 1) {
                throw new IllegalArgumentException(
                        "Entity " + interfaceEntityName + " for interface should have one primary key");
            }
            interfaceEntityPkField = entity.getFirstPkFieldName();
        }

        ModelEntity entity = null;
        try {
            entity = getDelegator().getModelReader().getModelEntity(entityName);
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        if (entity == null) {
            throw new IllegalArgumentException("Entity " + entityName + " not found");
        }
        pkFieldNames.addAll(entity.getPkFieldNames());
    }
}
