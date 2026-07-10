---
name: manage-rest-api
description: Design, expose, refactor, and verify OFBiz REST APIs using the framework REST support, service engine contracts, native OFBiz services, and contract-safe wrapper patterns. Use when working with `.rest.xml` files, REST-exposed services, PWA/API consumers, service export settings, or backend REST response contracts.
---

# Skill: Manage REST APIs

## Goal
Build OFBiz REST APIs that are thin, service-native, contract-safe, and easy to upstream into OFBiz.

Use OFBiz services, service definitions, entity utilities, and framework REST conventions first. Add REST wrapper services only when they adapt a transport contract, aggregate read data, or preserve compatibility for an existing consumer.

For low-level REST XML syntax, direct service export mechanics, SOAP integration, or controller JSON response patterns, also read the `manage-api-integration` skill.

## Core Workflow
1. **Map the contract first**
   - Find the REST mapping, service definition, service implementation, and active consumers.
   - Search frontend/API clients before removing request parameters or response fields.
   - Identify whether the endpoint is a generic service API, a read aggregate, or a compatibility adapter.

2. **Prefer native OFBiz services**
   - Reuse existing services before creating wrapper services.
   - Expose native services directly when their inputs and outputs are already suitable for the REST contract.
   - Keep write wrappers thin: resolve REST convenience parameters, delegate to native services, return the agreed response shape.

3. **Let the service engine validate and coerce**
   - Define service attributes with precise native types such as `Timestamp`, `BigDecimal`, `Double`, `Long`, and `Integer`.
   - Avoid manual parsing in Groovy/Java when service XML already declares the correct type.
   - Use OFBiz conversion utilities only when conversion is genuinely needed inside service logic.

4. **Use native query and paging utilities**
   - Prefer `EntityQuery`, `filterByDate`, `queryCount`, and helper methods that already exist in the target codebase over custom query loops.
   - Avoid N+1 queries by batch-loading related data and mapping in memory.
   - Use database-level filtering/counting where possible instead of loading full rows and filtering manually.

5. **Promote shared mechanics carefully**
   - Reuse existing OFBiz/framework/component utilities before adding local helpers.
   - Promote repeated mechanical patterns to the narrowest appropriate shared utility.
   - Do not promote domain-specific behavior into generic utilities just because the method is small.

6. **Verify with project-standard checks**
   - Run the repository's standard backend compilation, static analysis, and relevant tests.
   - Use the runtime/JDK version required by the project build configuration.
   - If a full check is blocked by environment or unrelated project issues, run the narrowest equivalent verification and report that it was targeted.

## Helper Availability
- Before using a helper for paging, partial-list slicing, case-insensitive parameter lookup, composite REST ID handling, or REST error shaping, confirm that the helper already exists in the target codebase or is being introduced in the same change.
- If a helper is not available yet, prefer native `EntityQuery`, service engine typing/validation, and small local endpoint logic over adding a dependency on a missing framework API.

## Wrapper Decision Rules
Create or keep a wrapper when it:
- Aggregates multiple native service/entity reads for a REST/PWA screen.
- Preserves a published REST contract that differs from native OFBiz service shape.
- Resolves REST-friendly identifiers into native key fields when the client cannot send native keys directly.
- Adds read-side display metadata that avoids excessive client round trips.

Remove or avoid a wrapper when it:
- Only duplicates a native CRUD service call.
- Manually validates what the service definition or native service already validates.
- Re-parses typed service parameters.
- Adds local formatting/conversion helpers already available in OFBiz.

Read [service-wrapper-patterns.md](references/service-wrapper-patterns.md) before adding or removing wrapper services.

## Contract Safety
Before changing a REST response:
- Search active consumers for every field you plan to remove.
- Update frontend/client mappers and tests in the same change when intentionally changing a contract.
- Keep compatibility fields only when still used or intentionally supported.
- Prefer stable semantic field names over implementation-detail names.

Read [rest-contract-checklist.md](references/rest-contract-checklist.md) before changing existing API inputs, paths, response fields, or service export settings.

## REST Mapping Guidance
- Keep REST XML mappings declarative and close to service names.
- Prefer path parameters for stable resource identity and query parameters for filtering/paging.
- Use request bodies for create/update/remove payloads, especially when native OFBiz services require multiple key fields.
- For multi-column keys, prefer native key fields in the body when that enables direct native service use. Use a stable composite REST identifier only when the REST contract truly needs one path/id value. Do not distort the underlying entity model.
- Ensure services exposed through REST are intentionally exported and authorized.

## Utility Promotion Test
Before adding a helper, answer:
- Is this already available in OFBiz?
- Is this repeated mechanical logic, or domain behavior?
- Is the helper useful outside this one endpoint?
- What is the narrowest appropriate owner: existing framework utility, component utility, or local script?

Good utility candidates are mechanical patterns such as batch lookup, case-insensitive ID search, pagination wrappers, or composite ID creation, but only when those helpers already exist in the target codebase or are being introduced in the same change. Poor candidates are domain queries such as "routing task associations" or "BOM component rows"; keep those near the service that owns the domain meaning.
