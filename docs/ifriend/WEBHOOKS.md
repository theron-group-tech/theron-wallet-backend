# Webhooks

## Estado atual (esta fase)

A iFriend **não** recebe webhooks outbound da Theron nesta versão.

- Eventos Asaas são processados **internamente** pelo backend Theron (token `asaas-access-token`).
- A iFriend deve usar **polling** nos endpoints de consulta:
  - `GET /pix/transfers/{id}`
  - `GET /pix/transactions/{id}?accountId=`
  - `GET /transactions/{id}`
  - `GET /accounts/{id}/wallet`

## Outbound futuro (contrato previsto — não implementado)

Quando habilitado, a Theron publicará eventos HTTP para uma URL cadastrada pela iFriend, por exemplo:

- `pix.transfer.completed`
- `pix.transfer.failed`
- `pix.inbound.received`

Payload e autenticação (HMAC/signature) serão documentados antes do go-live. Até lá, trate esta seção como roadmap.
