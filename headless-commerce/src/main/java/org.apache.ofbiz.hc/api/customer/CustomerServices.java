package org.apache.ofbiz.hc.api.customer;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.hc.api.common.CommonUtil;
import org.apache.ofbiz.party.contact.ContactHelper;
import org.apache.ofbiz.party.contact.ContactMechWorker;
import org.apache.ofbiz.security.SecurityUtil;
import org.apache.ofbiz.service.*;

import java.util.*;
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
            GenericValue productStore = CommonUtil.getProductStore(delegator, webSiteId);
            if (UtilValidate.isNotEmpty(productStore)) {
                productStoreId = productStore.getString("productStoreId");
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
    public static Map<String, Object> changePassword(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String customerPartyId = (String) context.get("customerPartyId");

        try {
            if (!CommonUtil.isValidCutomer(delegator, userLogin, customerPartyId)) {
                String errorMessage = UtilProperties.getMessage("HeadlessCommerceUiLabels", "HCAccessDeniedInvalidUser", locale);
                Debug.logError(errorMessage, MODULE);
                return ServiceUtil.returnError(errorMessage);
            }
            Map<String, Object> serviceContext = dctx.getModelService("updatePassword").makeValid(context, ModelService.IN_PARAM);
            serviceContext.put("userLoginId", userLogin.getString("userLoginId"));
            serviceContext.put("userLogin", userLogin);
            Map<String, Object> result = dispatcher.runSync("updatePassword", serviceContext);
            if (!ServiceUtil.isSuccess(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
            }
        } catch (GenericEntityException | GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createCustomer(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> result;
        Map<String, Object> serviceCtx;
        String webSiteId = (String) context.get("webSiteId");
        String firstName = (String) context.get("firstName");
        String middleName = (String) context.get("middleName");
        String lastName = (String) context.get("lastName");
        String emailAddress = (String) context.get("emailAddress");
        String username =  (String) context.get("username");
        String password = (String) context.get("password");
        String confirmPassword = (String) context.get("confirmPassword");
        String passwordHint = (String) context.get("passwordHint");
        Map<String, String> shippingAddress = UtilGenerics.cast(context.get("shippingAddress"));
        Map<String, String> homePhone = UtilGenerics.cast(context.get("homePhone"));
        Map<String, String> workPhone = UtilGenerics.cast(context.get("workPhone"));
        Map<String, String> faxNumber = UtilGenerics.cast(context.get("faxNumber"));
        Map<String, String> mobilePhone = UtilGenerics.cast(context.get("mobilePhone"));
        List<String> errorList;
        GenericValue productStore = null;

        try {
            result = dispatcher.runSync("validateCreateCustomer", context);
            if (!ServiceUtil.isSuccess(result)) {
                errorList = UtilGenerics.cast(result.get("errorMessageList"));
                Debug.logError("parameter validation exception", errorList.toString());
                return ServiceUtil.returnError(errorList);
            }
            GenericValue system = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();

            //fetching product store
            if (UtilValidate.isNotEmpty(webSiteId)) {
                productStore = CommonUtil.getProductStore(delegator, webSiteId);
            }

            //checking lowercase settings
            boolean usernameLowercase = Boolean.parseBoolean(EntityUtilProperties.getPropertyValue("security", "username.lowercase", "false", delegator));
            boolean passwordLowercase = Boolean.parseBoolean(EntityUtilProperties.getPropertyValue("security", "password.lowercase", "false", delegator));
            username = usernameLowercase ? username.toLowerCase(): username;
            password = passwordLowercase ? password.toLowerCase(): password;
            confirmPassword = passwordLowercase ? confirmPassword.toLowerCase(): confirmPassword;

            //create userlogin
            serviceCtx = dctx.getModelService("createPersonAndUserLogin").makeValid(context, ModelService.IN_PARAM);
            serviceCtx.put("userLoginId", username);
            serviceCtx.put("currentPassword", password);
            serviceCtx.put("currentPasswordVerify", confirmPassword);
            serviceCtx.put("passwordHint", passwordHint);
            result = dispatcher.runSync("createPersonAndUserLogin", serviceCtx);
            if (!ServiceUtil.isSuccess(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
            }
            GenericValue createdUserLogin = (GenericValue) result.get("newUserLogin");
            String userPartyId = (String) result.get("partyId");

            //Assigning customer role
            serviceCtx.put("partyId", userPartyId);
            serviceCtx.put("roleTypeId", "CUSTOMER");
            serviceCtx.put("userLogin", createdUserLogin);
            result = dispatcher.runSync("createPartyRole", serviceCtx);
            if (!ServiceUtil.isSuccess(result)) {
                Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
            }

            //shipping address
            if (UtilValidate.isNotEmpty(shippingAddress)) {
                String countryGeoId;
                String stateProvinceGeoId = null;
                GenericValue countryGeo = EntityQuery.use(delegator).from("Geo").where("geoTypeId", "COUNTRY", "abbreviation", shippingAddress.get("country")).queryFirst();
                countryGeoId = UtilValidate.isNotEmpty(countryGeo) ? countryGeo.getString("geoId") : null;

                if (UtilValidate.isNotEmpty(shippingAddress.get("state"))) {
                    GenericValue regionGeo = EntityQuery.use(delegator).from("GeoAssocAndGeoToWithState").
                            where("geoIdFrom", countryGeoId, "geoAssocTypeId", "REGIONS", "geoCode", shippingAddress.get("state")).queryFirst();
                    stateProvinceGeoId = UtilValidate.isEmpty(regionGeo) ? regionGeo.getString("geoId") : null;
                }

                serviceCtx.clear();
                serviceCtx = dctx.getModelService("createPartyPostalAddress").makeValid(shippingAddress, ModelService.IN_PARAM);
                serviceCtx.put("toName", firstName + " " + middleName + " " + lastName);
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("stateProvinceGeoId", stateProvinceGeoId);
                serviceCtx.put("countryGeoId", countryGeoId);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyPostalAddress", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");

                //create the shipping location
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "SHIPPING_LOCATION");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }

                //also consider this address the general correspondence address
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "GENERAL_LOCATION");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

            //home phone
            if (UtilValidate.isNotEmpty(homePhone)) {
                serviceCtx.clear();
                serviceCtx = dctx.getModelService("createPartyTelecomNumber").makeValid(homePhone, ModelService.IN_PARAM);
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyTelecomNumber", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");

                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "PHONE_HOME");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                //also consider as primary phone
                serviceCtx.put("contactMechPurposeTypeId", "PRIMARY_PHONE");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

            //work phone
            if (UtilValidate.isNotEmpty(workPhone)) {
                serviceCtx.clear();
                serviceCtx = dctx.getModelService("createPartyTelecomNumber").makeValid(workPhone, ModelService.IN_PARAM);
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyTelecomNumber", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");

                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "PHONE_WORK");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

            //fax number
            if (UtilValidate.isNotEmpty(faxNumber)) {
                serviceCtx.clear();
                serviceCtx = dctx.getModelService("createPartyTelecomNumber").makeValid(faxNumber, ModelService.IN_PARAM);
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyTelecomNumber", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "FAX_NUMBER");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }


            //mobile phone
            if (UtilValidate.isNotEmpty(mobilePhone)) {
                serviceCtx.clear();
                serviceCtx = dctx.getModelService("createPartyTelecomNumber").makeValid(mobilePhone, ModelService.IN_PARAM);
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyTelecomNumber", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "PHONE_MOBILE");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

            //email address
            if (UtilValidate.isNotEmpty(emailAddress)) {
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("emailAddress", emailAddress);
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyEmailAddress", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
                String contactMechId = (String) result.get("contactMechId");
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("contactMechId", contactMechId);
                serviceCtx.put("contactMechPurposeTypeId", "PRIMARY_EMAIL");
                serviceCtx.put("userLogin", createdUserLogin);
                result = dispatcher.runSync("createPartyContactMechPurpose", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

            //club number
            GenericValue person = EntityQuery.use(delegator).from("Person").where("partyId", userPartyId).queryOne();
            if (UtilValidate.isNotEmpty(context.get("requireClub"))) {
                if (person != null && UtilValidate.isEmpty(context.get("clubNumber")) ) {
                    String clubId = org.apache.ofbiz.party.party.PartyWorker.createClubId(delegator, "999", 13);
                    person.set("memberId", clubId);
                    person.store();
                }
            }

            if (productStore != null) {
                //Associate to Product Store
                String productStoreId = productStore.getString("productStoreId");
                serviceCtx.clear();
                serviceCtx.put("partyId", userPartyId);
                serviceCtx.put("roleTypeId", "CUSTOMER");
                serviceCtx.put("productStoreId", productStoreId);
                serviceCtx.put("userLogin", system);
                result = dispatcher.runSync("createProductStoreRole", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }

                //send off the registration email
                if (UtilValidate.isNotEmpty(emailAddress)) {
                    GenericValue productStoreEmailSetting = EntityQuery.use(delegator).from("ProductStoreEmailSetting").where("productStoreId", productStoreId, "emailType", "PRDS_CUST_REGISTER").queryFirst();
                    if (productStoreEmailSetting != null) {
                        serviceCtx.clear();
                        Map<String, Object> bodyParameters = new HashMap<>();
                        bodyParameters.put("person", person);
                        serviceCtx.put("bodyParameters", bodyParameters);
                        serviceCtx.put("sendTo", emailAddress);
                        serviceCtx.put("subject", productStoreEmailSetting.getString("subject"));
                        serviceCtx.put("sendFrom", productStoreEmailSetting.getString("fromAddress"));
                        serviceCtx.put("sendCc", productStoreEmailSetting.getString("ccAddress"));
                        serviceCtx.put("sendBcc", productStoreEmailSetting.getString("bccAddress"));
                        serviceCtx.put("contentType", productStoreEmailSetting.getString("contentType"));
                        serviceCtx.put("bodyScreenUri", productStoreEmailSetting.getString("bodyScreenLocation"));
                        serviceCtx.put("emailType", "PRDS_CUST_REGISTER");
                        dispatcher.runAsync("sendMailFromScreen", serviceCtx);
                    }
                }
            }

            //assign security group to manage profile
            if (createdUserLogin != null) {
                serviceCtx.clear();
                serviceCtx.put("userLoginId", createdUserLogin.getString("userLoginId"));
                serviceCtx.put("groupId", "ECOMMERCE_CUSTOMER");
                serviceCtx.put("userLogin", system);
                result = dispatcher.runSync("addUserLoginToSecurityGroup", serviceCtx);
                if (!ServiceUtil.isSuccess(result)) {
                    Debug.logError(ServiceUtil.getErrorMessage(result), MODULE);
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
                }
            }

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> validateCreateCustomer(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String emailAddress =  (String) context.get("emailAddress");
        String username =  (String) context.get("username");
        String password = (String) context.get("password");
        String passwordHint = (String) context.get("passwordHint");
        String confirmPassword = (String) context.get("confirmPassword");
        Map<String, String> shippingAddress = UtilGenerics.cast(context.get("shippingAddress"));
        Map<String, String> homePhone = UtilGenerics.cast(context.get("homePhone"));
        Map<String, String> workPhone = UtilGenerics.cast(context.get("workPhone"));
        Map<String, String> faxNumber = UtilGenerics.cast(context.get("faxNumber"));
        Map<String, String> mobilePhone = UtilGenerics.cast(context.get("mobilePhone"));
        List<String> errorList = new ArrayList<>();

        try {
            if (!UtilValidate.isEmail(emailAddress)) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyEmailAddressNotFormattedCorrectly", locale));
            }
            if (UtilValidate.isNotEmpty(shippingAddress)) {
                if (UtilValidate.isEmpty(shippingAddress.get("address1"))) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyAddressLine1MissingError", locale));
                }
                if (UtilValidate.isEmpty(shippingAddress.get("city"))) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyCityMissing", locale));
                }
                if (UtilValidate.isEmpty(shippingAddress.get("postalCode"))) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyZipCodeMissing", locale));
                }
                if (UtilValidate.isEmpty(shippingAddress.get("country"))) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyCountryMissing", locale));
                }
                String country = shippingAddress.get("country");
                String state = shippingAddress.get("state");
                if ("USA".equals(country) && UtilValidate.isEmpty(state)) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyStateInUsMissing", locale));
                }
                if ("CAN".equals(country) && UtilValidate.isEmpty(state)) {
                    errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyStateInCanadaMissing", locale));
                }
                GenericValue countryGeo = EntityQuery.use(delegator).from("Geo").where("geoTypeId", "COUNTRY", "abbreviation", country).queryFirst();
                if (UtilValidate.isNotEmpty(country) && countryGeo == null) {
                    errorList.add(UtilProperties.getMessage("HeadlessCommerceUiLabels", "HCInvalidCountryCode",
                            UtilMisc.toMap("countryCode", country), locale));
                }
                if (countryGeo != null && UtilValidate.isNotEmpty(state)) {
                    GenericValue regionGeo = EntityQuery.use(delegator).from("GeoAssocAndGeoToWithState").
                            where("geoIdFrom", countryGeo.getString("geoId"), "geoAssocTypeId", "REGIONS", "geoCode", state).queryFirst();
                    if (UtilValidate.isEmpty(regionGeo)) {
                        // if given state is not associated with country then return error.
                        errorList.add(UtilProperties.getMessage("HeadlessCommerceUiLabels", "HCCountrysStateIsNotValid",
                                UtilMisc.toMap("stateCode", state, "countryCode", country), locale));
                    }
                }
                
            }
            if (UtilValidate.isNotEmpty(homePhone) && UtilValidate.isEmpty(homePhone.get("contactNumber"))) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyContactNumberMissing", locale));
            }
            if (UtilValidate.isNotEmpty(workPhone) && UtilValidate.isEmpty(workPhone.get("contactNumber"))) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyContactNumberMissing", locale));
            }
            if (UtilValidate.isNotEmpty(faxNumber) && UtilValidate.isEmpty(faxNumber.get("contactNumber"))) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyContactNumberMissing", locale));
            }
            if (UtilValidate.isNotEmpty(mobilePhone) && UtilValidate.isEmpty(mobilePhone.get("contactNumber"))) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyContactNumberMissing", locale));
            }
            if (!password.equals(confirmPassword)) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyPasswordMatchError", locale));
            }

            GenericValue existingUser = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", username).queryOne();
            if (existingUser != null) {
                errorList.add(UtilProperties.getMessage("PartyUiLabels", "PartyUserNameInUse", locale));
            }

            //Check the password, etc for validity
            GenericValue newUserLogin = delegator.makeValue("UserLogin");
            newUserLogin.set("userLoginId", username);
            newUserLogin.set("currentPassword", password);
            newUserLogin.set("passwordHint", passwordHint);
            LoginServices.checkNewPassword(newUserLogin, null, password, confirmPassword, passwordHint, errorList, true, locale);

        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (UtilValidate.isNotEmpty(errorList)) {
            return ServiceUtil.returnError(errorList);
        }
        return ServiceUtil.returnSuccess();
    }
    public static Map<String, Object> getCustomerProfile(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String customerPartyId = (String) context.get("customerPartyId");
        String showOldStr = (String) context.get("showOld");
        Map <String, Object> response = ServiceUtil.returnSuccess();

        try {
            if (!CommonUtil.isValidCutomer(delegator, userLogin, customerPartyId)) {
                String errorMessage = UtilProperties.getMessage("HeadlessCommerceUiLabels", "HCAccessDeniedInvalidUser", locale);
                Debug.logError(errorMessage, MODULE);
                return ServiceUtil.returnError(errorMessage);
            }
            boolean showOld = "true".equals(showOldStr);
            GenericValue person = EntityQuery.use(delegator).from("Person").where("partyId", customerPartyId).queryFirst();
            if (person != null) {
                response.put("customerPartyId", person.getString("partyId"));
                response.put("personalTitle", person.getString("personalTitle"));
                response.put("firstName", person.getString("firstName"));
                response.put("middleName", person.getString("middleName"));
                response.put("lastName", person.getString("lastName"));
                response.put("suffix", person.getString("suffix"));

                List<Map<String, Object>> contactMechs = new ArrayList<>();
                List<Map<String, Object>> partyContactMechValueMaps = ContactMechWorker.getPartyContactMechValueMaps(delegator, customerPartyId, showOld);
                for (Map<String, Object> partyContactMechValueMap : partyContactMechValueMaps) {
                    Map<String, Object> infoMap = new HashMap<>();
                    GenericValue contactMech = (GenericValue) partyContactMechValueMap.get("contactMech");
                    GenericValue contactMechType = (GenericValue) partyContactMechValueMap.get("contactMechType");
                    infoMap.put("contactMechId", contactMech.getString("contactMechId"));
                    if (UtilValidate.isNotEmpty(partyContactMechValueMap.get("postalAddress"))) {
                        //postal address
                        GenericValue postalAddress = (GenericValue) partyContactMechValueMap.get("postalAddress");
                        if (postalAddress != null) {
                            infoMap.put("toName", postalAddress.getString("toName"));
                            infoMap.put("address1", postalAddress.getString("address1"));
                            infoMap.put("address2", postalAddress.getString("address2"));
                            infoMap.put("city", postalAddress.getString("city"));
                            infoMap.put("postalCode", postalAddress.getString("postalCode"));

                            Map<String, String> geoMap = new HashMap<>();
                            GenericValue countryGeo = EntityQuery.use(delegator).from("Geo").where("geoId", postalAddress.getString("countryGeoId")).queryOne();
                            if (countryGeo != null) {
                                geoMap.put("geoId", countryGeo.getString("geoId"));
                                geoMap.put("geoName", countryGeo.getString("geoName"));
                                geoMap.put("geoCode", countryGeo.getString("abbreviation"));
                                infoMap.put("country", geoMap);
                            }
                            geoMap = new HashMap<>();
                            GenericValue stateGeo = EntityQuery.use(delegator).from("Geo").where("geoId", postalAddress.getString("stateProvinceGeoId")).queryOne();
                            if (stateGeo != null) {
                                geoMap.put("geoId", stateGeo.getString("geoId"));
                                geoMap.put("geoName", stateGeo.getString("geoName"));
                                geoMap.put("geoCode", stateGeo.getString("abbreviation"));
                                infoMap.put("state", geoMap);
                            }
                            infoMap.put("contactMechPurposes", CustomerHelper.prepareContactMechPurposeList(
                                    delegator, UtilGenerics.cast(partyContactMechValueMap.get("partyContactMechPurposes"))));
                            contactMechs.add(infoMap);
                        }
                    } else if (UtilValidate.isNotEmpty(partyContactMechValueMap.get("telecomNumber"))) {
                        //phone number
                        GenericValue telecomNumber = (GenericValue) partyContactMechValueMap.get("telecomNumber");
                        if (telecomNumber != null) {
                            infoMap.put("countryCode", telecomNumber.getString("countryCode"));
                            infoMap.put("areaCode", telecomNumber.getString("areaCode"));
                            infoMap.put("contactNumber", telecomNumber.getString("contactNumber"));
                            infoMap.put("contactMechPurposes", CustomerHelper.prepareContactMechPurposeList(
                                    delegator, UtilGenerics.cast(partyContactMechValueMap.get("partyContactMechPurposes"))));
                            contactMechs.add(infoMap);
                        }
                    } else {
                        //email, web address, electronic address, etc.
                        infoMap.put("infoString", contactMech.getString("infoString"));
                    }

                    //contact mech type
                    Map<String, String> contactMechTypeMap = new HashMap<>();
                    contactMechTypeMap.put("contactMechTypeId", contactMechType.getString("contactMechTypeId"));
                    contactMechTypeMap.put("description", contactMechType.getString("description"));
                    infoMap.put("contactMechType", contactMechTypeMap);

                    //contact mech purpose
                    if (UtilValidate.isNotEmpty(partyContactMechValueMap.get("partyContactMechPurposes"))) {
                        infoMap.put("contactMechPurposes", CustomerHelper.prepareContactMechPurposeList(
                                delegator, UtilGenerics.cast(partyContactMechValueMap.get("partyContactMechPurposes"))));
                    }
                    contactMechs.add(infoMap);
                }
                response.put("contactMechs", contactMechs);
                response.put("loyaltyPoints", CustomerHelper.getLoyaltyPoints(dispatcher, customerPartyId, userLogin));
            }
        } catch (GenericEntityException | GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        return response;
    }
}