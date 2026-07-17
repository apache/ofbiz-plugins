package org.apache.ofbiz.commerce

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.ObjectType
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.order.order.OrderReadHelper
import org.apache.ofbiz.service.ServiceUtil

Map commerceCreateOrder() {
    Map result = ServiceUtil.returnSuccess()

    // 1. Validation and input extraction
    Map customerMap = parameters.customer
    if (UtilValidate.isEmpty(customerMap)) {
        return ServiceUtil.returnError('customer details are required.')
    }
    String customerExternalId = customerMap.customerExternalId
    if (UtilValidate.isEmpty(customerExternalId)) {
        return ServiceUtil.returnError('customerExternalId is required.')
    }

    List reqItems = parameters.items
    if (UtilValidate.isEmpty(reqItems)) {
        return ServiceUtil.returnError('items list is required and cannot be empty.')
    }

    try {
        // Find or create customer party
        String partyId = null
        GenericValue party = from('Party').where('externalId', customerExternalId).queryFirst()
        if (party) {
            partyId = party.partyId
        } else {
            // Create a new Person party
            Map createPersonCtx = [
                userLogin: userLogin,
                firstName: customerMap.firstName ?: 'First',
                lastName: customerMap.lastName ?: 'Last',
                externalId: customerExternalId
            ]
            Map createPersonResult = runService('createPerson', createPersonCtx)
            if (ServiceUtil.isError(createPersonResult)) {
                return ServiceUtil.returnError('Failed to create customer party: ' + ServiceUtil.getErrorMessage(createPersonResult))
            }
            partyId = createPersonResult.partyId
        }

        // Email setup
        if (customerMap.email) {
            Map emailRes = runService('getPartyEmail', [partyId: partyId, contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
            if (ServiceUtil.isError(emailRes) || !emailRes.emailAddress) {
                runService('createPartyEmailAddress', [
                    userLogin: userLogin,
                    partyId: partyId,
                    contactMechPurposeTypeId: 'PRIMARY_EMAIL',
                    emailAddress: customerMap.email
                ])
            }
        }

        // Phone setup
        if (customerMap.phone) {
            String phoneStr = customerMap.phone
            String countryCode = ''
            String areaCode = ''
            String contactNumber = phoneStr
            if (phoneStr.startsWith('+')) {
                String[] parts = phoneStr.substring(1).split('-')
                if (parts.length >= 3) {
                    countryCode = parts[0]
                    areaCode = parts[1]
                    contactNumber = parts[2..-1].join('-')
                } else if (parts.length == 2) {
                    areaCode = parts[0]
                    contactNumber = parts[1]
                }
            } else {
                String[] parts = phoneStr.split('-')
                if (parts.length >= 3) {
                    countryCode = parts[0]
                    areaCode = parts[1]
                    contactNumber = parts[2..-1].join('-')
                } else if (parts.length == 2) {
                    areaCode = parts[0]
                    contactNumber = parts[1]
                }
            }
            Map phoneRes = runService('getPartyTelephone', [partyId: partyId, contactMechPurposeTypeId: 'PRIMARY_PHONE'])
            if (ServiceUtil.isError(phoneRes) || !phoneRes.contactNumber) {
                runService('createPartyTelecomNumber', [
                    userLogin: userLogin,
                    partyId: partyId,
                    contactMechPurposeTypeId: 'PRIMARY_PHONE',
                    countryCode: countryCode,
                    areaCode: areaCode,
                    contactNumber: contactNumber
                ])
            }
        }

        // Helper closures with explicit type declarations instead of def
        Closure<String> cleanStr = { Object obj ->
            if (obj) {
                return obj.toString().trim()
            }
            return ''
        }

        Closure<Boolean> isAddressEqual = { GenericValue addr, Map addrMap ->
            return cleanStr(addr.toName) == cleanStr(addrMap.toName) &&
                   cleanStr(addr.address1) == cleanStr(addrMap.address1) &&
                   cleanStr(addr.city) == cleanStr(addrMap.city) &&
                   cleanStr(addr.stateProvinceGeoId) == cleanStr(addrMap.stateProvinceGeoId) &&
                   cleanStr(addr.countryGeoId) == cleanStr(addrMap.countryGeoId) &&
                   cleanStr(addr.postalCode) == cleanStr(addrMap.postalCode)
        }

        Closure<String> getOrCreatePostalAddress = { Map addrMap, String purposeTypeId ->
            if (!addrMap) {
                return null
            }
            List addresses = from('PartyAndPostalAddress')
                .where('partyId', partyId)
                .filterByDate()
                .queryList()
            for (Object addrObj : addresses) {
                GenericValue addr = (GenericValue) addrObj
                if (isAddressEqual(addr, addrMap)) {
                    return addr.contactMechId
                }
            }
            Map createAddrCtx = [
                userLogin: userLogin,
                partyId: partyId,
                contactMechPurposeTypeId: purposeTypeId,
                toName: addrMap.toName,
                address1: addrMap.address1,
                city: addrMap.city,
                stateProvinceGeoId: addrMap.stateProvinceGeoId,
                countryGeoId: addrMap.countryGeoId,
                postalCode: addrMap.postalCode
            ]
            Map createAddrResult = runService('createPartyPostalAddress', createAddrCtx)
            if (ServiceUtil.isError(createAddrResult)) {
                throw new IllegalArgumentException('Error creating postal address: ' + ServiceUtil.getErrorMessage(createAddrResult))
            }
            return (String) createAddrResult.contactMechId
        }

        String billingContactMechId = getOrCreatePostalAddress(parameters.billingAddress, 'BILLING_LOCATION')
        String shippingContactMechId = getOrCreatePostalAddress(parameters.shippingAddress, 'SHIPPING_LOCATION')

        // 2. Persist Order Header
        String orderId = delegator.getNextSeqId('OrderHeader')

        java.sql.Timestamp orderDate = parseDateTime(parameters.orderDate) ?: UtilDateTime.nowTimestamp()
        java.sql.Timestamp entryDate = parseDateTime(parameters.entryDate) ?: UtilDateTime.nowTimestamp()

        GenericValue orderHeader = delegator.makeValue('OrderHeader', [
            orderId: orderId,
            orderTypeId: parameters.orderTypeId ?: 'SALES_ORDER',
            orderName: parameters.orderName,
            externalId: parameters.externalId,
            salesChannelEnumId: parameters.salesChannelEnumId ?: 'WEB_SALES_CHANNEL',
            orderDate: orderDate,
            entryDate: entryDate,
            priority: parameters.priority,
            currencyUom: parameters.currencyCode ?: 'USD',
            statusId: parameters.status ?: 'ORDER_CREATED'
        ])
        if (parameters.agreements && parameters.agreements.size() > 0) {
            orderHeader.agreementId = parameters.agreements[0].agreementId
        }
        delegator.create(orderHeader)

        // Ensure Party roles exist & associate them with the order
        Closure<Void> ensurePartyRole = { String pId, String rTypeId ->
            GenericValue pr = from('PartyRole').where('partyId', pId, 'roleTypeId', rTypeId).queryOne()
            if (!pr) {
                delegator.create('PartyRole', [partyId: pId, roleTypeId: rTypeId])
            }
        }
        ensurePartyRole(partyId, 'PLACING_CUSTOMER')
        ensurePartyRole(partyId, 'BILL_TO_CUSTOMER')
        ensurePartyRole(partyId, 'SHIP_TO_CUSTOMER')

        delegator.create('OrderRole', [orderId: orderId, partyId: partyId, roleTypeId: 'PLACING_CUSTOMER'])
        delegator.create('OrderRole', [orderId: orderId, partyId: partyId, roleTypeId: 'BILL_TO_CUSTOMER'])
        delegator.create('OrderRole', [orderId: orderId, partyId: partyId, roleTypeId: 'SHIP_TO_CUSTOMER'])

        // Order Contact Mechs (wrapped for line length rule)
        if (shippingContactMechId) {
            delegator.create('OrderContactMech', [
                orderId: orderId,
                contactMechId: shippingContactMechId,
                contactMechPurposeTypeId: 'SHIPPING_LOCATION'
            ])
        }
        if (billingContactMechId) {
            delegator.create('OrderContactMech', [
                orderId: orderId,
                contactMechId: billingContactMechId,
                contactMechPurposeTypeId: 'BILLING_LOCATION'
            ])
        }

        // Create Default Ship Group
        String shipGroupSeqId = '00001'
        Map shipGroupData = [
            orderId: orderId,
            shipGroupSeqId: shipGroupSeqId,
            carrierPartyId: '_NA_'
        ]
        Map firstItem = reqItems[0]
        if (firstItem.shipmentMethodTypeId) {
            shipGroupData.shipmentMethodTypeId = firstItem.shipmentMethodTypeId
        }
        if (firstItem.shippingInstructions) {
            shipGroupData.shippingInstructions = firstItem.shippingInstructions
        }
        if (firstItem.facilityExternalId) {
            shipGroupData.facilityId = firstItem.facilityExternalId
        }
        if (shippingContactMechId) {
            shipGroupData.contactMechId = shippingContactMechId
        }
        delegator.create('OrderItemShipGroup', shipGroupData)

        // 3. Persist Items and Adjustments
        int itemSeq = 1
        for (Object reqItemObj : reqItems) {
            Map reqItem = (Map) reqItemObj
            GenericValue product = from('Product').where('productId', reqItem.sku).queryOne()
            if (!product) {
                return ServiceUtil.returnError('Product not found with SKU/productId: ' + reqItem.sku)
            }
            String orderItemSeqId = String.format('%05d', itemSeq)

            java.sql.Timestamp shipBeforeDate = parseDateTime(reqItem.shipByDate)
            java.sql.Timestamp shipAfterDate = parseDateTime(reqItem.shipAfterDate)
            java.sql.Timestamp estShipDate = parseDateTime(reqItem.estimatedShipDate)
            java.sql.Timestamp estDeliveryDate = parseDateTime(reqItem.estimatedDeliveryDate)

            GenericValue orderItem = delegator.makeValue('OrderItem', [
                orderId: orderId,
                orderItemSeqId: orderItemSeqId,
                orderItemTypeId: 'PRODUCT_ORDER_ITEM',
                externalId: reqItem.itemExternalId,
                productId: product.productId,
                quantity: reqItem.quantity ? new BigDecimal(reqItem.quantity) : BigDecimal.ONE,
                unitPrice: reqItem.unitAmount ? new BigDecimal(reqItem.unitAmount) : BigDecimal.ZERO,
                statusId: reqItem.status ?: 'ITEM_CREATED',
                shipBeforeDate: shipBeforeDate,
                shipAfterDate: shipAfterDate,
                estimatedShipDate: estShipDate,
                estimatedDeliveryDate: estDeliveryDate
            ])
            delegator.create(orderItem)

            // Associate Item to Ship Group
            delegator.create('OrderItemShipGroupAssoc', [
                orderId: orderId,
                orderItemSeqId: orderItemSeqId,
                shipGroupSeqId: shipGroupSeqId,
                quantity: reqItem.quantity ? new BigDecimal(reqItem.quantity) : BigDecimal.ONE
            ])

            // Item level adjustments (using each to avoid NestedForLoop)
            if (reqItem.adjustments) {
                reqItem.adjustments.each { Object adjObj ->
                    Map adj = (Map) adjObj
                    String adjId = delegator.getNextSeqId('OrderAdjustment')
                    GenericValue orderAdj = delegator.makeValue('OrderAdjustment', [
                        orderAdjustmentId: adjId,
                        orderAdjustmentTypeId: adj.type,
                        orderId: orderId,
                        orderItemSeqId: orderItemSeqId,
                        shipGroupSeqId: '_NA_',
                        amount: adj.amount ? new BigDecimal(adj.amount) : null,
                        productPromoId: adj.productPromoId
                    ])
                    delegator.create(orderAdj)
                }
            }

            itemSeq++
        }

        // 4. Persist Order Level Adjustments
        if (parameters.adjustments) {
            for (Object adjObj : parameters.adjustments) {
                Map adj = (Map) adjObj
                String adjId = delegator.getNextSeqId('OrderAdjustment')
                GenericValue orderAdj = delegator.makeValue('OrderAdjustment', [
                    orderAdjustmentId: adjId,
                    orderAdjustmentTypeId: adj.type,
                    orderId: orderId,
                    orderItemSeqId: '_NA_',
                    shipGroupSeqId: '_NA_',
                    amount: adj.amount ? new BigDecimal(adj.amount) : null,
                    productPromoId: adj.productPromoId
                ])
                delegator.create(orderAdj)
            }
        }

        // 5. Persist Payment Preferences & Gateway Response
        if (parameters.paymentPreferences) {
            for (Object prefObj : parameters.paymentPreferences) {
                Map pref = (Map) prefObj
                String prefId = delegator.getNextSeqId('OrderPaymentPreference')
                GenericValue orderPaymentPreference = delegator.makeValue('OrderPaymentPreference', [
                    orderPaymentPreferenceId: prefId,
                    orderId: orderId,
                    paymentMethodTypeId: pref.paymentMethodTypeId,
                    statusId: pref.statusId ?: 'PAYMENT_NOT_RECEIVED',
                    maxAmount: pref.maxAmount ? new BigDecimal(pref.maxAmount) : null
                ])
                delegator.create(orderPaymentPreference)

                if (pref.transactionId) {
                    String respId = delegator.getNextSeqId('PaymentGatewayResponse')
                    GenericValue paymentGatewayResponse = delegator.makeValue('PaymentGatewayResponse', [
                        paymentGatewayResponseId: respId,
                        orderPaymentPreferenceId: prefId,
                        paymentMethodTypeId: pref.paymentMethodTypeId,
                        amount: pref.maxAmount ? new BigDecimal(pref.maxAmount) : null,
                        currencyUomId: pref.currencyUomId ?: parameters.currencyCode ?: 'USD',
                        referenceNum: pref.transactionId,
                        transactionDate: UtilDateTime.nowTimestamp()
                    ])
                    delegator.create(paymentGatewayResponse)
                }
            }
        }

        // 6. Persist Attributes
        if (parameters.attributes) {
            for (Object attrObj : parameters.attributes) {
                Map attr = (Map) attrObj
                GenericValue orderAttr = delegator.makeValue('OrderAttribute', [
                    orderId: orderId,
                    attrName: attr.name,
                    attrValue: attr.value
                ])
                delegator.create(orderAttr)
            }
        }

        // 7. Persist Notes
        if (parameters.note) {
            for (Object noteMsg : parameters.note) {
                String noteStr = String.valueOf(noteMsg)
                String noteId = delegator.getNextSeqId('NoteData')
                GenericValue noteData = delegator.makeValue('NoteData', [
                    noteId: noteId,
                    noteInfo: noteStr,
                    noteDateTime: UtilDateTime.nowTimestamp()
                ])
                delegator.create(noteData)

                GenericValue orderNote = delegator.makeValue('OrderHeaderNote', [
                    orderId: orderId,
                    noteId: noteId,
                    internalNote: 'N'
                ])
                delegator.create(orderNote)
            }
        }

        result.orderId = orderId
    } catch (Exception e) {
        Debug.logError(e, 'Error creating order via commerce-api: ' + e.getMessage(), 'OrderServices')
        return ServiceUtil.returnError('Error creating order: ' + e.getMessage())
    }

    return result
}

Map commerceGetOrder() {
    Map result = ServiceUtil.returnSuccess()

    String orderId = parameters.orderId
    GenericValue orderHeader = from('OrderHeader').where('orderId', orderId).queryOne()
    if (!orderHeader) {
        return ServiceUtil.returnError('Order not found with ID: ' + orderId)
    }

    OrderReadHelper orh = new OrderReadHelper(orderHeader)

    result.putAll([
        orderId: orderHeader.orderId,
        externalId: orderHeader.externalId,
        orderName: orderHeader.orderName,
        orderTypeId: orderHeader.orderTypeId,
        salesChannelEnumId: orderHeader.salesChannelEnumId,
        orderDate: formatDateTime(orderHeader.orderDate),
        entryDate: formatDateTime(orderHeader.entryDate),
        priority: orderHeader.priority,
        currencyCode: orderHeader.currencyUom,
        status: orderHeader.statusId
    ])

    // 1. Customer (reusing existing getPartyEmail and getPartyTelephone services)
    GenericValue placingCustomer = orh.getPlacingParty()
    if (placingCustomer) {
        Map customerMap = [:]
        GenericValue party = from('Party').where('partyId', placingCustomer.partyId).queryOne()
        customerMap.customerExternalId = party?.externalId
        if (placingCustomer.getEntityName() == 'Person') {
            customerMap.firstName = placingCustomer.firstName
            customerMap.lastName = placingCustomer.lastName
        } else if (placingCustomer.getEntityName() == 'PartyGroup') {
            customerMap.lastName = placingCustomer.groupName
        }

        // Email via getPartyEmail service
        Map emailResult = runService('getPartyEmail', [partyId: placingCustomer.partyId, contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        if (ServiceUtil.isSuccess(emailResult) && emailResult.emailAddress) {
            customerMap.email = emailResult.emailAddress
        } else {
            emailResult = runService('getPartyEmail', [partyId: placingCustomer.partyId, contactMechPurposeTypeId: 'ORDER_EMAIL'])
            if (ServiceUtil.isSuccess(emailResult) && emailResult.emailAddress) {
                customerMap.email = emailResult.emailAddress
            }
        }

        // Phone via getPartyTelephone service
        Map phoneResult = runService('getPartyTelephone', [partyId: placingCustomer.partyId, contactMechPurposeTypeId: 'PRIMARY_PHONE'])
        if (ServiceUtil.isSuccess(phoneResult) && phoneResult.contactNumber) {
            List phoneParts = []
            if (phoneResult.countryCode) {
                String cc = phoneResult.countryCode
                if (!cc.startsWith('+')) {
                    cc = '+' + cc
                }
                phoneParts << cc
            }
            if (phoneResult.areaCode) {
                phoneParts << phoneResult.areaCode
            }
            if (phoneResult.contactNumber) {
                phoneParts << phoneResult.contactNumber
            }
            customerMap.phone = phoneParts.join('-')
        }
        result.customer = customerMap
    }

    // Helper closure to format address (returns empty map & uses ternary to solve CodeNarc warnings)
    Closure<Map> formatAddress = { GenericValue address ->
        return address ? [
            toName: address.toName,
            address1: address.address1,
            city: address.city,
            stateProvinceGeoId: address.stateProvinceGeoId,
            countryGeoId: address.countryGeoId,
            postalCode: address.postalCode
        ] : [:]
    }

    // 2. Addresses
    result.billingAddress = formatAddress(orh.getBillingLocations()?.first())
    result.shippingAddress = formatAddress(orh.getShippingLocations()?.first())

    // All order adjustments (loaded once for performance)
    List allAdjustments = from('OrderAdjustment').where('orderId', orderId).queryList()

    // 3. Items
    List items = []
    List orderItems = orh.getOrderItems()
    for (Object itemObj : orderItems) {
        GenericValue item = (GenericValue) itemObj
        // Initialize Map directly to prevent UnnecessaryObjectReferences
        Map itemMap = [
            itemExternalId: item.externalId,
            sku: item.productId,
            shipAfterDate: formatDateTime(item.shipAfterDate),
            shipByDate: formatDateTime(item.shipBeforeDate),
            estimatedShipDate: formatDateTime(item.estimatedShipDate),
            estimatedDeliveryDate: formatDateTime(item.estimatedDeliveryDate),
            status: item.statusId,
            quantity: item.quantity,
            unitAmount: item.unitPrice
        ]

        GenericValue product = item.getRelatedOne('Product', false)
        itemMap.taxable = (product?.taxable == 'Y')

        // Ship group details (facility, shipping method, instructions)
        GenericValue shipGroupAssoc = from('OrderItemShipGroupAssoc')
            .where('orderId', orderId, 'orderItemSeqId', item.orderItemSeqId)
            .queryFirst()
        if (shipGroupAssoc) {
            GenericValue shipGroup = from('OrderItemShipGroup')
                .where('orderId', orderId, 'shipGroupSeqId', shipGroupAssoc.shipGroupSeqId)
                .queryOne()
            if (shipGroup) {
                itemMap.shipmentMethodTypeId = shipGroup.shipmentMethodTypeId
                itemMap.shippingInstructions = shipGroup.shippingInstructions
                if (shipGroup.facilityId) {
                    itemMap.facilityExternalId = shipGroup.facilityId
                }
            }
        }

        // Item Adjustments
        List itemAdjustments = allAdjustments.findAll { it.orderItemSeqId == item.orderItemSeqId }
        if (itemAdjustments) {
            itemMap.adjustments = itemAdjustments.collect { adj ->
                [
                    type: adj.orderAdjustmentTypeId,
                    amount: adj.amount,
                    productPromoId: adj.productPromoId
                ]
            }
        }
        items << itemMap
    }
    result.items = items

    // 4. Order Level Adjustments
    List orderAdjustments = allAdjustments.findAll { it.orderItemSeqId == null || it.orderItemSeqId == '_NA_' }
    if (orderAdjustments) {
        result.adjustments = orderAdjustments.collect { adj ->
            [
                type: adj.orderAdjustmentTypeId,
                amount: adj.amount,
                productPromoId: adj.productPromoId
            ]
        }
    }

    // 5. Payment Preferences
    List paymentPrefs = from('OrderPaymentPreference').where('orderId', orderId).queryList()
    if (paymentPrefs) {
        result.paymentPreferences = paymentPrefs.collect { pref ->
            GenericValue gatewayResponse = from('PaymentGatewayResponse')
                .where('orderPaymentPreferenceId', pref.orderPaymentPreferenceId)
                .queryFirst()
            [
                paymentMethodTypeId: pref.paymentMethodTypeId,
                statusId: pref.statusId,
                maxAmount: pref.maxAmount,
                currencyUomId: gatewayResponse?.currencyUomId ?: orderHeader.currencyUom,
                transactionId: gatewayResponse?.referenceNum ?: gatewayResponse?.paymentGatewayResponseId ?: ''
            ]
        }
    }

    // 6. Agreements
    if (orderHeader.agreementId) {
        result.agreements = [[agreementId: orderHeader.agreementId]]
    }

    // 7. Attributes
    List attrs = from('OrderAttribute').where('orderId', orderId).queryList()
    if (attrs) {
        result.attributes = attrs.collect { attr ->
            [
                name: attr.attrName,
                value: attr.attrValue
            ]
        }
    }

    // 8. Notes
    List noteAssocs = from('OrderHeaderNote').where('orderId', orderId).queryList()
    if (noteAssocs) {
        List notes = []
        for (Object assocObj : noteAssocs) {
            GenericValue assoc = (GenericValue) assocObj
            GenericValue noteData = assoc.getRelatedOne('NoteData', false)
            if (noteData?.noteInfo) {
                notes << noteData.noteInfo
            }
        }
        result.note = notes
    }

    return result
}

private String formatDateTime(java.sql.Timestamp timestamp) {
    if (!timestamp) {
        return null
    }
    return timestamp.toInstant().atZone(ZoneId.of('UTC')).format(DateTimeFormatter.ISO_INSTANT)
}

private java.sql.Timestamp parseDateTime(String dateStr) {
    if (!dateStr) {
        return null
    }
    try {
        return java.sql.Timestamp.from(java.time.Instant.parse(dateStr))
    } catch (Exception e) {
        try {
            return java.sql.Timestamp.from(java.time.OffsetDateTime.parse(dateStr).toInstant())
        } catch (Exception ex) {
            Debug.logWarning('Failed to parse ISO-8601 date string [' + dateStr + '], using simpleTypeConvert: ' + ex.getMessage(), 'OrderServices')
            return ObjectType.simpleTypeConvert(dateStr, 'Timestamp', null, null)
        }
    }
}
