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

package org.apache.ofbiz.birt.flexible;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.party.party.PartyHelper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.eclipse.birt.report.engine.api.script.IReportContext;

public class BirtMasterReportServices {

    private static final String MODULE = BirtServices.class.getName();
    private static final String RESOURCE = "BirtUiLabels";

    // The following methods are flexible report services as examples for reporting
    public static Map<String, Object> workEffortPerPersonPrepareDate(DispatchContext dctx, Map<String, Object> context) {
        Map<String, String> dataMap = UtilMisc.toMap(
                "lastName", "name",
                "firstName", "name",
                "hours", "floating-point",
                "fromDate", "date-time",
                "thruDate", "date-time");
        LinkedHashMap<String, String> filterMap = new LinkedHashMap<>();
        filterMap.put("firstName", "name");
        filterMap.put("lastName", "name");
        filterMap.put("fromDate", "date-time");
        filterMap.put("thruDate", "date-time");
        Map<String, String> fieldDisplayLabels = UtilMisc.toMap(
                "lastName", "Last name",
                "firstName", "First name",
                "hours", "Hours",
                "fromDate", "From date",
                "thruDate", "Thru date");
        LinkedHashMap<String, String> filterDisplayLabels = new LinkedHashMap<>();
        filterDisplayLabels.put("firstName", "First name");
        filterDisplayLabels.put("lastName", "Last name");
        filterDisplayLabels.put("fromDate", "From date");
        filterDisplayLabels.put("thruDate", "Thru date");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("dataMap", dataMap);
        result.put("filterMap", filterMap);
        result.put("fieldDisplayLabels", fieldDisplayLabels);
        result.put("filterDisplayLabels", filterDisplayLabels);
        return result;
    }

    public static Map<String, Object> workEffortPerPerson(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        IReportContext reportContext = (IReportContext) context.get("reportContext");
        Map<String, Object> parameters = UtilGenerics.cast(reportContext.getParameterValue("parameters"));
        List<GenericValue> listWorkEffortTime = null;

        if (UtilValidate.isEmpty(parameters.get("firstName")) && UtilValidate.isEmpty(parameters.get("lastName"))) {
            return ServiceUtil.returnError("First and last name can not be both empty");
        }
        List<GenericValue> listPersons = null;
        try {
            // TODO: translate labels
            List<EntityExpr> listConditions = new ArrayList<>();
            if (UtilValidate.isNotEmpty(parameters.get("firstName"))) {
                EntityExpr conditionFirstName = EntityCondition.makeCondition("firstName", parameters.get("firstName"));
                listConditions.add(conditionFirstName);
            }
            if (UtilValidate.isNotEmpty(parameters.get("lastName"))) {
                EntityExpr conditionLastName = EntityCondition.makeCondition("lastName", parameters.get("lastName"));
                listConditions.add(conditionLastName);
            }
            EntityCondition ecl = EntityCondition.makeCondition(listConditions, EntityOperator.AND);
            listPersons = EntityQuery.use(delegator).from("Person").where(ecl).select("partyId", "firstName", "lastName").queryList();
            GenericValue person = null;
            if (listPersons.size() > 1) {
                return ServiceUtil.returnError("Your criteria match with several people");
            } else if (listPersons.size() == 1) {
                person = listPersons.get(0);
            } else {
                return ServiceUtil.returnError("Could not find this person");
            }
            String partyId = person.getString("partyId");

            List<EntityExpr> listConditionsWorkEffort = new ArrayList<>();
            Timestamp thruDate = null;
            Timestamp fromDate = null;
            if (UtilValidate.isEmpty(parameters.get("fromDate"))) {
                return ServiceUtil.returnError("The starting date is mandatory");
            } else {
                fromDate = Timestamp.valueOf((String) parameters.get("fromDate"));
                EntityExpr conditionFromDate = EntityCondition.makeCondition("fromDate", EntityOperator.GREATER_THAN_EQUAL_TO, fromDate);
                listConditionsWorkEffort.add(conditionFromDate);
            }
            if (UtilValidate.isEmpty(parameters.get("thruDate"))) {
                thruDate = UtilDateTime.nowTimestamp();
            } else {
                thruDate = Timestamp.valueOf((String) parameters.get("thruDate"));
            }
            EntityExpr conditionThruDate = EntityCondition.makeCondition("thruDate", EntityOperator.LESS_THAN_EQUAL_TO, thruDate);
            listConditionsWorkEffort.add(conditionThruDate);
            EntityExpr conditionParty = EntityCondition.makeCondition("partyId", partyId);
            listConditionsWorkEffort.add(conditionParty);
            ecl = EntityCondition.makeCondition(listConditionsWorkEffort, EntityOperator.AND);
            listWorkEffortTime = EntityQuery.use(delegator)
                    .from("WorkEffortAndTimeEntry")
                    .where(ecl).select("hours", "fromDate", "thruDate")
                    .queryList();
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            ServiceUtil.returnError("Error getting party from person name.");
        }
        List<GenericValue> listCompiled = new ArrayList<>();
        if (UtilValidate.isNotEmpty(listWorkEffortTime)) listCompiled.addAll(listWorkEffortTime);
        if (UtilValidate.isNotEmpty(listPersons)) listCompiled.addAll(listPersons);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("records", listCompiled);
        return result;
    }

