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
package org.apache.ofbiz.ecommerce.shoppinglist

import org.apache.ofbiz.base.util.UtilHttp
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.order.shoppingcart.ShoppingCart
import org.apache.ofbiz.order.shoppingcart.ShoppingCartEvents
import org.apache.ofbiz.order.shoppingcart.shipping.ShippingEstimateWrapper
import org.apache.ofbiz.order.shoppinglist.ShoppingListServices
import org.apache.ofbiz.party.contact.ContactHelper
import org.apache.ofbiz.product.catalog.CatalogWorker
import org.apache.ofbiz.product.store.ProductStoreWorker
import org.apache.ofbiz.service.calendar.RecurrenceInfo
import org.apache.ofbiz.webapp.website.WebSiteWorker

if (!userLogin) {
    return // session ended, prevents a NPE
}
GenericValue party = userLogin.getRelatedOne('Party', false)

ShoppingCart cart = ShoppingCartEvents.getCartObject(request)
currencyUomId = cart.getCurrency()

String productStoreId = ProductStoreWorker.getProductStoreId(request)
String prodCatalogId = CatalogWorker.getCurrentCatalogId(request)
String webSiteId = WebSiteWorker.getWebSiteId(request)

context.productStoreId = productStoreId
context.currencyUomId = currencyUomId

// get the top level shopping lists for the logged in user
EntityCondition condition = new EntityConditionBuilder().AND {
    EQUALS(partyId: userLogin.partyId)
    NOT_EQUAL(listName: 'auto-save')
}
List allShoppingLists = from('ShoppingList')
        .where(condition)
        .orderBy('listName')
        .queryList()
List shoppingLists = EntityUtil.filterByAnd(allShoppingLists, [parentShoppingListId: null])
context.allShoppingLists = allShoppingLists
context.shoppingLists = shoppingLists

// get all shoppingListTypes
context.shoppingListTypes = from('ShoppingListType').orderBy('description').cache().queryList()

// get the shoppingListId for this request
Map parameterMap = UtilHttp.getParameterMap(request)
String shoppingListId = parameterMap.shoppingListId ?: request.getAttribute('shoppingListId') ?: session.getAttribute('currentShoppingListId')
context.shoppingListId = shoppingListId

// no passed shopping list id default to first list
if (!shoppingListId && shoppingLists) {
    shoppingListId = shoppingLists.first().shoppingListId
}
session.setAttribute('currentShoppingListId', shoppingListId)

