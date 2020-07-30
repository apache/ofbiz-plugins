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
package org.apache.ofbiz.ws.rs.spi.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import org.apache.ofbiz.ws.rs.ApiServiceRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Provider
public class JsonifiedParamConverterProvider implements ParamConverterProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     *
     */
    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.getName().equals(ApiServiceRequest.class.getName())) {
            return new ParamConverter<T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T fromString(String value) {
                    Map<String, Object> map = null;
                    try {
                        map = getMapper().readValue(value, new TypeReference<Map<String, Object>>() {
                        });
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    return (T) new ApiServiceRequest(map);
                }

                @Override
                public String toString(T map) {
                    return ((ApiServiceRequest) map).getInParams().toString();
                }
            };
        }
        return null;
    }
}