    public static Map<String, Object> turnOverPrepareFields(DispatchContext dctx, Map<String, Object> context) {
        Map<String, String> dataMap = UtilMisc.toMap(
                "invoiceTypeId", "short-varchar",
                "invoicePartyId", "short-varchar",
                "statusId", "short-varchar",
                "invoiceDate", "date",
                "dueDate", "date",
                "currencyUomId", "short-varchar",
                "invoiceItemTypeId", "short-varchar",
                "invoiceItemSeqId", "short-varchar",
                "productId", "short-varchar",
                "partyId", "short-varchar",
                "primaryProductCategoryId", "short-varchar",
                "quantity", "numeric",
                "amount", "currency-amount",
                "productStoreId", "short-varchar",
                "storeName", "short-varchar");
        Map<String, String> fieldDisplayLabels = UtilMisc.toMap(
                "invoiceTypeId", "invoice Type",
                "invoicePartyId", "Invoice",
                "statusId", "Status",
                "invoiceDate", "Date",
                "dueDate", "Due date",
                "currencyUomId", "Currency",
                "invoiceItemTypeId", "Invoice type line",
                "invoiceItemSeqId", "Invoice line",
                "productId", "Product",
                "partyId", "Customer",
                "primaryProductCategoryId", "Product category",
                "quantity", "Qty",
                "amount", "Amount",
                "productStoreId", "Product Store",
                "storeName", "Product store name");
        LinkedHashMap<String, String> filterMap = new LinkedHashMap<>();
        filterMap.put("productCategoryId", "short-varchar");
        filterMap.put("productStoreId", "short-varchar");
        filterMap.put("fromDate", "date");
        filterMap.put("thruDate", "date");
        LinkedHashMap<String, String> filterDisplayLabels = new LinkedHashMap<>();
        //it's better to use Label Map, maybe an improvement point !
        filterDisplayLabels.put("productCategoryId", "product Category");
        filterDisplayLabels.put("productStoreId", "product Store");
        filterDisplayLabels.put("fromDate", "from Date");
        filterDisplayLabels.put("thruDate", "through Date");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("dataMap", dataMap);
        result.put("filterMap", filterMap);
        result.put("fieldDisplayLabels", fieldDisplayLabels);
        result.put("filterDisplayLabels", filterDisplayLabels);
        return result;
    }

    public static Map<String, Object> turnOver(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        IReportContext reportContext = (IReportContext) context.get("reportContext");
        Map<String, Object> parameters = UtilGenerics.cast(reportContext.getParameterValue("parameters"));

        List<GenericValue> listTurnOver = null;
        List<Map<String, Object>> listInvoiceEditable = new ArrayList<>();
        List<EntityCondition> listAllConditions = new ArrayList<>();
        try {
            // treating fromDate field condition
            if (UtilValidate.isNotEmpty(parameters.get("fromDate"))) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String fromDateString = (String) parameters.get("fromDate");
                Timestamp fromDate = new Timestamp(sdf.parse(fromDateString).getTime());
                EntityExpr conditionFromDate = EntityCondition.makeCondition("invoiceDate", EntityOperator.GREATER_THAN_EQUAL_TO, fromDate);
                listAllConditions.add(conditionFromDate);
            }

            // treating throughDate field condition
            if (UtilValidate.isNotEmpty(parameters.get("throughDate"))) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String throughDateString = (String) parameters.get("throughDate");
                Timestamp throughDate = new Timestamp(sdf.parse(throughDateString).getTime());
                EntityExpr conditionThroughDate = EntityCondition.makeCondition("invoiceDate", EntityOperator.LESS_THAN_EQUAL_TO, throughDate);
                listAllConditions.add(conditionThroughDate);
            }

