# Erros

## API (padrão Theron)

A maioria dos endpoints retorna:

```json
{
  "timestamp": "2026-09-08T22:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "message": "Insufficient scope: pix.transfer",
  "path": "/api/v1/pix/transfers",
  "traceId": "..."
}
```

Códigos comuns: `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `INVALID_REQUEST`, `CONFLICT`, `RATE_LIMITED`, `ASAAS_ERROR`.

| Situação | HTTP | code típico |
|----------|------|-------------|
| Token ausente/inválido/expirado | 401 | `UNAUTHORIZED` |
| Client revogado (checagem a cada request) | 401 | `UNAUTHORIZED` |
| Scope insuficiente / Account diferente da vinculada (1:1) | 403 | `FORBIDDEN` |
| Recurso inexistente | 404 | `NOT_FOUND` |
| Sem Idempotency-Key em PIX | 400 | `INVALID_REQUEST` |
| Mesma Idempotency-Key com payload diferente | 409 | `CONFLICT` |
| Content-Type inválido em `/oauth/token` (ex.: JSON) | 415 | `INVALID_REQUEST` |
| Validação Asaas (ex.: campo inválido) | 400 / 422 | `ASAAS_ERROR` — `message` costuma começar com `Asaas validation error:` |
| Erro genérico do provedor / sandbox sem detalhe | 502 | `ASAAS_ERROR` — `Payment provider error. Please try again later.` |
| Simular antecipação no Sandbox Asaas | 502 (ou falha de provedor) | `ASAAS_ERROR` — recurso **indisponível no sandbox**; use `POST /anticipations` |

## Exceção: `POST /oauth/token` (RFC 6749)

Este endpoint **não** usa `ApiErrorResponse`. Exemplo:

```json
{
  "error": "invalid_client",
  "error_description": "Invalid client credentials"
}
```

| error | HTTP | Quando |
|-------|------|--------|
| `invalid_client` | 401 | client_id/secret inválidos ou client revogado |
| `invalid_request` | 400 | parâmetros ausentes |
| `unsupported_grant_type` | 400 | grant_type ≠ `client_credentials` |

Libs OAuth padrão esperam este formato no token endpoint.

Use sempre:

```http
Content-Type: application/x-www-form-urlencoded
```
