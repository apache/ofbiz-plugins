package org.apache.ofbiz.hc.api.common;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.hc.api.customer.CustomerServices;

public class CommonUtil {
    public static final String MODULE = CommonUtil.class.getName();

    public static GenericValue getProductStore (Delegator delegator, String webSiteId) throws GenericEntityException {
        if (UtilValidate.isNotEmpty(webSiteId)) {
            GenericValue webSite = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).queryOne();
            if (webSite != null) {
                String productStoreId = webSite.getString("productStoreId");
                if (UtilValidate.isNotEmpty(productStoreId)) {
                    return EntityQuery.use(delegator).from("ProductStore").where("productStoreId", productStoreId).queryOne();
                }
            }
        }
        return null;
    }
    public static boolean isValidCutomer (Delegator delegator, GenericValue userLogin, String customerPartyId) throws GenericEntityException {
        if (UtilValidate.isNotEmpty(customerPartyId) && userLogin != null && customerPartyId.equalsIgnoreCase(userLogin.getString("partyId"))) {
            GenericValue partyRole = EntityQuery.use(delegator).from("PartyRole").where("partyId", customerPartyId, "roleTypeId", "CUSTOMER").queryOne();
            if (partyRole != null) {
                return true;
            }
        }
        return false;
    }
}
