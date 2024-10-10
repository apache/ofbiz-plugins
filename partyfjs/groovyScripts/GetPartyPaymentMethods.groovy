/*
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
 */

import org.apache.ofbiz.accounting.payment.PaymentWorker
import org.apache.ofbiz.accounting.payment.BillingAccountWorker
import org.apache.ofbiz.party.contact.ContactHelper
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.condition.*
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil


partyId = parameters.partyId ?: userLogin.partyId
showOld = "true".equals(parameters.SHOW_OLD)

currencyUomId = null;
paymentInfos = []
paymentInfoList = []
if (partyId) {
    //Retrieve Billing Account
    billingAccountAndRoles = from("BillingAccountAndRole").where("partyId", partyId, "thruDate", null).queryList()
    for (billingAccountAndRole in billingAccountAndRoles) {
        Debug.logInfo(billingAccountAndRole.billingAccountId, "GetPartyPaymentMethods")
        currencyUomId = billingAccountAndRole.accountCurrencyUomId
        if (currencyUomId) billingAccountList = BillingAccountWorker.makePartyBillingAccountList(userLogin, currencyUomId, partyId, delegator, dispatcher)
        for (billingAccount in billingAccountList) {
            Debug.logInfo(billingAccount.toString(), "GetPartyPaymentMethods")
            if (paymentInfoList.contains(billingAccount) == false) {
                paymentInfoList.addAll(billingAccount)
            }
        }
    }
    if (showOld) {
        EntityCondition cond = EntityCondition.makeCondition(
            [EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId),
             EntityCondition.makeCondition("accountThruDate", EntityOperator.NOT_EQUAL, null)
            ], EntityOperator.AND)
        billingAccountExpired = delegator.findList("BillingAccountAndRole", cond, null, null, null, false)
        for (billingAccountAR in billingAccountExpired) {
            billingAccountGV = delegator.findOne("BillingAccount",[billingAccountId : billingAccountAR.billingAccountId] ,true)
            billingAccount = [:]
            billingAccount.putAll(billingAccountGV)
            if (paymentInfoList.contains(billingAccount) == false) {
                paymentInfoList.addAll(billingAccount)
            }
        }
    }
    
    //Add payment methods
    paymentMethodList = PaymentWorker.getPartyPaymentMethodValueMaps(delegator, partyId, showOld)
    if (UtilValidate.isNotEmpty(paymentMethodList)) {
        paymentInfoList.addAll(paymentMethodList)
    }

    //Then create a description string for each information
    for (paymentInfo in paymentInfoList) {
        value = [:]
        //Billing Account
        if (UtilValidate.isNotEmpty(paymentInfo.billingAccountId)) {
            //Retrieve uom abbr
            uomAbbr = ""
            if (paymentInfo.accountCurrencyUomId) {
                GenericValue uom = from("Uom").where("uomId",paymentInfo.accountCurrencyUomId).cache(true).queryOne()
                if (UtilValidate.isNotEmpty(uom)) {
                    uomAbbr = uom.get("abbreviation",locale)
                }
            }

            //Create description for display
            Debug.logInfo(paymentInfo.toString(), "GetPartyPaymentMethods3")
            description = paymentInfo.billingAccountId
            if (paymentInfo.description)    description += " (" + paymentInfo.description+")"
            if (paymentInfo.accountLimit)   description += " (" + uiLabelMap.AccountingAccountLimit + " "+paymentInfo.accountLimit+" "+uomAbbr+")"
            if (paymentInfo.accountBalance) description += " (" + uiLabelMap.AccountingBillingAvailableBalance +" "+ paymentInfo.accountBalance+" "+uomAbbr+")"
            if (paymentInfo.fromDate)       description += " (" + uiLabelMap.CommonUpdated +" : "+ paymentInfo.fromDate+")"
            if (paymentInfo.thruDate)       description += " (" + uiLabelMap.PartyContactEffectiveThru +" : " +paymentInfo.thruDate+")"

            value = UtilMisc.toMap("billingAccountId",paymentInfo.billingAccountId,"type", uiLabelMap.AccountingBilling,"description",description)
            Debug.logInfo(value.toString(), "GetPartyPaymentMethods4")
            
        } else if (UtilValidate.isNotEmpty(paymentInfo.paymentMethod)) {
            paymentMethod = paymentInfo.paymentMethod
            
            //Retrieve payment method type
            type = paymentMethod.paymentMethodTypeId
            paymentMethodType = paymentMethod.getRelatedOne("PaymentMethodType", true)
            if (UtilValidate.isNotEmpty(paymentMethodType)) {
                type = paymentMethodType.get("description",locale)
            }

            //Create description for display
            description = ""

            if ("CREDIT_CARD".equals(paymentMethod.paymentMethodTypeId)) {
                //Credit Card
                creditCard = paymentMethod.getRelatedOne("CreditCard", true)
                if (UtilValidate.isNotEmpty(creditCard)) {
                    if (creditCard.companyNameOnCard) description += " "+ creditCard.companyNameOnCard
                    if (creditCard.titleOnCard)       description += " - "+ creditCard.titleOnCard
                    if (creditCard.firstNameOnCard)   description += " " + creditCard.firstNameOnCard
                    if (creditCard.middleNameOnCard)  description += " " + creditCard.middleNameOnCard
                    if (creditCard.suffixOnCard)      description += " " + creditCard.suffixOnCard
                    if (security.hasEntityPermission("PAY_INFO", "_VIEW", session)) {
                        if (creditCard.cardType)   description += " " + creditCard.cardType
                        cardNumber = creditCard.cardNumber
                        if (UtilValidate.isNotEmpty(cardNumber)) {
                            cardNumberDisplay = ""
                            size = cardNumber.size() - 4
                            if (size >0) {
                                for (int i = 0; i < size-1; i++) {
                                    cardNumberDisplay += "*"
                                }
                                cardNumberDisplay += cardNumber[size .. size + 3]
                                description += " " + cardNumberDisplay
                            }
                        }
                        if (creditCard.expireDate)      description += " " + creditCard.expireDate
                    } else {
                        description += ContactHelper.formatCreditCard(creditCard)
                    }
                }

            } else if ("GIFT_CARD".equals(paymentMethod.paymentMethodTypeId)) {
                //Gift Card
                giftCard = paymentMethod.getRelatedOne("GiftCard", true)
                if (UtilValidate.isNotEmpty(giftCard)) {
                    if (security.hasEntityPermission("PAY_INFO", "_VIEW", session)) {
                        cardNumber = giftCard.cardNumber
                        pinNumber = giftCard.pinNumber
                        if (UtilValidate.isEmpty(cardNumber)) cardNumber = "N/A"
                        if (UtilValidate.isEmpty(pinNumber))  pinNumber  = "N/A"

                        description += " "+ cardNumber + " [" + pinNumber +"]"
                    } else {
                        //Hide card number
                        if (UtilValidate.isNotEmpty(giftCard.cardNumber)) {
                            cardNumberDisplay = ""
                            cardNumber = giftCard.cardNumber
                            size = cardNumber.size() - 4
                            if (size >0) {
                                for (int i = 0; i < size-1; i++) {
                                    cardNumberDisplay += "*"
                                }
                                cardNumberDisplay += cardNumber[size .. size + 3]
                                description += cardNumberDisplay
                            }
                        } else {
                            description += "N/A"
                        }
                    }
                }

            } else if ("EFT_ACCOUNT".equals(paymentMethod.paymentMethodTypeId)) {
                //Eft Account
                eftAccount = paymentMethod.getRelatedOne("EftAccount", true)
                if (UtilValidate.isNotEmpty(eftAccount)) {
                    if (eftAccount.nameOnAccount)    description += " "+ eftAccount.nameOnAccount
                    if (eftAccount.bankName)         description += " - "+ uiLabelMap.PartyBank +" : "+ eftAccount.bankName
                    if (eftAccount.routingNumber && eftAccount.accountNumber)    description += " "+ uiLabelMap.PartyAccount +" :  BIC : "+ eftAccount.routingNumber+", IBAN :"+ eftAccount.accountNumber
                    else if (eftAccount.accountNumber)    description += " "+ uiLabelMap.PartyAccount +" : "+ eftAccount.accountNumber
                }
            }
            if (paymentMethod.description) description += " ("+ paymentMethod.description +")"
            if (paymentMethod.glAccountId) description += " ("+ uiLabelMap.CommonFor +" "+ paymentMethod.glAccountId+")"
            if (paymentMethod.fromDate)    description += " ("+ uiLabelMap.CommonUpdated +" : "+ paymentMethod.fromDate+")"
            if (paymentMethod.thruDate)    {
                description += " ("+ uiLabelMap.PartyContactEffectiveThru +" : "+ paymentMethod.thruDate+")"
                value = UtilMisc.toMap("paymentMethodId",paymentMethod.paymentMethodId,"type", type,"description", description)
            } else  value = UtilMisc.toMap("paymentMethodId",paymentMethod.paymentMethodId,"type", type,"description", description, "activeEdit", "Y")
        }
        paymentInfos.add(value)
    }
}
Debug.logInfo(paymentInfos.toString(), "GetPartyPaymentMethodsFINAL")

context.paymentInfos = paymentInfos
context.showOld = showOld
context.partyId = partyId
