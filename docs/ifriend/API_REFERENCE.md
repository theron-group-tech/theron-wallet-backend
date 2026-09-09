# API Reference (B2B)

Base: `/api/v1`. Todas as rotas abaixo (exceto `/oauth/token`) exigem `Authorization: Bearer <access_token>`.

## OAuth

### POST `/oauth/token`

- Auth: client_id/secret (form ou Basic)
- Erros: formato RFC 6749 (ver ERRORS.md)

## Organization

### GET `/organizations/{id}`

- Scope: `organization.read`
- Só a Organization do próprio client

## Accounts

### GET `/accounts/{id}`

- Scope: `wallet.read`
- Account vinculada 1:1 ao OAuth client

### GET `/accounts/{id}/wallet`

- Scope: `wallet.read`

### GET `/accounts/{id}/ledger-balance`

- Scope: `wallet.read`

## PIX

### POST `/pix/transfers`

- Scope: `pix.transfer`
- Header obrigatório: `Idempotency-Key: <uuid>`
- Body (exemplo):

```json
{
  "accountId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "amount": 100.50,
  "destinationPixKey": "destino@email.com",
  "destinationPixKeyType": "EMAIL",
  "description": "Pagamento"
}
```

- Replay com a mesma chave e mesmo payload → mesma operação (sem duplicar)
- Mesma chave com payload diferente → conflito

### GET `/pix/transfers?accountId=`

- Scope: `pix.read`

### GET `/pix/transfers/{id}`

- Scope: `pix.read`

### POST `/pix/qr-codes`

- Scope: `pix.create`
- Body: `accountId`, `pixKeyId`, `value?`, `description?`

### POST `/pix/qr-codes/pay`

- Scope: `pix.transfer`
- Header: `Idempotency-Key` obrigatório
- Body: `accountId`, `payload` (EMV), `amount?`, `description?`

### GET `/pix/transactions/{asaasPixTransactionId}?accountId=`

- Scope: `pix.read`
- Poll de status após pay QR

### GET `/pix/keys?accountId=` / POST `/pix/keys` / DELETE `/pix/keys/{id}`

- Scopes: `pix.read` / `pix.create`

### GET `/pix/keys/lookup?accountId=&type=&key=`

- Scope: `pix.transfer`

## Transactions / Wallets

### GET `/transactions?walletId=`

- Scope: `transactions.read`

### GET `/wallets/{walletId}`

- Scope: `wallet.read`

## Idempotency

Para `POST /pix/transfers` e `POST /pix/qr-codes/pay`:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Gere uma chave única por intenção de negócio (ex.: id do pagamento no ERP). Reenvie a mesma chave em retries de rede.
