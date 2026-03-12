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
name: manage-email-services
description: Configure and implement email services in OFBiz.
---

# Skill: manage-email-services

## Goal
Configure SMTP infrastructure and implement reliable, template-driven business notifications using OFBiz Screen Widgets, FreeMarker templates, and CommunicationEvent records.

## Triggers
**ALWAYS** read this skill when:
- Creating new email notifications or modifying existing ones.
- Configuring SMTP settings in `general.properties`.
- Managing `ProductStoreEmailSetting` or `EmailTemplateSetting` entities.

## Use when
- Sending automated business notifications.
- Designing email templates with consistent branding.
- Attaching PDF documents (e.g., invoices) to emails.

## Procedure

1.  **SMTP Configuration**:
    - Configure `framework/common/config/general.properties`:
    ```properties
    mail.notifications.enabled=Y
    mail.smtp.relay.host=smtp.example.com
    mail.smtp.port=465
    mail.smtp.auth=true
    mail.smtp.auth.user=user@example.com
    mail.smtp.auth.password=password
    mail.smtp.starttls.enable=Y
    mail.smtp.ssl.enable=true
    ```
    - **Note**: `mail.smtp.starttls.enable` often requires `Y` in OFBiz, while other boolean flags use `true`.
    - Use `mail.notifications.redirectTo` in development to intercept all outgoing mail.

2.  **Template Definition**:
    - **Commerce Emails**: Prefer **`ProductStoreEmailSetting`** for order-to-cash flows.
        - Examples: Order confirmations, Shipment notifications, Return confirmations.
    - **System Emails**: Use **`EmailTemplateSetting`** for generic/administrative alerts.
        - Examples: Password reset, User invitations, Administrative alerts.
    - **Full Compatibility**: Use `altBodyScreenLocation` to provide a plain-text fallback for HTML emails.
    - Example XML Data:
    ```xml
    <ProductStoreEmailSetting productStoreId="9000" emailType="PRDS_ODR_CONFIRM" 
        bodyScreenLocation="component://ecommerce/widget/EmailOrderScreens.xml#OrderConfirmNotice" 
        altBodyScreenLocation="component://ecommerce/widget/EmailOrderScreens.xml#OrderConfirmNoticeText"
        subject="Order Confirmation #${orderId}" fromAddress="orders@example.com"/>
    ```

3.  **Store & WebSite Mapping**:
    - `ProductStoreEmailSetting` relies on the **`WebSite`** entity (via `productStoreId`) to determine the base URL for links.
    - Ensure your `WebSite` has `standardHostName` and `secureHostName` configured to avoid broken links in emails.

4.  **Screen Design**:
    - Create a screen in your component's `widget/EmailScreens.xml`.
    - Ensure all links and images use **absolute URLs**.
    - **Pro-Tip**: Use `NotificationServices.setBaseUrl(delegator, webSiteId, bodyParameters)` in your logic before sending to populate `${baseUrl}` in FTL.

5.  **Service Invocation**:
    - Use `sendMailFromScreen` for granular control and PDF attachments.
    - Use `sendMailFromTemplateSetting` for simple entity-driven sending.
    - **ALWAYS** use `runAsync` (asynchronous) for production mailing to prevent thread blocking.

6.  **Handling Attachments**:
    - **PDF Generation**: Use `xslfoAttachScreenLocation` to render a Screen Widget as a PDF using Apache FOP.
    - **General Files**: Use the `bodyParts` parameter (list of Maps) to attach existing files.

7.  **Bulk and High-Volume Mailing**:
    - For high-volume notifications (e.g., shipment updates), use **Job Queuing**.
    - **Pattern**: Iterate through your targets and schedule the mailing service via `dispatcher.schedule`.
    - **NEVER** send thousands of emails in a single service execution or a single DB transaction.

## CommunicationEvent Lifecycle

In OFBiz, emails are not just "logged"—they are managed as **Communication Events**. This is a core architectural pattern that ensures every outbound email has a central source of truth.

### Why it Matters
- **Customer Service Visibility**: Agents can see exactly what was sent to a customer from any module (Order, Marketing, SFA).
- **Business Audit Trail**: Emails are linked to business documents (e.g., `OrderHeader`) via `CommunicationEventOrder`.
- **Interaction History**: Provides a complete timeline of customer touchpoints.
- **Compliance**: Ensures legal and support records are archived within the database, not just on a mail server.

### The Lifecycle Pattern
1.  **Creation**: A service (e.g., `sendOrderNotificationScreen`) invokes `sendMailFromScreen`.
2.  **Persistence**: The `sendMail` service family includes SECA (Service ECA) triggers that automatically create a `CommunicationEvent` record.
3.  **Linkage**: The `communicationEventId` is then linked to the relevant business object (e.g., `OrderHeader`, `Shipment`, or `ContactList`).

## Attachments and PDF Generation

