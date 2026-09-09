# Guia de integração

1. **Receber credenciais** — `client_id`, `client_secret`, Organization ID, Account IDs permitidos, URL base (sandbox/prod).
2. **Obter access token** — `POST /api/v1/oauth/token` com `grant_type=client_credentials`.
3. **Primeira leitura** — `GET /api/v1/accounts/{accountId}` com Bearer token.
4. **Consultar recursos** — wallet, extrato, chaves PIX (`pix.read` / `wallet.read`).
5. **Operação PIX** — `POST /pix/transfers` com `Idempotency-Key` único e scope `pix.transfer`.
6. **Acompanhar status** — polling em `GET /pix/transfers/{id}` (webhooks outbound ainda não disponíveis; ver [WEBHOOKS.md](WEBHOOKS.md)).
7. **Tratar erros** — ver [ERRORS.md](ERRORS.md); em 401 no token endpoint use RFC 6749; na API use `code`.
8. **Renovar token** — antes de `expires_in`; não há refresh token OAuth — reautentique com client credentials.

## Checklist de produção

- [ ] Secret apenas em secret manager
- [ ] HTTPS obrigatório
- [ ] Idempotency-Key estável por intenção de pagamento
- [ ] Retries com backoff em timeouts de rede
- [ ] Monitorar 401 (token expirado/revogado) e reobter token
- [ ] Plano de rotação de `client_secret` com a Theron
