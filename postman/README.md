# Theron B2B — Coleção Postman

Simula um parceiro externo (ex.: iFriend) consumindo a API com **OAuth 2.0 Client Credentials**.

Modelo:

```text
Organization
  └── Account  →  client_id + client_secret (1:1)
```

A iFriend é só um **exemplo** de Organization. Qualquer org/conta funciona.

## Arquivos

| Arquivo | Uso |
|---------|-----|
| [Theron-B2B-API.postman_collection.json](Theron-B2B-API.postman_collection.json) | Requests |
| [Theron-B2B-Sandbox.postman_environment.json](Theron-B2B-Sandbox.postman_environment.json) | Variáveis |

## Importar no Postman

1. Abra o Postman → **Import**.
2. Selecione os dois JSON desta pasta.
3. No canto superior direito, escolha o environment **Theron B2B Sandbox**.

## Preencher o Environment

| Variável | Obrigatório | O que colocar |
|----------|-------------|---------------|
| `base_url` | sim | Ex.: `http://localhost:8080` |
| `admin_email` / `admin_password` | sim (setup) | Platform Admin Theron |
| `organization_id` | sim | UUID da Organization (ex. parceiro) |
| `account_id` | sim | UUID da **Account** que receberá o OAuth client |
| `other_account_id` | para teste 403 | Outra Account (mesma org ou outra) |
| `pix_destination_key` | só PIX | Chave sandbox de destino |

O restante (`admin_access_token`, `client_id`, `client_secret`, `access_token`, `wallet_id`, `oauth_client_uuid`) é preenchido **automaticamente** pelos scripts das requests.

> Backend precisa estar rodando e a Account/Organization já existirem no banco.

## Ordem de uso (rápido)

```text
0. Setup Admin
   1) Login Admin
   2) Create OAuth Client (1:1 Account)   ← secret aparece UMA vez
1. OAuth Token
   3) Get Access Token
2. Conta Wallet Org
   4) Get Account → Get Wallet → …
3. Isolamento Security (opcional)
4. PIX Sandbox (opcional; só com Asaas sandbox)
```

### Passo a passo

1. **Login Admin**  
   Usa `admin_email` / `admin_password`. Salva `admin_access_token`.

2. **Create OAuth Client**  
   Body com `"accountIds": ["{{account_id}}"]` — **exatamente um** ID.  
   Salva `client_id`, `client_secret`, `oauth_client_uuid`.  
   Guarde o secret: não volta a aparecer (só em rotate).

3. **Get Access Token**  
   `grant_type=client_credentials`. Salva `access_token` (~15 min).

4. **Get Account / Get Wallet**  
   Bearer do cliente B2B. Wallet grava `wallet_id`.

5. **Isolamento**  
   Preencha `other_account_id` e rode **Get Other Account** → espera **403**.  
   Para revoke: rode **Revoke OAuth Client** e depois **After Revoke - Get Account** → **401**.

6. **PIX**  
   Só em sandbox. Transferências exigem header `Idempotency-Key`.

## Pastas da collection

| Pasta | Auth | Função |
|-------|------|--------|
| `0. Setup Admin` | Bearer admin | Criar/listar/rotacionar/revogar OAuth client |
| `1. OAuth Token` | client_id/secret | Obter JWT M2M |
| `2. Conta Wallet Org` | Bearer `access_token` | Conta, saldo, org, extrato |
| `3. Isolamento Security` | Bearer `access_token` | 403 / revoke |
| `4. PIX Sandbox` | Bearer `access_token` | Chaves e transfers |

## Scopes usados no Create Client

`organization.read`, `wallet.read`, `transactions.read`, `pix.read`, `pix.create`, `pix.transfer`, `charges.read`, `charges.create`, `charges.cancel`, `anticipations.read`, `anticipations.create`

## Segurança

- Não commite `client_secret` / senhas reais no Git.
- Não use essas credenciais em frontend ou app mobile.
- Em produção use HTTPS e vault para secrets.

## Documentação da API

- [docs/ifriend/AUTHENTICATION.md](../docs/ifriend/AUTHENTICATION.md)
- [docs/ifriend/API_REFERENCE.md](../docs/ifriend/API_REFERENCE.md)
- [docs/ifriend/ERRORS.md](../docs/ifriend/ERRORS.md)
