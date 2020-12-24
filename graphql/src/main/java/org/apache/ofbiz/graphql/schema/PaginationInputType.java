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

import java.util.Map;

public class PaginationInputType {
    public Integer pageIndex;
    public Integer pageSize;
    public Boolean pageNoLimit;
    public String orderByField;
    public Integer first;
    public String after;
    public Integer last;
    public String before;
    public String type; // 'offset' or 'cursor-after' or 'cursor-before'

    PaginationInputType(int pageIndex, int pageSize, boolean pageNoLimit, String orderByField) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.pageNoLimit = pageNoLimit;
        this.orderByField = orderByField;
    }

    PaginationInputType(int first, String after, int last, String before) {
        this.first = first;
        this.after = after;
        this.last = last;
        this.before = before;
    }

    public PaginationInputType(Map map) {
        this.pageIndex = (int) map.get("pageIndex");
        this.pageSize = (int) map.get("pageSize");
        this.pageNoLimit = (Boolean) map.get("pageNoLimit");
        this.orderByField = (String) map.get("orderByField");
        this.first = (int) map.get("first");
        this.after = (String) map.get("after");
        this.last = (int) map.get("last");
        this.before = (String) map.get("before");
        this.type = (String) map.get("type") != null ? (String) map.get("type") : "offset";
    }

}