            // product category field condition
            if (UtilValidate.isNotEmpty(parameters.get("productCategoryId"))) {
                List<String> productCategoryList = new ArrayList<>();
                if (parameters.get("productCategoryId") instanceof String) {
                    String productCategoryId = (String) parameters.get("productCategoryId");
                    productCategoryList.add(productCategoryId);
                } else if (parameters.get("productStoreId") instanceof String) {
                    productCategoryList = UtilGenerics.cast(parameters.get("productCategoryId"));
                }
                // getting productIds in these categories
                EntityExpr conditionProductCategory = EntityCondition.makeCondition("primaryProductCategoryId", EntityOperator.IN,
                        productCategoryList);
                EntityExpr conditionFromDate = EntityCondition.makeCondition("fromDate", EntityOperator.GREATER_THAN_EQUAL_TO,
                        UtilDateTime.nowTimestamp());
                EntityExpr conditionBeforeDate = EntityCondition.makeCondition("thruDate", EntityOperator.LESS_THAN_EQUAL_TO,
                        UtilDateTime.nowTimestamp());
                EntityExpr conditionNull = EntityCondition.makeCondition("thruDate", null);
                EntityCondition conditionThroughDate = EntityCondition.makeCondition(EntityOperator.OR, UtilMisc.toList(conditionBeforeDate,
                        conditionNull));
                List<GenericValue> listProductIds = EntityQuery.use(delegator)
                        .from("ProductCategoryMember")
                        .where(EntityCondition.makeCondition(UtilMisc.toList(conditionProductCategory, conditionFromDate, conditionThroughDate)))
                        .select("productId").queryList();
                List<String> listProductIdsString = EntityUtil.getFieldListFromEntityList(listProductIds, "productId", true);
                EntityExpr conditionProductCat = EntityCondition.makeCondition("productId", EntityOperator.IN, listProductIdsString);
                listAllConditions.add(conditionProductCat);
            }

            // productStoreId condition
            List<String> productStoreList = new ArrayList<>();
            if (UtilValidate.isNotEmpty(parameters.get("productStoreId"))) {
                if (parameters.get("productStoreId") instanceof String) {
                    String productStoreId = (String) parameters.get("productStoreId");
                    productStoreList.add(productStoreId);
                } else if (parameters.get("productStoreId") instanceof List) {
                    productStoreList = UtilGenerics.cast(parameters.get("productStoreId"));
                }
                // getting list of invoice Ids linked to these productStore
                EntityExpr conditionProductStoreId = EntityCondition.makeCondition("productStoreId", EntityOperator.IN, productStoreList);
                List<GenericValue> listOrderAndProductStores =
                        EntityQuery.use(delegator).from("OrderAndProductStore").where(conditionProductStoreId).select("orderId").queryList();
                List<String> listOrderIds = EntityUtil.getFieldListFromEntityList(listOrderAndProductStores, "orderId", true);
                EntityExpr conditionOrderId = EntityCondition.makeCondition("orderId", EntityOperator.IN, listOrderIds);
                List<GenericValue> listInvoices =
                        EntityQuery.use(delegator).from("OrderItemBilling").where(conditionOrderId).select("invoiceId").queryList();
                List<String> listInvoiceString = EntityUtil.getFieldListFromEntityList(listInvoices, "invoiceId", true);

                EntityExpr conditionInvoiceIdProductStore = EntityCondition.makeCondition("invoiceId", EntityOperator.IN, listInvoiceString);
                listAllConditions.add(conditionInvoiceIdProductStore);
            }

            // adding mandatory conditions
            // condition on invoice item type
            List<String> listInvoiceItemType = UtilMisc.toList("ITM_PROMOTION_ADJ", "INV_PROD_ITEM", "INV_FPROD_ITEM", "INV_DPROD_ITEM",
                    "INV_FDPROD_ITEM", "INV_PROD_FEATR_ITEM");
            listInvoiceItemType.add("ITM_DISCOUNT_ADJ");
            listInvoiceItemType.add("CRT_FPROD_ITEM");
            listInvoiceItemType.add("CRT_DPROD_ITEM");
            listInvoiceItemType.add("CRT_FDPROD_ITEM");
            listInvoiceItemType.add("CRT_SPROD_ITEM");
            listInvoiceItemType.add("CRT_PROMOTION_ADJ");
            listInvoiceItemType.add("CRT_DISCOUNT_ADJ");
            listInvoiceItemType.add("CRT_MAN_ADJ");
            listInvoiceItemType.add("INV_SPROD_ITEM");
            EntityExpr conditionInvoiceItemType = EntityCondition.makeCondition("invoiceItemTypeId", EntityOperator.IN, listInvoiceItemType);
            listAllConditions.add(conditionInvoiceItemType);

