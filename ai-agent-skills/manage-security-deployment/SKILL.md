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
name: manage-security-deployment
description: |
  Harden and secure an OFBiz deployment at the infrastructure limit. Manage global security properties, password policies, HTTP boundaries, and file upload protection. Use when:
  - Moving an OFBiz instance to a production environment.
  - Configuring global password strength and expiration policies.
  - Minimizing exposed webapp surface area.
  - Securing Tomcat server settings against host header injection, CSRF, or Webshells.
---

## Goal
Secure an OFBiz system for production deployment by hardening infrastructure configurations, minimizing surface area, enforcing strict system-wide password policies, and establishing network-level defenses.

## Triggers
**ALWAYS** read this skill when:
- **Going Live**: Transitioning an OFBiz instance from development to production.
- **Configuring Security Properties**: Modifying any value in `framework/security/config/security.properties`.
- **System Hardening**: Restricting endpoint exposure, protecting against webshells, modifying password requirements, or configuring CSP headers.
- **Remediating Demo Data**: Disabling default accounts or cleaning up development seed data.

> [!TIP]
> If you are looking on how to securely write Java/Groovy code, implement Service-Level authentication, or enforce business data scoping boundaries, see the [manage-security-advanced](../manage-security-advanced/SKILL.md) skill instead.

## Core Principles

### 1. Trust Model & Architectural Assumptions
Before configuring OFBiz security, developers and sysadmins must understand its foundational trust boundaries:
- **Administrative Trust Boundary**: OFBiz does **not** sandbox administrators from the host OS JVM. Any user with administrative rights is considered fully trusted.
- **Plugin & Extension Trust**: All custom extensions and distributed plugins (e.g., the `ecommerce` plugin) are assumed fully trusted. They execute with the same privileges as the core framework.
- **OS Privilege Boundary**: Because OFBiz lacks internal privilege separation, the application must be run by a dedicated, heavily restricted OS user account.
- **Log File Protection**: OFBiz does not automatically redact sensitive data. Deployers must secure log files independently to prevent information disclosure.

### 2. Remediating Demo Data
Never use credentials contained in demo data for production. If demo data was loaded (e.g., via `gradlew loadAll`), you **must** immediately change the passwords or disable the accounts for `admin`, `flexadmin`, `demoadmin`, `ltdadmin`, and `supplier`.

### 3. The Account Lockout Quirk (`login.disable.minutes`)
When disabling an account in the Party Manager UI, **leave the "disabled date" blank**. If you supply a disabled date, the account will automatically be re-enabled after 30 minutes! This auto-reactivation behavior is controlled by the `login.disable.minutes` property in `security.properties`.

### 4. Minimizing Webapp Surface Area
OFBiz ships with numerous webapps. You must dramatically reduce this surface area in production to save resources and deny attack vectors:
- **Global Level**: Comment out entire component groups in `framework/base/config/component-load.xml`.
- **Component Level**: If a component must load for its backend logic but its web UI should be hidden, add `app-bar-display="false"` to the `<webapp>` tag in the component's `ofbiz-component.xml`.

### 5. Hardening File Uploads (Webshell Defense)
Because OFBiz allows dynamic content upload, it is a target for Webshell injection. 
- Restrict upload directories (e.g., `themes/common-theme/webapp/images/products`) to trusted users.
- Make upload directories **non-executable** at the OS level.
- Actively manage `deniedFileExtensions` and `deniedWebShellTokens` inside `security.properties` to ensure modern exploits (e.g., `.phar`, `.phtml`, explicit execution commands) are instantly blocked globally.

### 6. Network Defense & Host Headers
If OFBiz is exposed beyond localhost, validate incoming request locations to prevent Host Header Injection caching attacks, and configure modern cookie rules.
- Maintain a strict `host-headers-allowed` blocklist in `security.properties`.
- Explicitly define `Content-Security-Policy` and `SameSiteCookieAttribute`.

---

## Procedure: Core Property Overrides

All critical infrastructure rules are managed in `framework/security/config/security.properties`.

### 1. Enforcing Strong Password Policies & Hashing
Change defaults to enforce enterprise-grade passwords and transition to slow, iterated hashes to defeat offline rainbow table attacks.

