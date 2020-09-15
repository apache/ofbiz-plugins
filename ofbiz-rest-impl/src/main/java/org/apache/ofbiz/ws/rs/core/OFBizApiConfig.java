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
package org.apache.ofbiz.ws.rs.core;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.component.ComponentException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.ws.rs.model.ModelApi;
import org.apache.ofbiz.ws.rs.model.ModelApiReader;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class OFBizApiConfig extends ResourceConfig {
    private static final String MODULE = OFBizApiConfig.class.getName();
    private static final Map<String, ModelApi> MICRO_APIS = new HashMap<>();
    public OFBizApiConfig() {
        packages("org.apache.ofbiz.ws.rs.resources");
        packages("org.apache.ofbiz.ws.rs.security.auth");
        packages("org.apache.ofbiz.ws.rs.spi.impl");
        //packages("io.swagger.v3.jaxrs2.integration.resources"); //commenting it out to generate customized OpenApi Spec
        register(JacksonFeature.class);
        register(MultiPartFeature.class);
        if (Debug.verboseOn()) {
            register(new LoggingFeature(Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME), Level.INFO,
                    LoggingFeature.Verbosity.PAYLOAD_ANY, 10000));
        }
        registerDSLResources();
    }

    public static Map<String, ModelApi> getModelApis() {
        return MICRO_APIS;
    }

    private void registerDSLResources() {
        loadApiDefinitions();
    }

    private void loadApiDefinitions() {
        Collection<ComponentConfig> components = ComponentConfig.getAllComponents();
        components.forEach(component -> {
            String cName = component.getComponentName();
            try {
                String apiSchema = ComponentConfig.getRootLocation(cName) + "/api/" + cName + ".rest.xml";
                File apiSchemaF = new File(apiSchema);
                if (apiSchemaF.exists()) {
                    Debug.logInfo("Processing REST API " + cName + ".rest.xml" + " from component " + cName, MODULE);
                    ModelApi api = ModelApiReader.getModelApi(apiSchemaF);
                    MICRO_APIS.put(cName, api);
                }
            } catch (ComponentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }
}
