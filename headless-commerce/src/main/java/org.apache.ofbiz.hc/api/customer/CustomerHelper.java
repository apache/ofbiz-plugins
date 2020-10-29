package org.apache.ofbiz.hc.api.customer;

import jdk.nashorn.internal.runtime.logging.DebugLogger;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.party.party.PartyHelper;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerHelper {
    public static final String MODULE = CustomerServices.class.getName();

    public static Map<String, Object> getLoyaltyPoints(LocalDispatcher dispatcher, String customerPartyId, GenericValue userLogin) throws GenericServiceException {
        Map<String, Object> loyaltyPointMap = new HashMap<>();
        if (UtilValidate.isNotEmpty(customerPartyId)) {
            //TODO: Need to make it configurable
            int monthsToInclude = 12;
            Map<String, Object> result = dispatcher.runSync("getOrderedSummaryInformation",
                    UtilMisc.toMap("partyId", customerPartyId, "roleTypeId", "PLACING_CUSTOMER",
                            "orderTypeId", "SALES_ORDER", "statusId", "ORDER_COMPLETED", "monthsToInclude", monthsToInclude, "userLogin", userLogin));
            if (!ServiceUtil.isSuccess(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                return null;
            }
            loyaltyPointMap.put("points", result.get("totalSubRemainingAmount"));
            loyaltyPointMap.put("totalOrders", result.get("totalOrders"));
            loyaltyPointMap.put("monthsToInclude", monthsToInclude);
        }
        return loyaltyPointMap;
    }
    public static List<Map<String, String>> prepareContactMechPurposeList(Delegator delegator, List<GenericValue> partyContactMechPurposes) throws GenericEntityException {
        List<Map<String, String>> contactMechPurposes = new ArrayList<>();
        for (GenericValue partyContactMechPurpose : partyContactMechPurposes) {
            Map<String, String> purposeMap = new HashMap<>();
            GenericValue contactMechPurposeType = EntityQuery.use(delegator).from("ContactMechPurposeType").where("contactMechPurposeTypeId", partyContactMechPurpose.getString("contactMechPurposeTypeId")).queryOne();
            purposeMap.put("contactMechPurposeTypeId", contactMechPurposeType.getString("contactMechPurposeTypeId"));
            purposeMap.put("description", contactMechPurposeType.getString("description"));
            contactMechPurposes.add(purposeMap);
        }
        return contactMechPurposes;
    }
    public static List<Map<String, Object>> getPartyContactLists(Delegator delegator, String customerPartyId) throws GenericEntityException {
        List<Map<String, Object>> subscriptionList = new ArrayList<>();
        List<GenericValue> contactListPartyList = EntityQuery.use(delegator).from("ContactListParty").where("partyId", customerPartyId).orderBy("-fromDate").queryList();
        for (GenericValue contactListParty : contactListPartyList) {
            Map<String, Object> infoMap = new HashMap<>();
            GenericValue contactList = EntityQuery.use(delegator).from("ContactList").where("contactListId", contactListParty.getString("contactListId")).queryOne();
            GenericValue statusItem = EntityQuery.use(delegator).from("StatusItem").where("statusId", contactListParty.getString("statusId")).queryOne();
            GenericValue emailContactMech = EntityQuery.use(delegator).select("infoString").from("ContactMech").where("contactMechId", contactListParty.getString("preferredContactMechId"), "contactMechTypeId", "EMAIL_ADDRESS").queryOne();
            infoMap.put("contactListId", contactList.getString("contactListId"));
            infoMap.put("email", emailContactMech.getString("infoString"));
            infoMap.put("contactListName", contactList.getString("contactListName"));
            infoMap.put("description", contactList.getString("description"));
            infoMap.put("fromDate", contactListParty.getString("fromDate"));
            infoMap.put("thruDate", contactListParty.getString("thruDate"));
            infoMap.put("status", UtilMisc.toMap("statusId", statusItem.getString("statusId"),
                    "description", statusItem.getString("description"), "statusCode", statusItem.getString("statusCode")));
            subscriptionList.add(infoMap);
        }
        return subscriptionList;
    }
    public static List<Map<String, Object>> getPartyCommunications(Delegator delegator, String customerPartyId, boolean showReceived, boolean showSent) throws GenericEntityException {
        List<Map<String, Object>> communications = new ArrayList<>();
        EntityCondition condition = null;
        if (showReceived && showSent) {
            condition = EntityCondition.makeCondition(
                    EntityCondition.makeCondition("partyIdTo", customerPartyId),
                    EntityOperator.OR,
                    EntityCondition.makeCondition("partyIdFrom", customerPartyId));
        } else if (showReceived) {
            condition = EntityCondition.makeCondition("partyIdTo", customerPartyId);
        } else if (showSent) {
            condition = EntityCondition.makeCondition("partyIdFrom", customerPartyId);
        }

        List<GenericValue> communicationEvents = EntityQuery.use(delegator).from("CommunicationEvent").where(condition).orderBy("-entryDate").queryList();
        for (GenericValue communicationEvent : communicationEvents) {
            Map<String, Object> infoMap = new HashMap<>();
            String fromPartyName = PartyHelper.getPartyName(delegator, communicationEvent.getString("partyIdFrom"), true) ;
            String toPartyName = PartyHelper.getPartyName(delegator, communicationEvent.getString("partyIdTo"), true) ;
            infoMap.put("communicationEventId", communicationEvent.getString("communicationEventId"));
            infoMap.put("fromPartyName", fromPartyName);
            infoMap.put("toPartyName", toPartyName);
            infoMap.put("subject", communicationEvent.getString("subject"));
            infoMap.put("entryDate", communicationEvent.getString("entryDate"));
            infoMap.put("content", communicationEvent.getString("content"));
            communications.add(infoMap);
        }
        return communications;
    }
}

