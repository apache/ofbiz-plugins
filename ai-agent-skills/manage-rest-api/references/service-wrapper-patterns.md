# Service Wrapper Patterns

Use these patterns when deciding whether a REST-facing service should exist.

## Prefer Direct Native Service Exposure
Use an existing OFBiz service directly when:
- The service already performs the business operation.
- The service attributes match the REST inputs closely.
- The output is acceptable to the consumer.
- The service has correct validation, auth, and export settings.

Do not create a wrapper only to copy fields from REST input to the same native service input.

## Thin Write Adapter
Use a thin wrapper when REST uses convenience parameters that differ from native keys and the client cannot reasonably send native key fields directly.

The wrapper should:
- Validate only what the service definition cannot express.
- Resolve convenience identifiers into native service fields.
- Call the native OFBiz service.
- Check `ServiceUtil.isError(result)` and return the native error when appropriate.
- Return only the agreed REST response fields.

Avoid adding duplicate business validation already enforced by the native service.

## Read Aggregate Wrapper
Use a read wrapper when one API call intentionally serves a screen or workflow by combining data from several entities/services.

The wrapper should:
- Batch-load related records instead of querying per row.
- Use `EntityQuery`, `filterByDate`, `queryCount`, and paging helpers.
- Keep response fields screen/use-case oriented, not raw database dumps.
- Avoid heavy summaries that are not displayed or otherwise consumed.

## Lookup/Search Endpoint
Use a lookup endpoint when multiple clients need the same typeahead or option list.

The service should:
- Accept generic filters where practical.
- Use case-insensitive search helpers or OFBiz-native search utilities only when they already exist in the target codebase.
- Page or cap result sizes.
- Return stable IDs plus display names/labels.
- Avoid embedding one component's business assumptions into a generic lookup.

## Utility Promotion
Promote helper code only when it is repeated mechanical logic.

Good candidates:
- Load entities by ID list and return a map keyed by ID.
- Search an entity across configured fields and return IDs.
- Count/group rows by a selected field.
- Build stable composite identifiers from key parts.

When a candidate depends on helper methods that are only present in a local framework checkout, do not reference those helpers in upstream-targeted work unless the same change also introduces them upstream.

Poor candidates:
- Domain relationship queries.
- Screen-specific payload assembly.
- Business validation rules.
- Helpers used by only one endpoint with no clear reuse.

## Deletion Check
Before deleting a wrapper:
- Confirm the native service is exported and authorized correctly.
- Confirm REST clients can send the native key fields.
- Confirm response handling still works, or update client mappers/tests.
- Keep a wrapper if it protects clients from unstable internal service details.
