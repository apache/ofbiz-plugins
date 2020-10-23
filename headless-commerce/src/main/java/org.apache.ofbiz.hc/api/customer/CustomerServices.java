package org.apache.ofbiz.hc.api.customer;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.party.contact.ContactHelper;
import org.apache.ofbiz.security.SecurityUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomerServices {

    public static final String MODULE = CustomerServices.class.getName();
    public static final String RESOURCE = "PartyUiLabels";
    public static final String SECRESOURCE  = "SecurityextUiLabels";

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
                String errMsg = UtilProperties.getMessage(SECRESOURCE, "loginevents.no_password_hint_specified_try_password_emailed", locale);
                Debug.logError(errMsg, MODULE);
                return ServiceUtil.returnError(errMsg);
            }
        } catch (GenericEntityException gee) {
            Debug.logWarning(gee, "", MODULE);
            return ServiceUtil.returnError(gee.getMessage());
        }
        return result;
    }
    public static Map<String, Object> sendResetPasswordEmail(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        String userLoginId = (String) context.get("username");
        String productStoreId = null;
        String webSiteId = (String) context.get("webSiteId");
        String defaultScreenLocation = "component://securityext/widget/EmailSecurityScreens.xml#PasswordEmail";

        try {
            if (UtilValidate.isNotEmpty(webSiteId)) {
                GenericValue webSite = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).queryOne();
                if (webSite != null) {
                    productStoreId = webSite.getString("productStoreId");
                }
            }
            // test if user exist and is active
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            if (userLogin == null || "N".equals(userLogin.getString("enabled"))) {
                Debug.logError("userlogin unknown or disabled " + userLogin, SECRESOURCE);
                //giving a "sent email to associated email-address" response, to suppress feedback on in-/valid usernames
                return ServiceUtil.returnError(UtilProperties.getMessage(SECRESOURCE, "loginevents.new_password_sent_check_email", locale));
            }

            // check login is associated to a party
            GenericValue userParty = userLogin.getRelatedOne("Party", false);
            if (userParty == null) {
                return ServiceUtil.returnError(UtilProperties.getMessage(SECRESOURCE, "loginevents.username_not_found_reenter", locale));
            }

            // check there is an email to send to
            List<GenericValue> contactMechs = (List<GenericValue>) ContactHelper.getContactMechByPurpose(userParty, "PRIMARY_EMAIL", false);
            if (UtilValidate.isEmpty(contactMechs)) {
                // the email was not found
                return ServiceUtil.returnError(UtilProperties.getMessage(SECRESOURCE, "loginevents.no_primary_email_address_set_contact_customer_service", locale));
            }
            String emails = contactMechs.stream()
                    .map(email -> email.getString("infoString"))
                    .collect(Collectors.joining(","));

            //Generate a JWT with default retention time
            String jwtToken = SecurityUtil.generateJwtToAuthenticateUserLogin(delegator, userLoginId);

            // get the ProductStore email settings
            GenericValue productStoreEmail = null;
            try {
                productStoreEmail = EntityQuery.use(delegator).from("ProductStoreEmailSetting").where("productStoreId",
                        productStoreId, "emailType", "PRDS_PWD_RETRIEVE").queryOne();
            } catch (GenericEntityException e) {
                Debug.logError(e, "Problem getting ProductStoreEmailSetting", MODULE);
            }

            String bodyScreenLocation = null;
            if (productStoreEmail != null) {
                bodyScreenLocation = productStoreEmail.getString("bodyScreenLocation");
            }
            if (UtilValidate.isEmpty(bodyScreenLocation)) {
                bodyScreenLocation = defaultScreenLocation;
            }

            // set the needed variables in new context
            Map<String, Object> bodyParameters = new HashMap<>();
            bodyParameters.put("token", jwtToken);
            bodyParameters.put("locale", locale);
            bodyParameters.put("userLogin", userLogin);
            bodyParameters.put("productStoreId", productStoreId);

            Map<String, Object> serviceContext = new HashMap<>();
            serviceContext.put("bodyScreenUri", bodyScreenLocation);
            serviceContext.put("bodyParameters", bodyParameters);
            serviceContext.put("webSiteId", webSiteId);
            if (productStoreEmail != null) {
                serviceContext.put("subject", productStoreEmail.getString("subject"));
                serviceContext.put("sendFrom", productStoreEmail.get("fromAddress"));
                serviceContext.put("sendCc", productStoreEmail.get("ccAddress"));
                serviceContext.put("sendBcc", productStoreEmail.get("bccAddress"));
                serviceContext.put("contentType", productStoreEmail.get("contentType"));
            } else {
                GenericValue emailTemplateSetting = EntityQuery.use(delegator).from("EmailTemplateSetting").where("emailTemplateSettingId",
                            "EMAIL_PASSWORD").cache().queryOne();
                if (emailTemplateSetting != null) {
                    String subject = emailTemplateSetting.getString("subject");
                    subject = FlexibleStringExpander.expandString(subject, UtilMisc.toMap("userLoginId", userLoginId));
                    serviceContext.put("subject", subject);
                    serviceContext.put("sendFrom", emailTemplateSetting.get("fromAddress"));
                } else {
                    serviceContext.put("subject", UtilProperties.getMessage(SECRESOURCE, "loginservices.password_reminder_subject",
                            UtilMisc.toMap("userLoginId", userLoginId), locale));
                    serviceContext.put("sendFrom", EntityUtilProperties.getPropertyValue("general", "defaultFromEmailAddress", delegator));
                }
            }
            serviceContext.put("sendTo", emails);
            serviceContext.put("partyId", userParty.getString("partyId"));

            Map<String, Object> result = dispatcher.runSync("sendMailHiddenInLogFromScreen", serviceContext);
            if (!ServiceUtil.isSuccess(result)) {
                String errorMessage = ServiceUtil.getErrorMessage(result);
                Map<String, Object> messageMap = UtilMisc.toMap("errorMessage", errorMessage);
                Debug.logError(errorMessage, MODULE);
                return ServiceUtil.returnError(UtilProperties.getMessage(SECRESOURCE, "loginevents.error_unable_email_password_contact_customer_service_errorwas",
                        messageMap, locale));
            }
        } catch (GeneralException e) {
            Debug.logWarning(e, "", MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(SECRESOURCE, "loginevents.error_unable_email_password_contact_customer_service", locale));
        }
        return ServiceUtil.returnSuccess(UtilProperties.getMessage(SECRESOURCE, "loginevents.new_password_sent_check_email", locale));
    }
}
