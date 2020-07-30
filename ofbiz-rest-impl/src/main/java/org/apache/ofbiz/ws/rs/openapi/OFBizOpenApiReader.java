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

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.webapp.WebAppUtil;
import org.apache.ofbiz.ws.rs.listener.ApiContextListener;
import org.apache.ofbiz.ws.rs.util.OpenApiUtil;

import io.swagger.v3.oas.integration.ContextUtils;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.integration.api.OpenApiReader;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

public final class OFBizOpenApiReader implements OpenApiReader {

    private OpenAPIConfiguration openApiConfiguration;
    private Components components;
    private Paths paths;
    private Set<Tag> openApiTags;
    @SuppressWarnings("rawtypes")
    private Map<String, Schema> schemas;
    private OpenAPI openApi;

    public OFBizOpenApiReader() {
        paths = new Paths();
        openApiTags = new LinkedHashSet<>();
        schemas = new HashMap<>();
    }

    @Override
    public void setConfiguration(OpenAPIConfiguration openApiConfiguration) {
        if (openApiConfiguration != null) {
            this.openApiConfiguration = ContextUtils.deepCopy(openApiConfiguration);
            if (openApiConfiguration.getOpenAPI() != null) {
                this.openApi = this.openApiConfiguration.getOpenAPI();
                if (this.openApi.getComponents() != null) {
                    this.components = this.openApi.getComponents();
                } else {
                    components = new Components();
                }
                components.schemas(schemas);
            } else {
                this.openApi = new OpenAPI();
            }
        }
    }

    @Override
    public OpenAPI read(Set<Class<?>> classes, Map<String, Object> resources) {
        openApi = openApiConfiguration.getOpenAPI();
        Tag serviceResourceTag = new Tag().name("Exported Services")
                .description("OFBiz services that are exposed via REST interface with export attribute set to true");
        openApiTags.add(serviceResourceTag);
        openApi.setTags(new ArrayList<Tag>(openApiTags));

        ServletContext servletContext = ApiContextListener.getApplicationCntx();
        LocalDispatcher dispatcher = WebAppUtil.getDispatcher(servletContext);
        DispatchContext context = dispatcher.getDispatchContext();
        Set<String> serviceNames = context.getAllServiceNames();

        for (String serviceName : serviceNames) {
            ModelService service = null;
            try {
                service = context.getModelService(serviceName);
            } catch (GenericServiceException e) {
                e.printStackTrace();
            }
            if (service != null && service.export && UtilValidate.isNotEmpty(service.action)) {
                final Operation operation = new Operation().summary(service.description)
                        .description(service.description).addTagsItem("Exported Services").operationId(service.name)
                        .deprecated(false);

                PathItem pathItemObject = new PathItem();

                if (service.action.equalsIgnoreCase(HttpMethod.GET)) {
                    final QueryParameter serviceInParam = (QueryParameter) new QueryParameter().required(true)
                            .description("Service In Parameters in JSON").name("inParams");
                    Schema<?> refSchema = new Schema<>();
                    refSchema.$ref(service.name + "Request");
                    serviceInParam.schema(refSchema);
                    operation.addParametersItem(serviceInParam);

                } else if (service.action.equalsIgnoreCase(HttpMethod.POST)) {
                    RequestBody request = new RequestBody().description("Request Body for service " + service.name)
                            .content(new Content().addMediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON,
                                    new MediaType().schema(new Schema<>().$ref(service.name + "Request"))));
                    operation.setRequestBody(request);
                }

                ApiResponses apiResponsesObject = new ApiResponses();
                ApiResponse successResponse = new ApiResponse().description("Success");
                Content content = new Content();
                MediaType jsonMediaType = new MediaType();
                Schema<?> refSchema = new Schema<>();
                refSchema.$ref(service.name + "Response");
                jsonMediaType.setSchema(refSchema);
                setOutSchemaForService(service);
                setInSchemaForService(service);
                content.addMediaType(javax.ws.rs.core.MediaType.APPLICATION_JSON, jsonMediaType);

                apiResponsesObject.addApiResponse("200", successResponse.content(content));
                setPathItemOperation(pathItemObject, service.action.toUpperCase(), operation);
                operation.setResponses(apiResponsesObject);
                paths.addPathItem("/services/" + service.name, pathItemObject);

            }
        }

        openApi.setPaths(paths);
        openApi.setComponents(components);

        return openApi;
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

    private void setOutSchemaForService(ModelService service) {
        Schema<Object> parentSchema = new Schema<Object>();
        parentSchema.setDescription("Out Schema for service: " + service.name + " response");
        parentSchema.setType("object");
        parentSchema.addProperties("statusCode", new IntegerSchema().description("HTTP Status Code"));
        parentSchema.addProperties("statusDescription", new StringSchema().description("HTTP Status Code Description"));
        parentSchema.addProperties("successMessage", new StringSchema().description("Success Message"));
        ObjectSchema dataSchema = new ObjectSchema();
        parentSchema.addProperties("data", dataSchema);
        service.getOutParamNamesMap().forEach((name, type) -> {
            Schema<?> schema = null;
            Class<?> schemaClass = OpenApiUtil.getOpenApiSchema(type);
            if (schemaClass == null) {
                return;
            }
            try {
                schema = (Schema<?>) schemaClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
            }
            if (schema instanceof ArraySchema) {
                ArraySchema arraySchema = (ArraySchema) schema;
                arraySchema.items(new StringSchema());
            }
            dataSchema.addProperties(name, schema.description(name));
        });
        schemas.put(service.name + "Response", parentSchema);
    }

    private void setInSchemaForService(ModelService service) {
        Schema<Object> parentSchema = new Schema<Object>();
        parentSchema.setDescription("In Schema for service: " + service.name + " request");
        parentSchema.setType("object");
        service.getInParamNamesMap().forEach((name, type) -> {
            Schema<?> schema = null;
            Class<?> schemaClass = OpenApiUtil.getOpenApiSchema(type);
            if (schemaClass == null) {
                return;
            }
            try {
                schema = (Schema<?>) schemaClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
            }
            parentSchema.addProperties(name, schema.description(name));
        });
        schemas.put(service.name + "Request", parentSchema);
    }

}
