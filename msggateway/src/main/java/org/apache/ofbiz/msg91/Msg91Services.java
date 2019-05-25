package org.apache.ofbiz.msg91;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.HttpClient;
import org.apache.ofbiz.base.util.HttpClientException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Msg91Services {
    public final static String module = Msg91Services.class.getName();

    public static Map<String, Object> sendMsg91Sms(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        
        List<String> numbers = UtilGenerics.checkList(context.get("numbers"));
        String message = (String) context.get("message");

        try {
            GenericValue msg91GatewayConfig = EntityQuery.use(delegator).from("Msg91GatewayConfig").queryFirst();
            if (msg91GatewayConfig != null) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("country", msg91GatewayConfig.getString("country"));
                paramMap.put("sender", msg91GatewayConfig.getString("sender"));
                paramMap.put("route", msg91GatewayConfig.getString("route"));
                paramMap.put("mobiles", numbers);
                paramMap.put("message", message);
                paramMap.put("authkey", msg91GatewayConfig.getString("authkey"));

                HttpClient httpClient = new HttpClient(msg91GatewayConfig.getString("apiUrl"), paramMap);
                httpClient.setHeader("content-type", "application/text");
                String response = httpClient.get();
                result.put("response", response);
            } else {
                Debug.logError("Message not sent as the telecom gateway configuration settings are not found", module);
                return ServiceUtil.returnError("Message not sent as the telecom gateway configuration settings are not found");
            }
        } catch (GenericEntityException | HttpClientException e) {
            Debug.logError(e.getMessage(), module);
            return ServiceUtil.returnError(e.getMessage());
        }
        return result;
    }
}
