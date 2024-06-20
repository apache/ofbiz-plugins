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
package org.apache.ofbiz.graphql.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.graphql.schema.GraphQLSchemaUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class GraphQLServices {

    /**
     * @param dctx
     * @param context
     * @return
     */
    public Map<String, Object> buildPageInfo(DispatchContext dctx, Map<String, Object> context) {
        String type = (String) context.get("type");
        int totalCount = (int) context.get("totalCount");
        int pageSize = (int) context.get("pageSize");
        int pageIndex = (int) context.get("pageIndex");
        String startCursor = (String) context.get("startCursor");
        String endCursor = (String) context.get("endCursor");
        Map<String, Object> pageInfo = new HashMap<String, Object>();
        int pageMaxIndex = new BigDecimal(totalCount - 1).divide(new BigDecimal(pageSize), 0, BigDecimal.ROUND_DOWN)
                .intValue();
        int pageRangeLow = pageIndex * pageSize + 1;
        int pageRangeHigh = (pageIndex * pageSize) + pageSize;
        if (pageRangeHigh > totalCount) {
            pageRangeHigh = totalCount;
        }
        boolean hasPreviousPage = pageIndex > 0;
        boolean hasNextPage = pageMaxIndex > pageIndex;
        switch (type) {
        case "offset":
            pageInfo.put("pageIndex", pageIndex);
            pageInfo.put("pageSize", pageSize);
            pageInfo.put("pageRangeLow", pageRangeLow);
            pageInfo.put("pageRangeHigh", pageRangeHigh);
            pageInfo.put("hasPreviousPage", hasPreviousPage);
            pageInfo.put("pageMaxIndex", pageMaxIndex);
            pageInfo.put("hasNextPage", hasNextPage);
            pageInfo.put("totalCount", totalCount);
            break;
        case "cursor-after":
        case "cursor-before":
            pageInfo.put("hasNextPage", hasNextPage);
            pageInfo.put("hasPreviousPage", hasNextPage);
            pageInfo.put("endCursor", endCursor);
            pageInfo.put("startCursor", startCursor);
            break;
        }
        Map<String, Object> sucess = ServiceUtil.returnSuccess();
        sucess.put("pageInfo", pageInfo);
        return sucess;
    }

    /**
     *
     * @param dctx
     * @param context
     * @return
     */
    public Map<String, Object> buildConnection(DispatchContext dctx, Map<String, Object> context) {

        List<GenericValue> el = (List<GenericValue>) context.get("el");
        int totalCount = (int) context.get("totalCount");
        int pageSize = (int) context.get("pageSize");
        int pageIndex = (int) context.get("pageIndex");
        Map<String, Object> pageInfo = new HashMap<String, Object>();
        int pageMaxIndex = new BigDecimal(totalCount - 1).divide(new BigDecimal(pageSize), 0, BigDecimal.ROUND_DOWN)
                .intValue();
        int pageRangeLow = pageIndex * pageSize + 1;
        int pageRangeHigh = (pageIndex * pageSize) + pageSize;
        if (pageRangeHigh > totalCount) {
            pageRangeHigh = totalCount;
        }
        boolean hasPreviousPage = pageIndex > 0;
        boolean hasNextPage = pageMaxIndex > pageIndex;
        Map<String, Object> edgesData;
        Map<String, Object> node;
        String id;

        List<Map<String, Object>> edgesDataList = new ArrayList<Map<String, Object>>(el.size());
        List<String> pkFieldNames = null;
        if (UtilValidate.isNotEmpty(el)) {
            Map<String, Object> primaryKeys = el.get(0).getPrimaryKey().getAllFields();
            pkFieldNames = new ArrayList<String>(primaryKeys.keySet());
            pageInfo.put("startCursor", GraphQLSchemaUtil.encodeRelayCursor(el.get(0), pkFieldNames)); // TODO
            pageInfo.put("endCursor", GraphQLSchemaUtil.encodeRelayCursor(el.get(el.size() - 1), pkFieldNames)); // TODO
        }

        for (int index = 0; index < el.size(); index++) {
            GenericValue gv = el.get(index);
            edgesData = new HashMap<>(2);
            node = new HashMap<>();
            Map<String, Object> primaryKeys = gv.getPrimaryKey().getAllFields();
            if (primaryKeys.size() > 0 && !primaryKeys.values().contains(null)) {
                id = GraphQLSchemaUtil.encodeRelayId(gv, new ArrayList<String>(primaryKeys.keySet()));
            } else {
                id = "" + index;
            }
            node.put("id", id);
            node.putAll(gv);
            edgesData.put("cursor", id); // TODO
            edgesData.put("node", node);
            edgesDataList.add(edgesData);
        }

        pageInfo.put("pageIndex", pageIndex);
        pageInfo.put("pageSize", pageSize);
        pageInfo.put("pageRangeLow", pageRangeLow);
        pageInfo.put("pageRangeHigh", pageRangeHigh);
        pageInfo.put("hasPreviousPage", hasPreviousPage);
        pageInfo.put("pageMaxIndex", pageMaxIndex);
        pageInfo.put("hasNextPage", hasNextPage);
        pageInfo.put("totalCount", totalCount);
        Map<String, Object> sucess = ServiceUtil.returnSuccess();
        sucess.put("pageInfo", pageInfo);
        sucess.put("edges", edgesDataList);
        return sucess;
    }

    private static Object buildFieldRecursive(Object obj) {
        Map result = new HashMap();
        if (obj instanceof List) {
            List edges = new ArrayList<>();
            for (Object item : edges) {
                Map edge = new HashMap();
                Map node = (Map) buildFieldRecursive(item);
                edge.put("node", node);
                edges.add(edge);
            }
            result.put("edges", edges);
            return result;
        } else if (obj instanceof Map) {
            Map map = (Map) obj;
            map.forEach((k, v) -> {
                result.put((String) k, buildFieldRecursive(v));
            });
            return result;
        } else {
            return obj;
        }
    }

    /**
     *
     * @param dctx
     * @param context
     * @return
     */
    public Map<String, Object> buildConnectionByList(DispatchContext dctx, Map<String, Object> context) {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> edgesData;
        Map<String, Object> node;
        List<String> pkFieldNames = (List<String>) context.get("pkFieldNames");
        List<GenericValue> el = (List<GenericValue>) context.get("el");
        List<Map<String, Object>> edgesDataList = new ArrayList<Map<String, Object>>(el.size());

        for (GenericValue gv : el) {
            String id;
            edgesData = new HashMap<>();
            node = new HashMap<>();
            if (pkFieldNames.size() > 0 && !pkFieldNames.contains(null)) {
                id = GraphQLSchemaUtil.encodeRelayId(gv, pkFieldNames);
                node.put("id", id);
            }
            Map map = (Map) buildFieldRecursive(gv);
            node.putAll(map);
            edgesData.put("node", node);
            edgesDataList.add(edgesData);
        }

        Map<String, Object> sucess = ServiceUtil.returnSuccess();
        sucess.put("edges", edgesDataList);

        Map<String, Object> buildPageInfoCtx = new HashMap<>();
        try {
            buildPageInfoCtx = dctx.makeValidContext("createCommunicationEvent", ModelService.IN_PARAM, context);
            Map<String, Object> buildPageInfoResult = dispatcher.runSync("buildPageInfo", buildPageInfoCtx);
            if (ServiceUtil.isSuccess(buildPageInfoResult)) {
                Map<String, Object> pageInfo = (Map<String, Object>) buildPageInfoResult.get("pageInfo");
                sucess.put("pageInfo", pageInfo);
            }
        } catch (GenericServiceException e) {
            e.printStackTrace();
        }

        return sucess;

    }

}
