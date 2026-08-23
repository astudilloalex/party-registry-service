# Geographic Reference Stub Fixtures

These test-only descriptors model the approved country lookup at
`GET /api/v1/countries/by-alpha2/{alpha2Code}`. The normal response envelope and activity values
come from Postman collection `15834347-d3591c82-bd52-46a9-973d-a7a102d4b9b3`; Party Registry's
three trusted request headers come from the newer provider-owner decision.

Each descriptor can define an HTTP status, content type, response headers, a JSON object or raw
string body, a fixed delay, or a WireMock fault. The process identifiers are synthetic. Delayed
fixtures are intended for deterministic timeout and client-cancellation tests; they do not set the
production timeout policy.
