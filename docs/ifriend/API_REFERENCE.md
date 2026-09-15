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

## Charges (cobranças Asaas)

`accountId` **não** é aceito no body — vem do OAuth client (1:1).

### POST `/charges`

- Scope: `charges.create`
- Idempotência local: mesmo `externalReference` na mesma Account retorna a charge existente (sem nova cobrança Asaas)
- `installments`: só para `billingType=CREDIT_CARD` (PIX/BOLETO sem parcelas nesta API)
- `split` (opcional): array de contrapartes Asaas; a Theron pode **acrescentar** split de plataforma se configurado

Body (exemplo):

```json
{
  "customer": {
    "name": "Cliente B2B",
    "cpfCnpj": "52998224725",
    "email": "cliente@example.com"
  },
  "value": 100.00,
  "billingType": "BOLETO",
  "dueDate": "2030-12-31",
  "description": "Cobrança parceiro",
  "externalReference": "erp-order-12345",
  "split": [
    {
      "walletId": "asaas-wallet-id-destino",
      "percentualValue": 10
    }
  ]
}
```

`billingType`: `PIX` | `BOLETO` | `CREDIT_CARD`.

Exemplo PIX (QR via fatura):

```json
{
  "billingType": "PIX",
  "value": 50.00,
  "dueDate": "2030-12-31",
  "customer": { "name": "Cliente B2B", "cpfCnpj": "52998224725", "email": "cliente@example.com" },
  "externalReference": "erp-pix-123"
}
```

Para PIX, a resposta inclui `invoiceUrl`: o pagador abre essa URL na fatura Asaas para visualizar/pagar o **QR Code**. A API B2B de charges **não** devolve payload EMV (copia e cola) nem imagem base64 do QR em `ChargeResponse`.

Split por item:
- `walletId` — ID de **carteira Asaas** (não UUID da wallet Theron)
- `percentualValue` **ou** `fixedValue` (um dos dois, > 0)

Cartão com parcelas:

```json
{
  "billingType": "CREDIT_CARD",
  "value": 300.00,
  "installments": 3,
  "dueDate": "2030-12-31",
  "customer": { "name": "...", "cpfCnpj": "..." }
}
```

Resposta típica inclui `id`, `asaasPaymentId`, `status`, `invoiceUrl` / `bankSlipUrl` (conforme tipo), `splits`.

### GET `/charges` / GET `/charges/{id}`

- Scope: `charges.read`
- Sempre filtrado à Account do OAuth client

### POST `/charges/{id}/cancel`

- Scope: `charges.cancel`
- Cancela no Asaas e atualiza status local

## Anticipations

A Account autenticada precisa ter subconta Asaas ativa. Antecipação opera sobre cobranças/pagamentos **dessa** Account.

### POST `/anticipations/simulate`

- Scope: `anticipations.create`
- Body: `chargeIds` e/ou `paymentIds` (UUIDs Theron e/ou IDs Asaas `pay_...`)
- Envie **exatamente um** payment elegível por request (a API Asaas antecipa um `payment` ou um `installment` por chamada)
- **Sandbox Asaas:** simulação **não está disponível** (limitação do provedor). Use `POST /anticipations` + listagem para homologar no sandbox; `simulate` só em produção Asaas

### POST `/anticipations`

- Scope: `anticipations.create`
- Mesmo body que simulate; **disponível no sandbox** (solicitar antecipação)
- Preferir um único `chargeId` / `paymentId` por request

Exemplo:

```json
{
  "chargeIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
}
```

### GET `/anticipations` / GET `/anticipations/{id}`

- Scope: `anticipations.read`
- Listar / detalhar antecipações da Account autenticada (**disponível no sandbox**)

## Idempotency

### Header (PIX)

Para `POST /pix/transfers` e `POST /pix/qr-codes/pay`:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Gere uma chave única por intenção de negócio (ex.: id do pagamento no ERP). Reenvie a mesma chave em retries de rede.

### Body (charges)

Use `externalReference` estável por cobrança no ERP. Retries com o mesmo valor na mesma Account não criam cobrança duplicada.

O Theron normaliza a `Idempotency-Key` enviada ao Asaas para no máximo 48 caracteres (limite do provedor); `externalReference` no contrato Theron pode ser mais longo (até 100).
