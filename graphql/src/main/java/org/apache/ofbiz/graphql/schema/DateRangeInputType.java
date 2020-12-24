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
public class DateRangeInputType extends LinkedHashMap<String, String> {
    public String period;
    public String poffset;
    public String from;
    public String thru;

    DateRangeInputType(String period, String poffset, String from, String thru) {
        this.period = period;
        this.poffset = poffset;
        this.from = from;
        this.thru = thru;
        if (this.period != null) {
            this.put("period", this.period);
        }
        if (this.poffset != null) {
            this.put("poffset", this.poffset);
        }
        if (this.from != null) {
            this.put("from", this.from);
        }
        if (this.thru != null) {
            this.put("thru", this.thru);
        }
    }

    public DateRangeInputType(Map map) {
        this((String) map.get("period"), (String) map.get("poffset"), (String) map.get("from"), (String) map.get("thru"));
    }
}
