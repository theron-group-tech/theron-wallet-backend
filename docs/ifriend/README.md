# Theron Wallet API — Integração iFriend

Documentação de contrato para consumo **machine-to-machine** da Theron Wallet API pelo sistema interno da iFriend.

## Visão geral

A Theron Wallet API gerencia contas financeiras (Accounts), saldos, PIX e extrato em cima do trilho Asaas. A iFriend **não** usa o frontend Theron: autentica com **OAuth 2.0 Client Credentials** e chama a API diretamente.

```text
iFriend Backend
  → POST /api/v1/oauth/token  (client_id + client_secret)
  → Access Token (JWT, curta duração)
  → GET/POST /api/v1/...  (Authorization: Bearer <token>)
```

Credenciais Asaas (API keys) são **exclusivas do backend Theron** e nunca são expostas a parceiros.

## Documentos

| Arquivo | Conteúdo |
|---------|----------|
| [AUTHENTICATION.md](AUTHENTICATION.md) | Client Credentials, token, scopes |
| [API_REFERENCE.md](API_REFERENCE.md) | Endpoints disponíveis ao client |
| [WEBHOOKS.md](WEBHOOKS.md) | Polling atual; outbound futuro |
| [ERRORS.md](ERRORS.md) | Erros OAuth vs API |
| [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) | Passo a passo de integração |

## Ambientes

| Ambiente | Uso |
|----------|-----|
| Sandbox / Homologação | Testes; `environment=SANDBOX` no client |
| Produção | Tráfego real; `environment=PRODUCTION` |

URLs base e credenciais são fornecidas pela Theron fora de banda (não documentadas com valores reais aqui).

## Como obter credenciais

1. A Theron cria uma Organization da iFriend na plataforma.
2. Um Platform Admin cria um OAuth client (`POST /api/v1/admin/organizations/{orgId}/oauth-clients`) com scopes e allowlist de Accounts.
3. O `client_id` e o `client_secret` são entregues **uma vez** por canal seguro (vault).
4. O secret **não** pode ser recuperado depois — apenas rotacionado.

## Segurança (resumo)

- Nunca coloque `client_secret` em frontend, apps móveis ou repositórios.
- Armazene secrets em secret manager.
- Use HTTPS.
- Renove o access token antes da expiração.
- Use `Idempotency-Key` em toda transferência PIX.
- Rotacione credenciais periodicamente e em incidente.
