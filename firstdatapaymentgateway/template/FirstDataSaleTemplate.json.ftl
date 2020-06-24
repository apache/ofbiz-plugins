{
  "transactionAmount": {
    "total": "${amount}",
    "currency": "${currency}"
  },
  "requestType": "PaymentCardSaleTransaction",
  "paymentMethod": {
    "paymentCard": {
      "number": "${cardNumber}",
      <#if cardSecurityCode?has_content>
      "securityCode": "${cardSecurityCode}",
      </#if>
      "expiryDate": {
        "month": "${expireMonth}",
        "year": "${expireYear}"
      }
    }
  }
}