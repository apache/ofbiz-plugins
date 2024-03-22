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

@SuppressWarnings("unused")
public class PaginationInputType {
    private Integer pageIndex;
    private Integer pageSize;
    private Boolean pageNoLimit;
    private String orderByField;
    private Integer first;
    private String after;
    private Integer last;
    private String before;
    private String type; // 'offset' or 'cursor-after' or 'cursor-before'



    /**
     * @return the pageIndex
     */
    public Integer getPageIndex() {
        return pageIndex;
    }

    /**
     * @param pageIndex the pageIndex to set
     */
    public void setPageIndex(Integer pageIndex) {
        this.pageIndex = pageIndex;
    }

    /**
     * @return the pageSize
     */
    public Integer getPageSize() {
        return pageSize;
    }

    /**
     * @param pageSize the pageSize to set
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * @return the pageNoLimit
     */
    public Boolean getPageNoLimit() {
        return pageNoLimit;
    }

    /**
     * @param pageNoLimit the pageNoLimit to set
     */
    public void setPageNoLimit(Boolean pageNoLimit) {
        this.pageNoLimit = pageNoLimit;
    }

    /**
     * @return the orderByField
     */
    public String getOrderByField() {
        return orderByField;
    }

    /**
     * @param orderByField the orderByField to set
     */
    public void setOrderByField(String orderByField) {
        this.orderByField = orderByField;
    }

    /**
     * @return the first
     */
    public Integer getFirst() {
        return first;
    }

    /**
     * @param first the first to set
     */
    public void setFirst(Integer first) {
        this.first = first;
    }

    /**
     * @return the after
     */
    public String getAfter() {
        return after;
    }

    /**
     * @param after the after to set
     */
    public void setAfter(String after) {
        this.after = after;
    }

    /**
     * @return the last
     */
    public Integer getLast() {
        return last;
    }

    /**
     * @param last the last to set
     */
    public void setLast(Integer last) {
        this.last = last;
    }

    /**
     * @return the before
     */
    public String getBefore() {
        return before;
    }

    /**
     * @param before the before to set
     */
    public void setBefore(String before) {
        this.before = before;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

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

    public PaginationInputType(Map<String, Object> map) {
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