```properties
# Force strong passwords utilizing Lookahead Assertions
security.login.password.pattern.enable=true
security.login.password.pattern=^.*(?=.{8,})(?=.*[a-z])(?=.*[A-Z])(?=.*[\d]).*$

# ENFORCE MODERN HASHING (Prevents offline cracking)
# Type should be SHA-256, SHA-512, or PBKDF2
password.encrypt.hash.type=SHA-256
# Iterations must be high (100,000+) to slow down attackers
password.encrypt.hash.iterations=600000

# Extend lockout penalties for brute forcing
max.failed.logins=3
login.disable.minutes=30

# Enable automated expirations
user.auto.change.password.enable=true
user.change.password.days=90
```

### 2. Locking Down Tomcat CSRF, Host Headers, and HSTS
Explicitly configure the HTTP layer to prevent token leakage, domain spoofing, and man-in-the-middle attacks.

```properties
# Whitelist expected hostnames (prevents proxy spoofing / cache poisoning)
host-headers-allowed=localhost,127.0.0.1,store.yourcompany.com,internal.yourcompany.com

# STRICT CSRF DEFENSE
# Use Token-based defense for all mutating requests
csrf.defence.strategy=org.apache.ofbiz.webapp.control.ControlFilter$TokenDefenseStrategy
csrf.token.generator=org.apache.ofbiz.webapp.control.ControlFilter$DefaultTokenGenerator

# TRANSPORT SECURITY (HSTS)
security.hsts.enable=true
security.hsts.maxAge=31536000
security.hsts.includeSubDomains=true

# SECURITY HEADERS (Content protection)
security.x-frame-options=SAMEORIGIN
security.x-content-type-options=nosniff
security.x-xss-protection=1; mode=block

# Explicit modern cookie strategies
SameSiteCookieAttribute=Strict
useContent-Security-Policy=true
Content-Security-Policy=Content-Security-Policy
PolicyDirectives=default-src 'self'
```

### 3. Disabling Default Features
Disable unnecessary diagnostic features that could leak system context to attackers.

```properties
# Disable cross-webapp token leaking if you do not use SSO
security.login.externalLoginKey.enabled=false

# Prevent accidental or malicious admin impersonation
security.disable.impersonation=true
```

---

## Examples

### Safely Shutting Down Webapps (`component-load.xml`)
If you only need the eCommerce app, do not load the massive manufacturing and accounting dashboards into the servlet environment.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- framework/base/config/component-load.xml -->
<component-loader xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ...>
    <!-- Core is required -->
    <load-component component-location="framework"/>
    
    <!-- Disable entire backend branches in production if not needed -->
    <!-- <load-component component-location="applications"/> -->
    <!-- <load-component component-location="specialpurpose"/> -->
</component-loader>
```

### Hiding Webapps from the UI (`ofbiz-component.xml`)
If you need the component logically but want to hide it from the App Bar UI.

```xml
<!-- Example: Accounting must be loaded for eCommerce, but end-users shouldn't see an "Accounting" tab -->
<webapp name="accounting"
    title="Accounting"
    server="default-server"
    location="webapp/accounting"
    app-bar-display="false" 
    mount-point="/accounting"/>
```

---

## Anti-Patterns

- **❌ Deploying Demo Users to Production**: Releasing an instance where `admin:ofbiz` can log in to the internet-facing server. If demo data *must* be utilized, every single default account's password must be scrambled or universally deleted.
- **❌ Setting a Disabled Date for Immediate Lockout**: Filling out the "disabled date" string in the Party Manager expecting it to permanently lock the user out. The `login.disable.minutes` auto-recovery daemon will immediately see a timestamp, do the math, and reactivate the rogue account shortly after.
- **❌ Permissive Host Headers**: Leaving `host-headers-allowed` set only to `localhost` when the app is placed behind a proxy/load-balancer. The server will begin rejecting legitimate proxied traffic, or if left entirely undefined, accept malicious host headers that poison the cache.
- **❌ Leaving Impersonation Enabled**: Allowing `security.disable.impersonation=false` on a live environment where a compromised QA or limited Admin account can suddenly `impersonate` the SuperAdmin without needing their password.
- **❌ Application Level Logic Flaws**: Assuming configuring `security.properties` perfectly protects your system. A single missing `auth="true"` in a controller or unprotected mutating service exposes your database regardless of Tomcat hardening. See [manage-security-advanced](../manage-security-advanced/SKILL.md).
