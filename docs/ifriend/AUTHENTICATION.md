# Autenticação — OAuth 2.0 Client Credentials

## Fluxo

```http
POST /api/v1/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=tc_...&client_secret=...
```

Também é aceito `Authorization: Basic base64(client_id:client_secret)`.

### Resposta de sucesso

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "organization.read wallet.read pix.read pix.transfer"
}
```

### Uso

```http
GET /api/v1/accounts/{accountId}
Authorization: Bearer <access_token>
```

## Access token

| Campo | Valor |
|-------|--------|
| Formato | JWT assinado (HS256) |
| Validade | ~15 minutos (`expires_in`) |
| Refresh | Não há — obtenha um novo token com client credentials |
| Revogação | Client `REVOKED` invalida tokens **na próxima request** (checagem no servidor) |

Não há refresh token OAuth. Reautentique com `client_id` + `client_secret`.

## Scopes (PermissionCodes)

O token usa scopes com **ponto** (iguais às permissões internas). Alias documental `wallet:read` ≡ `wallet.read`.

| Scope | Descrição | Endpoints típicos |
|-------|-----------|-------------------|
| `organization.read` | Ler organização | `GET /organizations/{id}` |
| `wallet.read` | Ler conta/saldo | `GET /accounts/{id}`, `/wallet`, `/ledger-balance` |
| `transactions.read` | Extrato | `GET /transactions`, `/wallets/{id}/transactions` |
| `pix.read` | Consultar PIX | `GET /pix/transfers`, `/pix/keys`, `/pix/transactions/{id}` |
| `pix.create` | Criar chave/QR cobrar | `POST /pix/keys`, `POST /pix/qr-codes` |
| `pix.transfer` | Enviar PIX / lookup / pay | `POST /pix/transfers`, `/pix/qr-codes/pay`, `GET /pix/keys/lookup` |
| `transactions.create` | Depósitos (se habilitado) | `POST /deposits` |
| `wallet.transfer` | Saques/transferências internas (se habilitado) | `POST /withdraws` |
| `beneficiaries.*` | Favorecidos | `/beneficiaries` |

O client só acessa Accounts na **allowlist** vinculada pela Theron. O `organization_id` vem do token — não confie em IDs enviados pelo cliente para autorização.

## Tenant

- Tenant = Organization do client.
- Operações financeiras exigem `accountId` permitido.
- Conta fora da allowlist → `403 FORBIDDEN`.
