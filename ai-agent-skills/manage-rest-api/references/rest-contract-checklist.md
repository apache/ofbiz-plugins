# REST Contract Checklist

Use this checklist before changing existing OFBiz REST endpoints or service exports.

## Discover
- Find the `.rest.xml` operation or direct service export.
- Find the service definition and implementation.
- Find active consumers, including PWAs, tests, scripts, and API clients.
- Confirm whether the endpoint is public, authenticated, or internal.

## Inputs
- Preserve path, query, and body parameter names unless consumers are updated together.
- Prefer precise service attribute types so the service engine handles validation and coercion.
- Avoid manual parsing of already-typed service parameters.
- Keep required/optional flags aligned with real REST usage.
- Confirm service `export` settings are intentional for REST access.

## Outputs
- Search consumers before removing any response field.
- Keep fields that drive visible UI, filters, badges, links, or client-side update/delete calls.
- Remove unused metadata when contract consumers are updated and tests cover the change.
- Avoid returning native internal fields that are not part of the REST contract.
- Do not stringify timestamps, quantities, or money unless the endpoint is explicitly presentation/export oriented.

## Paths and Keys
- Prefer stable resource paths for common single-key resources.
- For multi-column native keys, prefer request-body fields for create/update/remove operations when that enables native OFBiz service calls.
- Use composite REST IDs only when the contract truly needs one path/id value, and keep encoding/decoding at the API edge.
- Do not modify OFBiz entity keys just to make REST paths shorter.

## Compatibility
- If a frontend/PWA uses a legacy field name, either preserve it or update the frontend/client mapper in the same change.
- When simplifying payloads, update type definitions, mappers, and contract tests together.
- Document intentional contract changes in the commit/PR summary.

## Verification
- Run project-standard backend compile/static analysis/tests.
- Run consumer contract tests when clients are in scope.
- If only targeted verification was possible, state exactly what was and was not verified.
