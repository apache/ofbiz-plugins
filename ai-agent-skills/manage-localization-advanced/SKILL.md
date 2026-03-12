<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

---
name: manage-localization-advanced
description: Manage advanced localization in OFBiz, including date/time formatting, currency conversion, and locale-specific data handling beyond simple labels.
---

# Manage Localization Advanced Skill

This skill covers localization techniques in OFBiz that go beyond standard UI labels, focusing on data formatting and locale-aware logic.

## Date and Time Localization

Use `UtilDateTime` for date arithmetic and locale-specific date retrieval.

### Pattern: Formatting Dates
```java
// Format date for specific locale and timezone
String formattedDate = UtilFormatOut.formatDate(myDate, "yyyy-MM-dd", locale, timeZone);

// Format date-time for specific locale and timezone
String formattedDateTime = UtilFormatOut.formatDateTime(myTimestamp, "yyyy-MM-dd HH:mm", locale, timeZone);
```

### Pattern: Date Arithmetic
```java
// Get start of month for a timestamp
Timestamp monthStart = UtilDateTime.getMonthStart(nowTimestamp, timeZone, locale);

// Add days to timestamp
Timestamp weekLater = UtilDateTime.addDaysToTimestamp(nowTimestamp, 7);
```

## Currency and Number Formatting

Use `UtilFormatOut` and `UtilNumber` for currency and numeric formatting.

### Pattern: Formatting Currency
```java
// Format currency based on ISO code and locale
String priceStr = UtilFormatOut.formatCurrency(priceBigDecimal, "USD", locale);

// Format percentage
String percentStr = UtilFormatOut.formatPercentage(discountDouble);
```

## UOM (Unit of Measure) System

OFBiz uses the `Uom` and `UomConversion` entities for units of measure and currency conversion.

### Pattern: Currency Conversion
```java
// Convert currency using standard service
Map<String, Object> results = dispatcher.runSync("convertUom", UtilMisc.toMap(
    "uomId", "USD",
    "uomIdTo", "EUR",
    "originalValue", priceAmount
));
BigDecimal convertedValue = (BigDecimal) results.get("convertedValue");
```

## Localized Data Storage

For properties stored in the database (e.g., system settings), use `EntityUtilProperties`.

### Pattern: Localized DB Properties
```xml
<!-- In SystemProperty.xml -->
<SystemProperty systemResourceId="general" systemPropertyId="currency.uom.id.default" systemPropertyValue="USD"/>
```

```java
// Retrieve localized property from DB
String currencyId = EntityUtilProperties.getPropertyValue("general", "currency.uom.id.default", delegator);
```

## Best Practices

1. **Always Pass Locale/TimeZone**: Never rely on `Locale.getDefault()` or `TimeZone.getDefault()` in multi-user web applications; always use the user's session locale and timezone.
2. **Use UOM Entities**: Prefer the `Uom` system for dimensions, weights, and currencies rather than hardcoding units.
3. **Handle GMT**: Use `UtilDateTime.toGmtTimestampString()` for logging or external API integrations that require a standard time representation.
4. **Prefer UtilFormatOut**: Use the utility classes for formatting to ensure consistency across the application.
