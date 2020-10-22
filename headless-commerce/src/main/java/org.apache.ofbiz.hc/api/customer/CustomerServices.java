package org.apache.ofbiz.hc.api.customer;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.Locale;
import java.util.Map;

public class CustomerServices {

    public static final String MODULE = CustomerServices.class.getName();
    public static final String RESOURCE = "PartyUiLabels";

    public static Map<String, Object> getPasswordHint(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String userLoginId = (String) context.get("username");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        String passwordHint = null;

        try {
            String  usernameLowercase = EntityUtilProperties.getPropertyValue("security", "username.lowercase", delegator);
            if (UtilValidate.isNotEmpty(userLoginId) && "true".equals(usernameLowercase)) {
                userLoginId = userLoginId.toLowerCase(locale);
            }

            GenericValue supposedUserLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            if (supposedUserLogin != null) {
                passwordHint = supposedUserLogin.getString("passwordHint");
                result.put("username", userLoginId);
                result.put("passwordHint", passwordHint);
            }
            if (supposedUserLogin == null || UtilValidate.isEmpty(passwordHint)) {
                // the Username was not found or there was no hint for the Username
                String errMsg = UtilProperties.getMessage("SecurityextUiLabels", "loginevents.no_password_hint_specified_try_password_emailed", locale);
                Debug.logError(errMsg, MODULE);
                return ServiceUtil.returnError(errMsg);
            }
        } catch (GenericEntityException gee) {
            Debug.logWarning(gee, "", MODULE);
            return ServiceUtil.returnError(gee.getMessage());
        }
        return result;
    }
}
