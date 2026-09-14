# Guia de integração

1. **Receber credenciais** — `client_id`, `client_secret`, Organization ID, **Account ID** (1:1 com o client), URL base (sandbox/prod).
2. **Obter access token** — `POST /api/v1/oauth/token` com `Content-Type: application/x-www-form-urlencoded` e `grant_type=client_credentials` (ver [AUTHENTICATION.md](AUTHENTICATION.md)).
3. **Primeira leitura** — `GET /api/v1/accounts/{accountId}` com Bearer token.
4. **Consultar recursos** — wallet, extrato, chaves PIX (`pix.read` / `wallet.read`).
5. **Operação PIX** (opcional) — `POST /pix/transfers` com `Idempotency-Key` único e scope `pix.transfer`.
6. **Cobrança (charges)** — `POST /charges` com scopes `charges.create` (sem `accountId` no body). Use `externalReference` do ERP; `billingType` = `PIX` | `BOLETO` | `CREDIT_CARD`. Split opcional com `walletId` Asaas. Ver [API_REFERENCE.md](API_REFERENCE.md).
7. **Antecipação** — no **sandbox**, use `POST /anticipations` (criar) + `GET /anticipations` (listar). **`/anticipations/simulate` não funciona no Sandbox Asaas** — só em produção. Um payment/charge por request.
8. **Acompanhar status** — polling em `GET /pix/transfers/{id}` ou `GET /charges/{id}` (webhooks outbound ainda não disponíveis; ver [WEBHOOKS.md](WEBHOOKS.md)). Eventos Asaas de pagamento atualizam a charge no Theron via webhook inbound.
9. **Tratar erros** — ver [ERRORS.md](ERRORS.md); em 401 no token endpoint use RFC 6749; na API use `code`.
10. **Renovar token** — antes de `expires_in`; não há refresh token OAuth — reautentique com client credentials.

## Checklist de produção

- [ ] Secret apenas em secret manager
- [ ] HTTPS obrigatório
- [ ] Idempotency-Key estável por intenção de pagamento PIX
- [ ] `externalReference` estável por cobrança no ERP
- [ ] Retries com backoff em timeouts de rede
- [ ] Monitorar 401 (token expirado/revogado) e reobter token
- [ ] Plano de rotação de `client_secret` com a Theron
- [ ] Homologar charges (boleto/PIX/cartão) e antecipação (`create`/`list`; `simulate` em produção)
- [ ] Confirmar `walletId` Asaas corretos se usar `split`

## Coleção Postman

Importar [`postman/Theron-B2B-API.postman_collection.json`](../../postman/Theron-B2B-API.postman_collection.json) e o environment sandbox — ver [`postman/README.md`](../../postman/README.md).
