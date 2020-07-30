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
package org.apache.ofbiz.ws.rs.resources;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.ws.rs.openapi.OFBizOpenApiReader;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.GenericOpenApiContextBuilder;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Path("/openapi.{type:json|yaml}")
public final class OpenApiResource {
    @Context
    private ServletConfig config;

    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest request;

    @Context
    private Application app;

    @GET
    @Produces({MediaType.APPLICATION_JSON, "application/yaml"})
    @Operation(hidden = true)
    public Response getOpenApi(@Context HttpHeaders headers, @Context UriInfo uriInfo, @PathParam("type") String type)
            throws Exception {
        boolean pretty = false;
        OpenAPI openApi = new OpenAPI();
        openApi.addServersItem(buildOpenApiServer());

        SecurityScheme securitySchemeApiKey = new SecurityScheme();
        securitySchemeApiKey.setName("api-key");
        securitySchemeApiKey.setType(SecurityScheme.Type.APIKEY);
        securitySchemeApiKey.setIn(SecurityScheme.In.HEADER);
        openApi.schemaRequirement(securitySchemeApiKey.getName(), securitySchemeApiKey);
        SwaggerConfiguration config = new SwaggerConfiguration().openAPI(openApi.info(buildOpenApiInfo()))
                .readerClass(OFBizOpenApiReader.class.getName());


        OpenApiContext ctx = new GenericOpenApiContextBuilder<>().openApiConfiguration(config).buildContext(true);

        openApi = ctx.read();

        if (UtilValidate.isNotEmpty(type) && type.trim().equalsIgnoreCase("yaml")) {
            return Response.status(Response.Status.OK)
                    .entity(pretty ? Yaml.pretty(openApi) : Yaml.mapper().writeValueAsString(openApi))
                    .type("application/yaml").build();
        } else {
            return Response.status(Response.Status.OK)
                    .entity(pretty ? Json.pretty(openApi) : Json.mapper().writeValueAsString(openApi))
                    .type(MediaType.APPLICATION_JSON_TYPE).build();
        }
    }


    private Info buildOpenApiInfo() {
        Info info = new Info().version("1.0.0").title("OFBiz REST Store")
                .description("Open API specification for OFBiz RESTful APIs.").contact(buildOpenApiContact())
                .termsOfService("https://ofbiz.apache.org/")
                .license(new License()
                        .name("Apache 2.0")
                        .url("http://www.apache.org/licenses/LICENSE-2.0.html"));

        return info;
    }

    private Contact buildOpenApiContact() {
        Contact contact = new Contact().name("OFBiz API Team").email("ofbiz@apache.org")
                .url("https://ofbiz.apache.org/");
        return contact;
    }

    private Server buildOpenApiServer() {
        System.out.println(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath());
        Server serverItem =
                new Server().url(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath())
                        .description("Server Hosting the REST API");
        return serverItem;
    }
}
