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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaDefinition.FieldDefinition;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.w3c.dom.Element;

import graphql.schema.DataFetchingEnvironment;
import graphql.servlet.context.DefaultGraphQLServletContext;

@SuppressWarnings({ "unchecked", "rawtypes" })
public final class ServiceDataFetcher extends BaseDataFetcher {

    private String serviceName;
    private String invoke;
    private String defaultEntity;
    private boolean isEntityAutoService;

    public Map<String, String> getRelKeyMap() {
        return relKeyMap;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isEntityAutoService() {
        return isEntityAutoService;
    }

    private Map<String, String> relKeyMap = new HashMap<>();

    public ServiceDataFetcher(Element node, FieldDefinition fieldDef, Delegator delegator, LocalDispatcher dispatcher) {
        super(fieldDef, delegator);
        this.serviceName = node.getAttribute("service");
        List<? extends Element> elements = UtilXml.childElementList(node, "key-map");
        for (Element keyMapNode : elements) {
            relKeyMap.put(keyMapNode.getAttribute("field-name"),
                    keyMapNode.getAttribute("related") != null ? keyMapNode.getAttribute("related")
                            : keyMapNode.getAttribute("field-name"));
        }

        try {
            ModelService service = dispatcher.getDispatchContext().getModelService(serviceName);
            if (service == null) {
                throw new IllegalArgumentException("Service ${serviceName} not found");
            }
            if (service.getEngineName().equalsIgnoreCase("entity-auto")) {
                isEntityAutoService = true;
            }

            defaultEntity = service.getDefaultEntityName();
            invoke = service.getInvoke();

            if (this.isEntityAutoService) {
                if (!fieldDef.isMutation()) {
                    throw new IllegalArgumentException("Query should not use entity auto service ${serviceName}");
                }
            }
        } catch (GenericServiceException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    Object fetch(DataFetchingEnvironment environment) {
        DefaultGraphQLServletContext context = environment.getContext();
        HttpServletRequest request = context.getHttpServletRequest();
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        if (dispatcher == null) {
            dispatcher = (LocalDispatcher) request.getServletContext().getAttribute("dispatcher");
        }

        ModelService service = null;

        try {
            service = dispatcher.getDispatchContext().getModelService(serviceName);
        } catch (GenericServiceException e) {
            e.printStackTrace();
        }
        Map<String, Object> inputFieldsMap = new HashMap<>();
        Map<String, Object> operationMap = new HashMap<>();
        inputFieldsMap.put("userLogin", userLogin);
        if (getFieldDef().isMutation()) {
            GraphQLSchemaUtil.transformArguments(environment.getArguments(), inputFieldsMap, operationMap);
        } else {
            GraphQLSchemaUtil.transformQueryServiceArguments(service, environment.getArguments(), inputFieldsMap);
            Map source = environment.getSource();
            GraphQLSchemaUtil.transformQueryServiceRelArguments(source, relKeyMap, inputFieldsMap);
        }

        Map<String, Object> result = null;

        try {
            if (getFieldDef().isMutation()) {
                result = dispatcher.runSync(serviceName, inputFieldsMap);
                String verb = GraphQLSchemaUtil.getVerbFromName(serviceName, dispatcher);
                if (this.isEntityAutoService || isCRUDService()) {
                    if (UtilValidate.isNotEmpty(verb) && verb.equals("delete")) {
                        result.put("error", false);
                        result.put("message", "Deleted Successfully");
                    } else {
                        GenericValue entity = null;
                        try {
                            entity = EntityQuery.use(getDelegator()).from(defaultEntity).where(result).cache().queryOne();
                            result.put("_graphql_result_", entity);
                        } catch (GenericEntityException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                result = dispatcher.runSync(serviceName, inputFieldsMap);
            }
        } catch (GenericServiceException e) {
            e.printStackTrace();
        }

        return result;
    }

    private boolean isCRUDService() {
        if ((invoke.startsWith("create") || invoke.startsWith("update") || invoke.startsWith("delete"))
                && invoke.endsWith(defaultEntity)) {
            return true;
        }
        return false;
    }
}
