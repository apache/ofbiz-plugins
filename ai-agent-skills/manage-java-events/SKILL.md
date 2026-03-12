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
name: manage-java-events
description: |-
  - Implementing OFBiz Events (web boundaries) in Java.
  - Handling HTTP requests, responses, and MVC flow.
  - Requires manage-java-patterns for shared standards.
---

# Skill: manage-java-events
## Goal
Implement robust web request handling and control flow using Java Events in OFBiz. This is particularly suited for complex web requests requiring integration with Java libraries or complex pre-processing before calling services.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying Java methods intended to be called as events by `controller.xml`.
- Interacting directly with `HttpServletRequest` or `HttpServletResponse` in Java.

> [!TIP]
> **See [manage-java-patterns](../manage-java-patterns/SKILL.md)** for shared Java standards (logging, localization, and exception handling).

---

## Rules & Procedures

### 1. Class Structure & Method Signature
- **Static Methods**: A Java Event must be a `public static` method.
- **Signature**: `public static String myEvent(HttpServletRequest request, HttpServletResponse response)`.
- **Return Type**: Always returns a `String` mapping to `<response>` in `controller.xml` (e.g., `"success"`, `"error"`, `"none"`).

### 2. Context & Input Handling
- **Delegator/Dispatcher**: Retrieve from request attributes.
  ```java
  Delegator delegator = (Delegator) request.getAttribute("delegator");
  LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
  ```
- **Security Context**: Fetch `userLogin`, `locale`, and `timeZone` from request attributes/session.
  ```java
  GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
  if (userLogin == null) {
      userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
  }
  Locale locale = UtilHttp.getLocale(request);
  TimeZone timeZone = UtilHttp.getTimeZone(request);
  ```
  - **Rule**: Derive identity from the `userLogin` object, never from parameters. 
- **Parameters**: Use `request.getParameter("id")` for single values or `UtilHttp.getParameterMap(request)` for bulk.
- **Rule**: Consult **[manage-java-patterns](../manage-java-patterns/SKILL.md)** for mandatory trimming and validation before passing data to services.

### 3. Response & Message Management
- **Success Pattern**: Set `_EVENT_MESSAGE_` and return `"success"`.
- **Error Pattern**: Set `_ERROR_MESSAGE_` or `_ERROR_MESSAGE_LIST_` and return `"error"`.
- **AJAX/JSON Boundary**: 
    - Set `response.setContentType("application/json")` and HTTP status.
    - Write JSON directly to `response.getWriter()`.
    - **MUST** return `"none"` to stop further view rendering.
- **Redirection**: For `request-redirect`, store messages in the session (`request.getSession().setAttribute("_ERROR_MESSAGE_", ...)`) to ensure they survive the redirect.

### 4. Uploads & Streaming
- **Multipart Data**: Standard `request.getParameter()` returns `null` for file uploads.
    - **Rule**: Use OFBiz upload utilities or request wrappers.
    - **Rule**: Never read full files into memory; process as a stream.
- **Direct Streaming**: For file downloads, write directly to the response output stream and return `"none"`.

---

## Guardrails
- **Standard Response Strings**: Stick to `"success"`, `"error"`, or `"none"`.
- **Return "none"**: Required whenever the response is already committed (JSON, redirects, streaming).
- **Localization**: ALL user-facing messages MUST be localized. Refer to **[manage-java-patterns](../manage-java-patterns/SKILL.md)**.
- **Validation & Exceptions**: Refer to **[manage-java-patterns](../manage-java-patterns/SKILL.md)** for standard `UtilValidate` usage and exception strategies.
- **CSRF & Identity**: Ensure CSRF protection for state-changing requests. Derive identity from `userLogin` to prevent spoofing.

## Bad Practices & Anti-patterns

### Anti-pattern: The "Fat Event" (Deep Business Logic)
**Pitfall**: Writing business logic (loops, calculations, direct DB commits) inside a Java Event. This makes the logic non-reusable and bypasses the service engine's transaction and ECA management.
- **❌ Bad (Logic in Event)**:
  ```java
  for (String id : ids) {
      GenericValue gv = delegator.findOne("MyEntity", UtilMisc.toMap("id", id), false);
      gv.set("statusId", "DONE");
      gv.store();
  }
  return "success";
  ```
