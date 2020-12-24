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
 * under the License.aaaab	
 *******************************************************************************/
package org.apache.ofbiz.graphql.schema;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("serial")
class OperationInputType extends LinkedHashMap<String, String> {
    public String value;
    public String op;
    public String not;
    public String ic;

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

    public OperationInputType(Map map) {
        this((String) map.get("value"), (String) map.get("op"), (String) map.get("not"), (String) map.get("ic"));
    }
}
