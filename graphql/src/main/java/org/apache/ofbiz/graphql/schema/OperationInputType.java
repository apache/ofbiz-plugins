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
package org.apache.ofbiz.graphql.schema;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"unused"})
class OperationInputType extends LinkedHashMap<String, String> {

    private static final long serialVersionUID = 1L;
    private String value;
    private String op;
    private String not;
    private String ic;

    OperationInputType(String value, String op, String not, String ic) {
        this.value = value;
        this.op = op;
        this.not = not;
        this.ic = ic;
        if (value != null) {
            this.put("value", value);
        }
        if (op != null) {
            this.put("op", op);
        }
        if (not != null) {
            this.put("not", not);
        }
        if (ic != null) {
            this.put("ic", ic);
        }
    }

    OperationInputType(Map<String, String> map) {
        this(map.get("value"), map.get("op"), map.get("not"), map.get("ic"));
    }
}
