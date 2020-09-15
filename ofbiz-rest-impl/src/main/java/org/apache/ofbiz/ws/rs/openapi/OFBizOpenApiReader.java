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
package org.apache.ofbiz.ws.rs.openapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.webapp.WebAppUtil;
import org.apache.ofbiz.ws.rs.listener.ApiContextListener;
import org.apache.ofbiz.ws.rs.util.OpenApiUtil;

import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.integration.api.OpenApiReader;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;

public final class OFBizOpenApiReader extends Reader implements OpenApiReader {

    private Components components;
    private Paths paths;
    private Set<Tag> openApiTags;
    @SuppressWarnings("rawtypes")
    private Map<String, Schema> schemas;
    private OpenAPI openApi;
    private DispatchContext context;

    public OFBizOpenApiReader() {
        openApiTags = new LinkedHashSet<>();
    }

    @Override
    public void setConfiguration(OpenAPIConfiguration openApiConfiguration) {
        super.setConfiguration(openApiConfiguration);
    }

    @Override
    public OpenAPI read(Set<Class<?>> classes, Map<String, Object> resources) {
        openApi = super.read(classes, resources);
        ServletContext servletContext = ApiContextListener.getApplicationCntx();
        LocalDispatcher dispatcher = WebAppUtil.getDispatcher(servletContext);
        context = dispatcher.getDispatchContext();
        initializeStdOpenApiComponents();
        addPredefinedSchemas();
        addExportableServices();
        addApiResources();
        openApi.setPaths(paths);
        openApi.setComponents(components);
        return openApi;
    }

    // TODO - Add method contents
    private void addApiResources() {
    }

    private void addExportableServices() {
        Set<String> serviceNames = context.getAllServiceNames();
        for (String serviceName : serviceNames) {
            ModelService service = null;
            try {
                service = context.getModelService(serviceName);
            } catch (GenericServiceException e) {
                e.printStackTrace();
            }
            if (service != null && service.isExport() && UtilValidate.isNotEmpty(service.getAction())) {
                SecurityRequirement security = new SecurityRequirement();
                security.addList("jwtToken");
                final Operation operation = new Operation().summary(service.getDescription())
                        .description(service.getDescription()).addTagsItem("Exported Services")
                        .operationId(service.getName()).deprecated(false).addSecurityItem(security);
                PathItem pathItemObject = new PathItem();
                if (service.getAction().equalsIgnoreCase(HttpMethod.GET)) {
                    final QueryParameter serviceInParam = (QueryParameter) new QueryParameter().required(true)
                            .description("Service In Parameters in JSON").name("inParams");
                    Schema<?> refSchema = new Schema<>();
                    refSchema.$ref("#/components/schemas/" + "api.request." + service.getName());
                    serviceInParam.schema(refSchema);
                    operation.addParametersItem(serviceInParam);
                } else if (service.getAction().equalsIgnoreCase(HttpMethod.POST)) {
                    RequestBody request = new RequestBody().description("Request Body for service " + service.getName())
                            .content(new Content().addMediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON,
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + "api.request." + service.getName()))));
                    operation.setRequestBody(request);
                }
                addServiceOutSchema(service);
                addServiceInSchema(service);
                addServiceOperationApiResponses(service, operation);
                setPathItemOperation(pathItemObject, service.getAction().toUpperCase(), operation);
                paths.addPathItem("/services/" + service.getName(), pathItemObject);
            }
        }
    }

    private void initializeStdOpenApiComponents() {
        if (openApi.getTags() != null) {
            openApiTags.addAll(openApi.getTags());
        }
        Tag serviceResourceTag = new Tag().name("Exported Services")
                .description("OFBiz services that are exposed via REST interface with export attribute set to true");
        openApiTags.add(serviceResourceTag);
        openApi.setTags(new ArrayList<Tag>(openApiTags));
        components = openApi.getComponents();
        if (components == null) {
            components = new Components();
        }
        schemas = components.getSchemas();
        if (schemas == null) {
            schemas = new HashMap<>();
            components.schemas(schemas);
        }
        paths = openApi.getPaths();
        if (paths == null) {
            paths = new Paths();
        }
    }

    private void setPathItemOperation(PathItem pathItemObject, String method, Operation operation) {
        switch (method) {
        case HttpMethod.POST:
            pathItemObject.post(operation);
            break;
        case HttpMethod.GET:
            pathItemObject.get(operation);
            break;
        case HttpMethod.DELETE:
            pathItemObject.delete(operation);
            break;
        case HttpMethod.PUT:
            pathItemObject.put(operation);
            break;
        case HttpMethod.PATCH:
            pathItemObject.patch(operation);
            break;
        case HttpMethod.HEAD:
            pathItemObject.head(operation);
            break;
        case HttpMethod.OPTIONS:
            pathItemObject.options(operation);
            break;
        default:
            // Do nothing here
            break;
        }
    }

    private void addServiceOutSchema(ModelService service) {
        schemas.put("api.response." + service.getName() + ".success", OpenApiUtil.getOutSchema(service));
    }

    private void addServiceInSchema(ModelService service) {
        schemas.put("api.request." + service.getName(), OpenApiUtil.getInSchema(service));
    }

    private void addPredefinedSchemas() {
        OpenApiUtil.getStandardApiResponseSchemas().forEach((name, schema) -> {
            schemas.put(name, schema);
        });
    }

    private void addServiceOperationApiResponses(ModelService service, Operation operation) {
        ApiResponses apiResponsesObject = new ApiResponses();
        ApiResponse successResponse = OpenApiUtil.buildSuccessResponse(service);
        apiResponsesObject.addApiResponse(String.valueOf(Response.Status.OK.getStatusCode()), successResponse);
        OpenApiUtil.getStandardApiResponses().forEach((code, response) -> {
            apiResponsesObject.addApiResponse(code, response);
        });
        operation.setResponses(apiResponsesObject);
    }

}
