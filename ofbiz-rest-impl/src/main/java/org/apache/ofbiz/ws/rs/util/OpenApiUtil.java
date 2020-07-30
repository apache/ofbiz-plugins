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
package org.apache.ofbiz.ws.rs.util;

import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;

public final class OpenApiUtil {

    private OpenApiUtil() {

    }

    private static final Map<String, String> CLASS_ALIAS = new HashMap<>();
    private static final Map<String, Class<?>> JAVA_OPEN_API_MAP = new HashMap<>();

    static {
        CLASS_ALIAS.put("String", "String");
        CLASS_ALIAS.put("java.lang.String", "String");
        CLASS_ALIAS.put("CharSequence", "String");
        CLASS_ALIAS.put("java.lang.CharSequence", "String");
        CLASS_ALIAS.put("Date", "String");
        CLASS_ALIAS.put("java.sql.Date", "String");
        CLASS_ALIAS.put("Time", "String");
        CLASS_ALIAS.put("java.sql.Time", "String");
        CLASS_ALIAS.put("Timestamp", "Timestamp");
        CLASS_ALIAS.put("java.sql.Timestamp", "Timestamp");
        CLASS_ALIAS.put("Integer", "Int");
        CLASS_ALIAS.put("java.lang.Integer", "Int");
        CLASS_ALIAS.put("Long", "Long");
        CLASS_ALIAS.put("java.lang.Long", "Long");
        CLASS_ALIAS.put("BigInteger", "BigInteger");
        CLASS_ALIAS.put("java.math.BigInteger", "BigInteger");
        CLASS_ALIAS.put("Float", "Float");
        CLASS_ALIAS.put("java.lang.Float", "Float");
        CLASS_ALIAS.put("Double", "Float");
        CLASS_ALIAS.put("java.lang.Double", "Float");
        CLASS_ALIAS.put("BigDecimal", "BigDecimal");
        CLASS_ALIAS.put("java.math.BigDecimal", "BigDecimal");
        CLASS_ALIAS.put("Boolean", "Boolean");
        CLASS_ALIAS.put("java.lang.Boolean", "Boolean");

        CLASS_ALIAS.put("org.apache.ofbiz.entity.GenericValue", "GenericValue");
        CLASS_ALIAS.put("GenericValue", "GenericValue");
        CLASS_ALIAS.put("GenericPK", "GenericPK");
        CLASS_ALIAS.put("org.apache.ofbiz.entity.GenericPK", "GenericPK");
        CLASS_ALIAS.put("org.apache.ofbiz.entity.GenericEntity", "GenericEntity");
        CLASS_ALIAS.put("GenericEntity", "GenericEntity");

        CLASS_ALIAS.put("java.util.List", "List");
        CLASS_ALIAS.put("List", "List");
        CLASS_ALIAS.put("java.util.Set", "Set");
        CLASS_ALIAS.put("Set", "Set");
        CLASS_ALIAS.put("java.util.Map", "Map");
        CLASS_ALIAS.put("Map", "Map");
        CLASS_ALIAS.put("java.util.HashMap", "HashMap");
        CLASS_ALIAS.put("HashMap", "HashMap");

        JAVA_OPEN_API_MAP.put("String", StringSchema.class);
        JAVA_OPEN_API_MAP.put("Integer", IntegerSchema.class);
        JAVA_OPEN_API_MAP.put("Long", IntegerSchema.class);
        JAVA_OPEN_API_MAP.put("Map", MapSchema.class);
        JAVA_OPEN_API_MAP.put("GenericEntity", MapSchema.class);
        JAVA_OPEN_API_MAP.put("GenericPK", MapSchema.class);
        JAVA_OPEN_API_MAP.put("GenericValue", MapSchema.class);
        JAVA_OPEN_API_MAP.put("HashMap", MapSchema.class);
        JAVA_OPEN_API_MAP.put("List", ArraySchema.class);
        JAVA_OPEN_API_MAP.put("Float", NumberSchema.class);
        JAVA_OPEN_API_MAP.put("Double", NumberSchema.class);
        JAVA_OPEN_API_MAP.put("BigDecimal", NumberSchema.class);
        JAVA_OPEN_API_MAP.put("Timestamp", DateSchema.class);

    }


    public static Class<?> getOpenApiSchema(String type) {
        return JAVA_OPEN_API_MAP.get(CLASS_ALIAS.get(type));
    }
}
