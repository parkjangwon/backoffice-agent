# backoffice-agent Client Integration Guide

Use this guide when adding natural-language backoffice queries to an existing admin console.

The browser should never call `backoffice-agent` directly with secrets. Route requests through your existing application server, where the user is already authenticated and authorized.

## Environment

```text
BACKOFFICE_AGENT_BASE_URL=http://localhost:8080
BACKOFFICE_AGENT_API_KEY=change-me
BACKOFFICE_AGENT_SIGNING_SECRET=change-me-long-random-secret
```

Server-to-server requests should include:

```http
X-API-Key: {BACKOFFICE_AGENT_API_KEY}
X-Agent-Timestamp: {epoch seconds}
X-Agent-Signature: sha256={hmac hex}
Content-Type: application/json
```

The signature payload is:

```text
timestamp + "\n" + method + "\n" + pathAndQuery + "\n" + "sha256:" + sha256Hex(body)
```

## Access Fields

`backoffice-agent` uses explicit access claims:

- `actorId`: stable id of the authenticated operator, service account, or job
- `role`: `GLOBAL` or `SCOPED`
- `scopeValues`: required for `SCOPED`; empty for `GLOBAL`

The service does not derive scope from email addresses. Your application owns the mapping from logged-in identity to scope values.

Request:

```json
{
  "actorId": "operator-123",
  "role": "SCOPED",
  "scopeValues": ["acme"],
  "prompt": "Show inactive accounts for acme",
  "responseFormat": "JSON",
  "limit": 20
}
```

## JSON Preview

```http
POST /api/query/preview
```

Response:

```json
{
  "success": true,
  "data": {
    "rowCount": 1,
    "columns": ["account_id", "status"],
    "rows": [
      {
        "account_id": "acct_001",
        "status": "INACTIVE"
      }
    ],
    "message": "",
    "guidance": ""
  },
  "message": null
}
```

## Text Response

```http
POST /api/query
```

Use `responseFormat: "TEXT"` for short natural-language answers.

## Stream Response

```http
POST /api/query
```

Use `responseFormat: "STREAM"` for chat-style UX. The response is `text/event-stream`.

```text
event:delta
data:{"text":"R"}

event:done
data:[DONE]
```

Because the request is POST, browser clients should use `fetch` streaming rather than plain `EventSource`.

## CSV Export

```http
POST /api/query/export?format=csv
```

CSV output applies spreadsheet formula-injection defense.

## Recommended Proxy Flow

1. Browser posts prompt to your application server.
2. Your application resolves `actorId`, `role`, and `scopeValues` from its own session and authorization data.
3. Your application signs the exact JSON body.
4. Your application calls `backoffice-agent`.
5. Your application streams or returns the result to the browser.

Example server-side payload:

```json
{
  "actorId": "{authenticated user id}",
  "role": "SCOPED",
  "scopeValues": ["{authorized scope}"],
  "prompt": "{operator prompt}",
  "responseFormat": "JSON",
  "limit": 100
}
```