- **✅ Good (Delegate to Service)**:
  ```java
  // Event only prepares input and calls service
  Map<String, Object> result = dispatcher.runSync("updateMyStatus", UtilMisc.toMap("ids", ids, "userLogin", userLogin));
  return ServiceUtil.isError(result) ? "error" : "success";
  ```

### Anti-pattern: Identity Spoofing (Trusting Parameters)
**Pitfall**: Using an ID passed in the request parameter (e.g., `partyId`, `userId`) to determine the identity or permissions of the caller. Parameters can be easily spoofed.
- **❌ Bad**:
  ```java
  String partyId = request.getParameter("partyId");
  // Security Risk: User can pass any partyId in the URL!
  GenericValue profile = delegator.findOne("Party", UtilMisc.toMap("partyId", partyId), false);
  ```
- **✅ Good**: 
  ```java
  // Use the verified userLogin object from the session/request
  String partyId = userLogin.getString("partyId"); 
  ```

### Anti-pattern: Standard Response Bypass
**Pitfall**: Returning non-standard strings or hardcoded message literals instead of using the controller flow and localized labels.
- **❌ Bad**:
  ```java
  if (failure) return "Something went wrong"; // Breaks controller mapping
  ```
- **✅ Good**: 
  ```java
  if (failure) {
      request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(...));
      return "error"; // Correctly enters the error flow defined in controller.xml
  }
  ```

### Other Bad Practices
- **Assuming Form EncTypes**: Using `request.getParameter()` with `multipart/form-data` (returns `null`).
- **Memory-Heavy Uploads**: Reading a full `InputStream` into memory; always use streaming.
- **Heavy Sessions**: Storing large, non-essential objects in `HttpSession`.
- **Assuming Auto-Transactions**: Events do NOT handle transactions; use Services for atomicity.

---

## Common Code Patterns (Examples)

### Standard Java Event (Delegating to Service)
See how this event extracts context, validates, and hands off to the dispatcher:
```java
public static String processOrderEvent(HttpServletRequest request, HttpServletResponse response) {
    LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
    Locale locale = UtilHttp.getLocale(request);
    GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
    
    String productId = request.getParameter("productId");
    if (UtilValidate.isEmpty(productId)) {
        request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage("UiLabels", "MissingProduct", locale));
        return "error";
    }

    try {
        Map<String, Object> serviceCtx = UtilMisc.toMap("productId", productId, "userLogin", userLogin, "locale", locale);
        Map<String, Object> result = dispatcher.runSync("myOrderService", serviceCtx);
        if (ServiceUtil.isError(result)) {
            request.setAttribute("_ERROR_MESSAGE_", ServiceUtil.getErrorMessage(result));
            return "error";
        }
    } catch (Exception e) {
        Debug.logError(e, "Event Failure", MODULE);
        request.setAttribute("_ERROR_MESSAGE_", "System Error");
        return "error";
    }
    
    request.setAttribute("_EVENT_MESSAGE_", "Success");
    return "success";
}
```

### Redirect with Session-Based Messages
When using `request-redirect`, messages must be stored in the session to survive the redirect:
```java
public static String redirectWithMsg(HttpServletRequest request, HttpServletResponse response) {
    Locale locale = UtilHttp.getLocale(request);
    String msg = UtilProperties.getMessage("MyLabels", "FormSubmitted", locale);
    // Store in session for visibility after redirect
    request.getSession().setAttribute("_EVENT_MESSAGE_", msg);
    return "request-redirect";
}
```

### Direct JSON Response for AJAX
```java
public static String returnJsonResponse(HttpServletRequest request, HttpServletResponse response) {
    Map<String, Object> data = UtilMisc.toMap("status", "ok", "timestamp", UtilDateTime.nowTimestamp());
    try {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(HttpServletResponse.SC_OK);
        
        // Use framework JSON utilities to serialize/write
        // E.g., JSON.from(data).write(response.getWriter());
        String json = // ... framework serialization ...
        response.getWriter().write(json);
        response.getWriter().flush();
    } catch (Exception e) {
        Debug.logError(e, "Error writing JSON response", MODULE);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return "none"; // Stop further view rendering
}
```