OFBiz excels at generating and attaching documents like **Invoices, Packing Slips, and Return Labels** directly within the mail services.

### The PDF Pattern (`xslfoAttachScreenLocation`)
This is the most common pattern for business documents. You provide a screen location, and the service renders it as a PDF attachment.

- **Service**: `sendMailFromScreen`
- **Parameter**: `xslfoAttachScreenLocation` (e.g., `component://accounting/widget/InvoiceScreens.xml#InvoicePDF`)
- **Expert Guidance**: For designing XSL-FO templates and report screens, refer to the [manage-pdf-export](../manage-pdf-export/SKILL.md) skill.
- **Attachment Name**: Pass `attachmentName` to set the filename (e.g., `Invoice_10000.pdf`).

### Multi-part Pattern (`bodyParts`)
For non-PDF attachments (e.g., CSVs, images) or multiple files, use `sendMailMultiPart`.
- **Parameter**: `bodyParts` (A `List` of `Map` objects containing `content`, `type`, and `filename`).

## Guardrails

- **Transactional Integrity**: **CRITICAL.** Commit business transactions *before* triggering emails. Use `runAsync` or After-Commit SECAs to prevent SMTP hangs from impacting DB locks.
- **Fault Tolerance**: SMTP failures must **NEVER** break the main user workflow. Log errors, update `CommunicationEvent` status, but ensure primary actions succeed.
- **Modern Security**: Externalize secrets (Env vars/Vaults). Avoid plain-text passwords in `general.properties`.
- **Absolute Continuity**: Always provide plain-text fallbacks (`altBodyScreenLocation`) and pass `webSiteId` for correct absolute URL resolution.
- **Pattern Compliance**: **ALWAYS** create and link `CommunicationEvent` records for auditability and customer history.
- **Environment Safety**: Verify `mail.notifications.redirectTo` is set in dev/test.

## Examples

### Example: Custom Email Screen
```xml
<screen name="MyEmailBody">
    <section>
        <actions>
            <set field="title" value="Special Notification"/>
        </actions>
        <widgets>
            <platform-specific>
                <html><html-template location="component://myplugin/template/email/MyEmailBody.ftl"/></html>
            </platform-specific>
        </widgets>
    </section>
</screen>
```

### Example: Sending via Template (Groovy)
```groovy
// Sending an order confirmation asynchronously
run service: 'sendMailFromTemplateSetting', with: [
    emailTemplateSettingId: 'PRDS_ODR_CONFIRM',
    sendTo: 'customer@example.com',
    bodyParameters: [orderId: '10000', webSiteId: 'MyStore']
]
```

### Example: Sending an Invoice PDF (Groovy)
```groovy
// Sending an invoice as a PDF attachment asynchronously
run service: 'sendMailFromScreen', with: [
    emailTemplateSettingId: 'INVOICE_NOTIFY',
    sendTo: 'customer@example.com',
    bodyParameters: [invoiceId: '10000'],
    xslfoAttachScreenLocation: 'component://accounting/widget/InvoiceScreens.xml#InvoicePDF',
    attachmentName: 'Invoice_10000.pdf'
]
```

### Example: Audit Trail Linking (Groovy)
```groovy
// Step 1: Send the email as usual
Map result = run service: 'sendMailFromScreen', with: [
    emailTemplateSettingId: 'PRDS_ODR_CONFIRM',
    sendTo: 'customer@example.com',
    bodyParameters: [orderId: '10000']
]

// Step 2: The service automatically creates a CommunicationEvent.
// Step 3: Link it to your business document for visibility.
String commEventId = result.communicationEventId;
run service: 'createCommunicationEventOrder', with: [
    communicationEventId: commEventId,
    orderId: '10000'
]
```

## Anti-Patterns

### 1. Synchronous Mailing in Transactions
**BAD**: If SMTP hangs, the order creation fails or hangs indefinitely.
```groovy
// Inside createOrder service
run service: 'createOrder', with: parameters
// ANTI-PATTERN: Sending synchronously inside the transaction
run service: 'sendOrderConfirmation', with: parameters 
```
**GOOD**: Decouple delivery from business logic.
```groovy
// Inside createOrder service
run service: 'createOrder', with: parameters
// Success: Return to caller. SECA or Post-Commit logic handles the email.
run service: 'sendOrderConfirmation', as: 'async', with: parameters
```

### 2. Relative URLs in Templates
**BAD**: Images won't load in Gmail/Outlook.
```html
<img src="/images/company-logo.png"> <!-- BROKEN in email clients -->
```
**GOOD**: Use absolute URLs (e.g., `https://example.com/images/company-logo.png`).

### 3. Hardcoding HTML in Java
**BAD**: Frustrating to maintain and style.
```java
String body = "<html><body><h1>Hello " + name + "</h1></body></html>"; // ANTI-PATTERN
```
**GOOD**: Use Screen Widgets and FreeMarker templates.
