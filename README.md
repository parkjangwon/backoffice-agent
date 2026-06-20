# backoffice-agent

Natural-language, read-only backoffice query agent for operational databases.

`backoffice-agent` lets an internal operator ask business questions without knowing table names, column names, SQL dialect details, or storage units. It scans database metadata into a local catalog, asks an AI model to draft a SQL query from a constrained catalog context, validates the SQL, enforces access scope, executes it through a read-only connection, and formats the result for people.

The project is designed as a sidecar service for existing admin consoles. It does not replace your authentication system; your application sends a verified actor id, access role, and explicit scope values.

## Goals

Typical supported questions:

- `Which customers had the highest storage usage last month?`
- `Show inactive accounts for the acme scope.`
- `List recent login events for customer acme.`
- `Which enterprise-plan workspaces had failed billing events this week?`
- `Show the top 10 API users by request count.`

Out of scope:

- schema dumping and system exploration
- password, token, credential, or secret lookup
- cross-scope data access
- data mutation
- DDL, stored procedure calls, or multiple SQL statements

## Safety Model

- Only a single `SELECT` statement is allowed.
- `INSERT`, `UPDATE`, `DELETE`, DDL, `CALL`, multi-statement SQL, lock clauses, file functions, and system functions are blocked.
- The target database connection is read-only.
- Request identity is explicit: `actorId`, `role`, and `scopeValues`.
- `GLOBAL` can query the scanned catalog.
- `SCOPED` must include explicit `scopeValues`; the server does not infer scope from email domains.
- Prompt injection, system data requests, and scope-bypass attempts are rejected before SQL generation.
- AI-generated SQL is validated with an AST parser and grounded against the scanned catalog before execution.
- Raw prompts, raw SQL, and result rows are not written to audit logs by default.

## Architecture

1. Catalog scan
   - Scans database/table/column metadata.
   - Captures primary keys, index metadata, comments, estimated row counts, semantic hints, and scope relationship hints.
   - Stores the snapshot locally in `data/catalog-snapshot.json`.

2. Catalog index
   - Builds an in-memory search index from the snapshot.
   - Selects only relevant tables and inferred relationships for each prompt.

3. Query execution
   - Accepts `/api/query`, `/api/query/preview`, and `/api/query/export`.
   - Validates IP allowlist, API key, and optional HMAC request signature.
   - Resolves `GLOBAL` or `SCOPED` access from explicit request fields.
   - Generates SQL from a provider-neutral prompt.
   - Validates read-only behavior, catalog grounding, access rules, scope enforcement, and risk policy.
   - Executes with query timeout and max-row controls.

4. Presentation and audit
   - Converts known units such as bytes into human-readable values.
   - Returns JSON, TEXT, CSV, or SSE STREAM.
   - Writes JSONL audit events with hashes, row counts, latency, cache status, and catalog version.

## Database Support

The open-source version includes dialect metadata for:

- MySQL
- MariaDB
- PostgreSQL
- SQLite
- Oracle

The scanner uses native metadata paths when available and falls back to JDBC metadata for portable discovery. Dialect metadata covers identifier quoting, row limiting, and default system schema exclusions.

## AI Providers

The service keeps SQL generation behind Spring AI's `ChatClient` abstraction and accepts provider-neutral configuration.

Supported provider values:

- `OLLAMA`
- `OPENAI`
- `ANTHROPIC`
- `AZURE_OPENAI`
- `GEMINI`
- `GOOGLE_VERTEX_AI`
- `AMAZON_BEDROCK`
- `MISTRAL`
- `DEEPSEEK`
- `Z_AI`
- `MINIMAX`
- `MIMO`
- `CURSOR`
- `OPENAI_COMPATIBLE`

Use `OPENAI_COMPATIBLE` for OpenRouter, vLLM, LM Studio, LocalAI, custom gateways, and providers that expose an OpenAI-compatible API surface.

## Configuration

Copy the example configuration and edit it for your environment:

```bash
cp config/application.example.yml config/application.yml
./gradlew bootRun
```

Safe defaults remain in `src/main/resources/application.yml`; environment-specific settings belong in `config/application.yml`.

Minimal request example:

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

## API Examples

Catalog status:

```bash
curl http://localhost:8080/api/catalog/status \
  -H "X-API-Key: change-me"
```

JSON preview:

```bash
curl -X POST http://localhost:8080/api/query/preview \
  -H "X-API-Key: change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "actorId": "operator-123",
    "role": "SCOPED",
    "scopeValues": ["acme"],
    "prompt": "Show inactive accounts for acme",
    "limit": 20
  }'
```

Text response:

```bash
curl -X POST http://localhost:8080/api/query \
  -H "X-API-Key: change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "actorId": "operator-123",
    "role": "SCOPED",
    "scopeValues": ["acme"],
    "prompt": "Which accounts used the most storage this month?",
    "responseFormat": "TEXT",
    "limit": 5
  }'
```

CSV export:

```bash
curl -X POST "http://localhost:8080/api/query/export?format=csv" \
  -H "X-API-Key: change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "actorId": "operator-123",
    "role": "SCOPED",
    "scopeValues": ["acme"],
    "prompt": "Export recent login events for acme",
    "limit": 100
  }'
```

## Client Integration

Your existing application should:

- authenticate the user itself
- map the user to `actorId`
- choose `GLOBAL` or `SCOPED`
- provide explicit `scopeValues` for scoped access
- sign the request body server-side when request signing is enabled
- keep API keys and signing secrets away from browsers

See [docs/client-integration.md](docs/client-integration.md).

## Audit

Audit events are stored in `data/audit-events.jsonl` and exposed through `/api/audit`.

Recorded fields include:

- `requestId`
- `actorId`, `role`, `clientIp`
- `promptHash`, `sqlHash`
- `rowCount`
- `latencyMs`
- `stageLatencyMs`
- `cacheHit`
- `catalogVersion`
- `outcome`

## Tests

```bash
./gradlew test
```

The test suite covers read-only SQL validation, scope enforcement, prompt injection defense, catalog grounding, SQL identifier normalization, result formatting, CSV export safety, audit scrubbing, and open-source project contracts.
