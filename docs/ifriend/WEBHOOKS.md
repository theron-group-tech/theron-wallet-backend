# Webhooks

## Inbound (Asaas → Theron)

Eventos Asaas são processados em `POST /api/v1/webhooks/asaas` (header `asaas-access-token`).

## Outbound — Vem Comigo (Theron → parceiro)

Para a organização Vem Comigo (`platform.vem-comigo.organization-id`, default `565d0a47-6cd8-441a-b1de-766f042cd6d5`), a Theron publica webhooks HTTP das movimentações financeiras.

### Configuração (env)

| Variável | Uso |
|----------|-----|
| `VEM_COMIGO_WEBHOOK_URL` | URL HTTPS do parceiro |
| `VEM_COMIGO_WEBHOOK_SECRET` | Segredo HMAC-SHA256 |
| `VEM_COMIGO_WEBHOOK_ENABLED` | Default `true`; desliga enqueue/delivery |
| `VEM_COMIGO_ORGANIZATION_ID` | Override do UUID da org |

Sem URL ou secret, o enqueue é no-op (não falha o fluxo Asaas).

### Entrega resiliente

1. Após efeito financeiro local, o evento vai para a tabela `partner_webhook_outbox` (idempotente).
2. `PartnerWebhookDeliveryScheduler` faz poll e POST assíncrono.
3. Retry com backoff (`30s × 2^(n-1)`, teto 15 min); após `PARTNER_WEBHOOK_MAX_ATTEMPTS` (default 10) → status `DEAD`.
4. Registros presos em `DELIVERING` (crash mid-POST) são reclaimed após `PARTNER_WEBHOOK_STALE_DELIVERING_SECONDS` (default 120s).

### Headers

| Header | Valor |
|--------|-------|
| `Content-Type` | `application/json` |
| `X-Theron-Signature` | `sha256=<hex HMAC-SHA256 do body raw com o secret>` |
| `X-Theron-Event` | tipo do evento (ex. `charge.received`) |
| `X-Theron-Delivery-Id` | UUID do outbox |
| `X-Theron-Timestamp` | ISO-8601 |

### Envelope

```json
{
  "id": "<delivery-uuid>",
  "event": "charge.received",
  "occurredAt": "2026-10-02T20:00:00Z",
  "organizationId": "565d0a47-6cd8-441a-b1de-766f042cd6d5",
  "data": {
    "chargeId": "...",
    "accountId": "...",
    "amount": "100.00",
    "status": "RECEIVED",
    "asaasPaymentId": "pay_..."
  }
}
```

### Eventos

| Evento | Quando |
|--------|--------|
| `charge.received` | Cobrança paga (`RECEIVED` / `CONFIRMED`) |
| `charge.cancelled` | Cobrança cancelada / overdue |
| `charge.refunded` | Estorno / chargeback |
| `pix.inbound.received` | PIX recebido creditado |
| `pix.transfer.completed` | PIX saída concluído |
| `pix.transfer.failed` | PIX saída falhou |
| `transfer.inbound.completed` | Transferência inbound concluída |
| `transfer.outbound.completed` | Transferência outbound (não-PIX) concluída |
| `transfer.outbound.failed` | Transferência outbound falhou |

Resposta esperada do parceiro: HTTP **2xx**. Qualquer outro status ou timeout dispara retry.

## Outras orgs / iFriend genérico

Outbound genérico para demais parceiros **ainda não** está habilitado; usem polling nos endpoints documentados em `API_REFERENCE.md` / `INTEGRATION_GUIDE.md`.
