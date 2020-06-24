{
  "transactionAmount": {
    "total": "${amount}",
    "currency": "${currency}"
  },
  "requestType": "PaymentCardPreAuthTransaction",
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