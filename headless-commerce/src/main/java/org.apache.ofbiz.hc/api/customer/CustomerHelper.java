package org.apache.ofbiz.hc.api.customer;

import jdk.nashorn.internal.runtime.logging.DebugLogger;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
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
}
