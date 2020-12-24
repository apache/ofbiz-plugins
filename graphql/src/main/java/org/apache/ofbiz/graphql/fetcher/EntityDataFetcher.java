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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityFindOptions;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.graphql.fetcher.utils.DataFetcherUtils;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.FieldDefinition;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaUtil;
import org.w3c.dom.Element;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings({ "unchecked" })
public final class EntityDataFetcher extends BaseEntityDataFetcher {

    public EntityDataFetcher() {
        super(null, null, null);
    }

    public EntityDataFetcher(Delegator delegator, Element node, FieldDefinition fieldDef) {
        super(delegator, node, fieldDef);
    }

    EntityDataFetcher(Delegator delegator, FieldDefinition fieldDef, String entityName, Map<String, String> relKeyMap) {
        this(delegator, fieldDef, entityName, null, relKeyMap);
    }

    EntityDataFetcher(Delegator delegator, FieldDefinition fieldDef, String entityName, String interfaceEntityName,
            Map<String, String> relKeyMap) {
        super(delegator, fieldDef, entityName, interfaceEntityName, relKeyMap);
    }

    Object fetch(DataFetchingEnvironment environment) {
        Map<String, Object> inputFieldsMap = new HashMap<>();
        Map<String, Object> operationMap = new HashMap<>();
        Map<String, Object> resultMap = new HashMap<>();
        GraphQLSchemaUtil.transformArguments(environment.getArguments(), inputFieldsMap, operationMap);
        if (getOperation().equals("one")) {
            try {
                GenericValue entity = null;
                EntityQuery entityQuery = EntityQuery.use(getDelegator()).from(getEntityName()).where(inputFieldsMap);
                for (Map.Entry<String, String> entry : getRelKeyMap().entrySet()) {
                    entityQuery.where(EntityCondition.makeCondition(entry.getValue(), EntityOperator.EQUALS, (
                            (Map<?, ?>) environment.getSource()).get(entry.getKey())));
                }
                entity = entityQuery.queryOne();
                if (UtilValidate.isEmpty(entity)) {
                    return null;
                }
                if (getInterfaceEntityName() == null || getInterfaceEntityName().isEmpty()
                        || getEntityName().equals(getInterfaceEntityName())) {
                    return entity;
                } else {
                    GenericValue interfaceEntity = null;
                    entityQuery = EntityQuery.use(getDelegator()).from(getInterfaceEntityName())
                            .where(EntityCondition.makeCondition(entity.getPrimaryKey().getAllFields()));
                    interfaceEntity = entityQuery.queryOne();
                    Map<String, Object> jointOneMap = new HashMap<>();
                    if (interfaceEntity != null) {
                        jointOneMap.putAll(interfaceEntity);
                    }
                    jointOneMap.putAll(entity);
                    return jointOneMap;
                }

            } catch (GenericEntityException e) {
                e.printStackTrace();
                return null;
            }
        } else if (getOperation().equals("list")) {
            EntityFindOptions options = null;
            List<GenericValue> result = null;
            Map<String, Object> edgesData;
            List<EntityCondition> entityConditions = new ArrayList<EntityCondition>();
            if (inputFieldsMap.size() != 0) {
                entityConditions.add(EntityCondition.makeCondition(inputFieldsMap));
            } else {
                DataFetcherUtils.addEntityConditions(entityConditions, operationMap,
                        GraphQLSchemaUtil.getEntityDefinition(getEntityName(), getDelegator()));
            }
            for (Map.Entry<String, String> entry : getRelKeyMap().entrySet()) {
                entityConditions.add(EntityCondition.makeCondition(entry.getValue(), EntityOperator.EQUALS, (
                        (Map<?, ?>) environment.getSource()).get(entry.getKey())));
            }
            List<Map<String, Object>> edgesDataList = null;
            if (GraphQLSchemaUtil.requirePagination(environment)) {
                Map<String, Object> arguments = environment.getArguments();
                Map<String, Object> paginationMap = (Map<String, Object>) arguments.get("pagination");
                options = new EntityFindOptions();
                int pageIndex = (int) paginationMap.get("pageIndex");
                int pageSize = (int) paginationMap.get("pageSize");
                int pageRangeLow = pageIndex * pageSize + 1;
                int pageRangeHigh = (pageIndex * pageSize) + pageSize;
                int first = (int) paginationMap.get("first");
                String after = (String) paginationMap.get("after");
                boolean hasPreviousPage = pageIndex > 0;
                String orderBy = (String) paginationMap.get("orderByField");
                options.setLimit(pageSize);
                options.setMaxRows(pageSize);
                options.setOffset(pageIndex);
                Map<String, Object> pageInfo = new HashMap<String, Object>();
                pageInfo.put("pageIndex", pageIndex);
                pageInfo.put("pageSize", pageSize);
                pageInfo.put("pageRangeLow", pageRangeLow);
                pageInfo.put("pageRangeHigh", pageRangeHigh);
                pageInfo.put("hasPreviousPage", hasPreviousPage);
                int count = 0;
                try {
                    count = (int) getDelegator().findCountByCondition(getEntityName(),
                            EntityCondition.makeCondition(entityConditions), null, options);
                    result = getDelegator().findList(getEntityName(), EntityCondition.makeCondition(entityConditions), null,
                            UtilValidate.isNotEmpty(orderBy) ? Arrays.asList(orderBy.split(",")) : null, options,
                            false);
                } catch (GenericEntityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                int pageMaxIndex = new BigDecimal(count - 1).divide(new BigDecimal(pageSize), 0, BigDecimal.ROUND_DOWN)
                        .intValue();
                pageInfo.put("pageMaxIndex", pageMaxIndex);
                if (pageRangeHigh > count) {
                    pageRangeHigh = count;
                }
                boolean hasNextPage = pageMaxIndex > pageIndex;
                pageInfo.put("hasNextPage", hasNextPage);
                pageInfo.put("totalCount", count);
                edgesDataList = new ArrayList<Map<String, Object>>(result.size());
                if (UtilValidate.isNotEmpty(result)) {
                    String cursor = null;
                    if (getInterfaceEntityName() == null || getInterfaceEntityName().isEmpty()
                            || getEntityName().equals(getInterfaceEntityName())) {
                        pageInfo.put("startCursor", GraphQLSchemaUtil.encodeRelayCursor(result.get(0), getPkFieldNames())); // TODO
                        pageInfo.put("endCursor",
                                GraphQLSchemaUtil.encodeRelayCursor(result.get(result.size() - 1), getPkFieldNames())); // TODO
                        for (GenericValue gv : result) {
                            edgesData = new HashMap<>(2);
                            cursor = GraphQLSchemaUtil.encodeRelayCursor(gv, getPkFieldNames());
                            edgesData.put("cursor", cursor); // TODO
                            edgesData.put("node", gv);
                            edgesDataList.add(edgesData);
                        }
                    }
                    resultMap.put("pageInfo", pageInfo);
                }

            } else {
                try {
                    result = getDelegator().findList(getEntityName(), EntityCondition.makeCondition(entityConditions), null, null,
                            options, false);
                } catch (GenericEntityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                edgesDataList = new ArrayList<Map<String, Object>>(result != null ? result.size() : 0);
                if (getInterfaceEntityName() == null || getInterfaceEntityName().isEmpty()
                        || getEntityName().equals(getInterfaceEntityName())) {
                    for (GenericValue gv : result) {
                        edgesData = new HashMap<>(2);
                        edgesData.put("cursor", "2"); // TODO
                        edgesData.put("node", gv);
                        edgesDataList.add(edgesData);
                    }
                }
            }
            resultMap.put("edges", edgesDataList);
            return resultMap;
        }
        return null;
    }

}