// if we passed a shoppingListId get the shopping list info
if (shoppingListId) {
    GenericValue shoppingList = from('ShoppingList').where(shoppingListId: shoppingListId).queryOne()
    context.shoppingList = shoppingList

    if (shoppingList) {
        BigDecimal shoppingListItemTotal = 0.0
        BigDecimal shoppingListChildTotal = 0.0

        List shoppingListItems = shoppingList.getRelated('ShoppingListItem', null, null, true)
        if (shoppingListItems) {
            List shoppingListItemDatas = []
            shoppingListItems.each { shoppingListItem ->
                Map shoppingListItemData = [:]
                GenericValue product = shoppingListItem.getRelatedOne('Product', true)
                Map calcPriceOutMap = run service: 'calculateProductPrice', with: [product: product,
                                                                                   quantity: shoppingListItem.quantity,
                                                                                   currencyUomId: currencyUomId,
                                                                                   webSiteId: webSiteId,
                                                                                   prodCatalogId: prodCatalogId,
                                                                                   productStoreId: productStoreId]
                BigDecimal price = calcPriceOutMap.price
                BigDecimal totalPrice = price * shoppingListItem.quantity
                // similar code at ShoppingCartItem.java getRentalAdjustment
                if (['ASSET_USAGE', 'ASSET_USAGE_OUT_IN'].contains(product.productTypeId)) {
                    persons = shoppingListItem.reservPersons ?: 0
                    reservNthPPPerc = product.reservNthPPPerc ?: 0
                    reserv2ndPPPerc = product.reserv2ndPPPerc ?: 0
                    rentalValue = 0
                    if (persons) {
                        if (persons > 2) {
                            persons -= 2
                            if (reservNthPPPerc) {
                                rentalValue = persons * reservNthPPPerc
                            } else if (reserv2ndPPPerc) {
                                rentalValue = persons * reserv2ndPPPerc
                            }
                            persons = 2
                        }
                        if (persons == 2) {
                            if (reserv2ndPPPerc) {
                                rentalValue += reserv2ndPPPerc
                            } else if (reservNthPPPerc) {
                                rentalValue = persons * reservNthPPPerc
                            }
                        }
                    }
                    rentalValue += 100 // add final 100 percent for first person
                    reservLength = shoppingListItem.reservLength ?: 0
                    totalPrice *= (rentalValue / 100 * reservLength)
                }
                shoppingListItemTotal += totalPrice

                productVariantAssocs = null
                if (product.isVirtual == 'Y') {
                    productVariantAssocs = product.getRelated('MainProductAssoc', [productAssocTypeId: 'PRODUCT_VARIANT'], ['sequenceNum'], true)
                    productVariantAssocs = EntityUtil.filterByDate(productVariantAssocs)
                }
                shoppingListItemData.shoppingListItem = shoppingListItem
                shoppingListItemData.product = product
                shoppingListItemData.unitPrice = price
                shoppingListItemData.totalPrice = totalPrice
                shoppingListItemData.productVariantAssocs = productVariantAssocs
                shoppingListItemDatas.add(shoppingListItemData)
            }
            context.shoppingListItemDatas = shoppingListItemDatas
            // pagination for the shopping list
            viewIndex = Integer.valueOf(parameters.VIEW_INDEX ?: 1)
            viewSize = parameters.VIEW_SIZE ? Integer.valueOf(parameters.VIEW_SIZE) : visualTheme.getModelTheme().getDefaultViewSize() ?: 20
            listSize = shoppingListItemDatas ? shoppingListItemDatas.size() : 0

            lowIndex = ((viewIndex - 1) * viewSize) + 1
            highIndex = viewIndex * viewSize
            highIndex = highIndex > listSize ? listSize : highIndex
            lowIndex = lowIndex > highIndex ? highIndex : lowIndex

            context.viewIndex = viewIndex
            context.viewSize = viewSize
            context.listSize = listSize
            context.lowIndex = lowIndex
            context.highIndex = highIndex
        }

        shoppingListType = shoppingList.getRelatedOne('ShoppingListType', false)
        context.shoppingListType = shoppingListType

        // get the child shopping lists of the current list for the logged in user
        List childShoppingLists = from('ShoppingList')
                .where(partyId: userLogin.partyId, parentShoppingListId: shoppingListId)
                .orderBy('listName')
                .cache()
                .queryList()
        // now get prices for each child shopping list...
        if (childShoppingLists) {
            List childShoppingListDatas = []
            childShoppingLists.each { childShoppingList ->
                Map childShoppingListData = [:]

                Map childShoppingListPriceMap = run service: 'calculateShoppingListDeepTotalPrice', with:
                        [shoppingListId: childShoppingList.shoppingListId,
                         prodCatalogId: prodCatalogId,
                         webSiteId: webSiteId,
                         currencyUomId: currencyUomId]
                BigDecimal totalPrice = childShoppingListPriceMap.totalPrice
                shoppingListChildTotal += totalPrice

                childShoppingListData.childShoppingList = childShoppingList
                childShoppingListData.totalPrice = totalPrice
                childShoppingListDatas << childShoppingListData
            }
            context.childShoppingListDatas = childShoppingListDatas
        }
        context.shoppingListTotalPrice = shoppingListItemTotal + shoppingListChildTotal
        context.shoppingListItemTotal = shoppingListItemTotal
        context.shoppingListChildTotal = shoppingListChildTotal

        // get the parent shopping list if there is one
        parentShoppingList = shoppingList.getRelatedOne('ParentShoppingList', false)
        context.parentShoppingList = parentShoppingList

        context.canView = userLogin.partyId == shoppingList.partyId

        // auto-reorder info
        if (shoppingListType?.shoppingListTypeId == 'SLT_AUTO_REODR') {
            recurrenceVo = shoppingList.getRelatedOne('RecurrenceInfo', false)
            context.recurrenceInfo = recurrenceVo

            if (userLogin.partyId == shoppingList.partyId) {
                // get customer's shipping & payment info
                context.chosenShippingMethod = shoppingList.shipmentMethodTypeId + '@' + shoppingList.carrierPartyId
                context.shippingContactMechList = ContactHelper.getContactMech(party, 'SHIPPING_LOCATION', 'POSTAL_ADDRESS', false)
                context.paymentMethodList = EntityUtil.filterByDate(party.getRelated('PaymentMethod', null, ['paymentMethodTypeId'], false))

                GenericValue shipAddress = from('PostalAddress').where(contactMechId: shoppingList.contactMechId).queryOne()
                if (shipAddress) {
                    ShoppingCart listCart = ShoppingListServices.makeShoppingListCart(dispatcher, shoppingListId, locale)
                    if (listCart) {
                        ShippingEstimateWrapper shippingEstWpr = new ShippingEstimateWrapper(dispatcher, listCart, 0)
                        context.listCart = listCart
                        context.shippingEstWpr = shippingEstWpr
                        context.carrierShipMethods = shippingEstWpr.getShippingMethods()
                    }
                }

                if (recurrenceVo) {
                    recInfo = new RecurrenceInfo(recurrenceVo)
                    context.recInfo = recInfo
                    lastSlOrderDate = shoppingList.lastOrderedDate
                    context.lastSlOrderDate = lastSlOrderDate
                    lastSlOrderDate = lastSlOrderDate ?: recurrenceVo.startDateTime
                    context.lastSlOrderTime = lastSlOrderDate.getTime()
                }
            }
        }
    }
}