            // condition on invoice ((not cancelled) or null)
            EntityExpr conditionStatusNotCancelled = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INVOICE_CANCELLED");
            EntityExpr conditionStatusNull = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, null);
            EntityCondition conditionStatus = EntityCondition.makeCondition(UtilMisc.toList(conditionStatusNotCancelled, conditionStatusNull),
                    EntityOperator.OR);
            listAllConditions.add(conditionStatus);

            // condition sales invoice or customer return invoice
            EntityExpr conditionSalesInvoice = EntityCondition.makeCondition("invoiceTypeId", EntityOperator.IN, UtilMisc.toList("SALES_INVOICE",
                    "CUST_RTN_INVOICE"));
            listAllConditions.add(conditionSalesInvoice);

            // retrieving all invoices
            Set<String> fieldsToSelect = UtilMisc.toSet("invoiceId");
            fieldsToSelect.add("invoiceTypeId");
            fieldsToSelect.add("invoicePartyId");
            fieldsToSelect.add("statusId");
            fieldsToSelect.add("invoiceDate");
            fieldsToSelect.add("dueDate");
            fieldsToSelect.add("currencyUomId");
            fieldsToSelect.add("invoiceItemTypeId");
            fieldsToSelect.add("invoiceItemSeqId");
            fieldsToSelect.add("quantity");
            fieldsToSelect.add("amount");
            fieldsToSelect.add("productId");
            fieldsToSelect.add("partyId");
            fieldsToSelect.add("primaryProductCategoryId");
            listTurnOver = EntityQuery.use(delegator)
                    .from("InvoiceItemProductAndParty")
                    .where(EntityCondition.makeCondition(listAllConditions))
                    .select(fieldsToSelect)
                    .queryList();

            // adding missing fields
            for (GenericValue invoice : listTurnOver) {
                Map<String, Object> invoiceEditableTemp = UtilGenerics.cast(invoice.clone());
                invoiceEditableTemp.remove("GenericEntity");
                Map<String, Object> invoiceEditable = new HashMap<>();
                invoiceEditable.putAll(invoiceEditableTemp);
                invoiceEditable.put("partyName", PartyHelper.getPartyName(delegator, invoice.getString("partyId"), false));

                // adding productStoreId and productStoreName
                EntityExpr conditionInvoiceId = EntityCondition.makeCondition("invoiceId", invoice.getString("invoiceId"));
                //                EntityExpr conditionInvoiceItemSeqId = EntityCondition.makeCondition("invoiceItemSeqId", invoice.getString
                // ("invoiceItemSeqId"));
                //                List<GenericValue> listOrderBilling = delegator.findList("OrderItemBilling", EntityCondition.makeCondition
                //                (UtilMisc.toList(conditionInvoiceId, conditionInvoiceItemSeqId)), UtilMisc.toSet("orderId"), null, null, false);
                GenericValue orderBilling = EntityQuery.use(delegator)
                        .from("OrderItemBilling")
                        .where(conditionInvoiceId)
                        .select("orderId")
                        .queryFirst();
                if (orderBilling != null) {
                    EntityExpr conditionOrderId = EntityCondition.makeCondition("orderId", orderBilling.getString("orderId"));
                    GenericValue productStore = EntityQuery.use(delegator)
                            .from("OrderAndProductStore")
                            .where(conditionOrderId)
                            .cache()
                            .queryFirst();
                    if (UtilValidate.isNotEmpty(productStoreList) && !productStoreList.contains(productStore.getString("productStoreId"))) {
                        continue; // FIXME pretty ugly... but had problems with the rare case where an invoice matches with several orders with
                        // more than one productStore
                    }
                    invoiceEditable.put("productStoreId", productStore.getString("productStoreId"));
                    invoiceEditable.put("storeName", productStore.getString("storeName"));
                } else {
                    invoiceEditable.put("productStoreId", "_NA_");
                    invoiceEditable.put("storeName", "_NA_");
                }
                listInvoiceEditable.add(invoiceEditable);
            }
        } catch (Exception e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(RESOURCE, "BirtErrorRetrievingTurnOver", locale));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("records", listInvoiceEditable);
        return result;
    }
}
