# Autenticação — OAuth 2.0 e Theron API Key

Há **dois** modos machine-to-machine equivalentes (mesmo principal `CLIENT`, mesmos scopes, mesma Account 1:1):

1. **OAuth Client Credentials** → JWT curto (`/oauth/token`)
2. **Theron API Key** (`tk_sandbox_…` / `tk_live_…`) → Bearer direto, sem JWT

A API Key **é** o `OauthClient` (evolução); não há tabela paralela. Credenciais Asaas **nunca** são expostas.

## Opção A — OAuth 2.0 Client Credentials

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
  "scope": "organization.read wallet.read charges.create charges.read anticipations.create anticipations.read"
}
```

### Uso

```http
GET /api/v1/accounts/{accountId}
Authorization: Bearer <access_token>
```

### Access token

| Campo | Valor |
|-------|--------|
| Formato | JWT assinado (HS256) |
| Validade | ~15 minutos (`expires_in`) |
| Refresh | Não há — obtenha um novo token com client credentials |
| Revogação | Client `REVOKED` invalida tokens **na próxima request** (checagem no servidor) |

Não há refresh token OAuth. Reautentique com `client_id` + `client_secret`.

## Opção B — Theron API Key (Bearer direto)

```http
GET /api/v1/charges
Authorization: Bearer tk_sandbox_<secret>
```

| Campo | Valor |
|-------|--------|
| Formato | `tk_sandbox_…` (homolog) ou `tk_live_…` (produção) |
| Emissão | OWNER via `POST /developer/api-keys` (produto) ou admin via OAuth client |
| Secret | Aparece **uma vez** no create/rotate; listagens só mostram `prefix` |
| Revogação | `DELETE /developer/api-keys/{id}` ou revoke admin → 401 na próxima request |
| Rotação | `POST /developer/api-keys/{id}/rotate` invalida a chave anterior |

Não misture API Key com JWT: se o Bearer começa com `tk_`, o servidor autentica por hash SHA-256 no `oauth_client` (nunca parseia como JWT).

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
| `charges.read` | Ler cobranças | `GET /charges`, `GET /charges/{id}` |
| `charges.create` | Criar cobranças | `POST /charges` |
| `charges.cancel` | Cancelar cobranças | `POST /charges/{id}/cancel` |
| `anticipations.read` | Ler antecipações | `GET /anticipations` |
| `anticipations.create` | Simular/criar antecipação | `POST /anticipations/simulate`, `POST /anticipations` |
| `onboarding.read` | Ler status do onboarding Asaas | `GET /asaas/onboarding/b2b`, `GET /asaas/subaccount/status/b2b` |
| `onboarding.submit` | Provisionar subconta Asaas (one-shot) | `POST /asaas/onboarding/b2b` |

O client acessa **exatamente uma Account** (vínculo 1:1 com as credenciais). O `organization_id` e o `account_id` vêm das credenciais — **nunca** envie `accountId` no body de `/charges`, `/anticipations` ou `/asaas/onboarding/b2b`.

## Tenant

- Tenant = Organization do client.
- Credenciais autenticam a Account vinculada 1:1; operações financeiras usam esse `accountId`.
- Conta diferente da vinculada → `403 FORBIDDEN`.
