# REGRAS DE NEGÓCIO OFICIAIS — THERON WALLET BACKEND

Este documento é a **fonte canônica** do domínio. Substitui o modelo anterior com roles `ADMIN`/`AUDITOR`, carteira coletiva da Organization e `ApprovalPolicy` genérica para PIX pessoal.

## Modelo

```
PLATFORM OWNER → PLATFORM ACCOUNT → ASAAS MASTER
                      ↓
                 ORGANIZATION (tenant administrativo)
                      ↓
           OWNER / FINANCE / EMPLOYEE
                      ↓
              ACCOUNT INDIVIDUAL → WALLET → ASAAS SUBACCOUNT
```

A Organization **não** possui saldo coletivo. Recursos financeiros pertencem às Accounts individuais.

---

## 1. Atores e autenticação

| Ator | JWT | Escopo |
|------|-----|--------|
| PLATFORM OWNER | `adminId` | `/api/v1/admin/**` |
| PRODUCT USER | `userId` | rotas de produto; membership + RBAC |

- Login: produto primeiro; senão PLATFORM OWNER ativo.
- Produto: access + refresh/session. Admin: access only.
- Nunca confiar em `organizationId`/`accountId`/`userId` do client para autorização.

## 2. Platform Owner

- Cria/edita/ativa/suspende Organizations; define OWNER inicial via `POST /admin/organizations/{id}/owners` (cria user + membership OWNER + Account + Asaas). Assign legado: `POST .../admin` com `{ userId }`.
- Consulta Platform Account / saldo Master (`GET /admin/platform-account`) e extrato global (`GET /admin/transactions`). Divergências Asaas vs ledger local: `GET /admin/platform-account/balance-divergences`.
- Configura split; administra Platform Account (Master Asaas).
- PIX da Platform Account: `GET/POST/DELETE /admin/platform-account/pix/keys`, `GET /admin/platform-account/pix/keys/lookup` (lookup de destino via Asaas `/pix/addressKeys/external`, usado no modal de confirmação de envio) e `GET/POST /admin/platform-account/pix/transfers` com `ASAAS_API_KEY` (Master). Criação de chave só `EVP`. Transfers exigem `Idempotency-Key` e são persistidos em `platform_pix_transfer` (sem FK de Account) para a autorização externa Asaas (`POST /webhooks/asaas/transfer-validation` com `ASAAS_TRANSFER_VALIDATION_TOKEN` / URL). Webhooks TRANSFER_* da Master usam `ASAAS_WEBHOOK_TOKEN` e atualizam o status; se o destino for chave PIX Theron ACTIVE, creditam `TRANSFER_IN` + wallet local (`credit_transaction_id`). Backfill: `POST /admin/platform-account/pix/reconcile-credits`.
- PIX produto: `GET /pix/keys/lookup?accountId&type&key` consulta destino com a apiKey da subconta no modal de envio (exige bind ACTIVE + `pix.transfer`).
- **Saldo exibido** (produto e admin): Asaas `GET /finance/balance` (Master ou apiKey da subconta). `wallet.balance` / ledger permanecem espelho interno; dashboard inclui `ledgerBalance` para auditoria.
- Não pertence a Organization.
- Platform Account recebe splits; **não** é subconta filha.

## 3. Organization

- Tenant administrativo (isolamento, memberships, roles).
- Documento **CNPJ** (14 dígitos). CPF não é suportado — regra Asaas BaaS para subcontas.
- Status: `ACTIVE` | `SUSPENDED` | `INACTIVE`.
- Operações normais exigem `ACTIVE`.
- **Não** tem Wallet/saldo coletivo.

## 3.1 Asaas subconta

- Subcontas Asaas exigem **CNPJ** (titular PJ). CPF é rejeitado no provision (`422`).
- **OWNER:** CNPJ da Organization (ou informado no admin).
- **FINANCE/EMPLOYEE:** CNPJ **próprio** (MEI/filial), distinto por Account.
- `ASAAS_WEBHOOK_URL` é **opcional** em dev/sandbox; sem URL, create não registra webhooks inline. Necessário em produção para eventos de pagamento/transfer.
- Operações PIX/deposit/withdraw exigem subconta `ACTIVE` (não `PENDING_EVALUATION`).
- **Aprovado ≠ Aguardando ativação:** “Aguardando ativação” no painel Asaas é senha/login da UI (e-mail na conta pai no Sandbox). Não é pré-requisito para PIX via API.
- No Sandbox o BE chama `POST /accounts/{id}/approve` e sincroniza `GET /myAccount/status` (apiKey da subconta). Se docs pendentes, `AsaasBindResponse.onboardingUrl` é exposto.

## 4. Membership

- Estados: `ACTIVE` | `INVITED` | `SUSPENDED` | `REMOVED`.
- Só `ACTIVE` opera. Cross-tenant → **403** sem vazar existência.

## 5. Roles (somente três)

| Role | Essência |
|------|----------|
| OWNER | Admin da org + própria Account; aprova PaymentOrders; CRUD membros |
| FINANCE | Própria Account; cria/cancela PaymentOrders; **não** aprova |
| EMPLOYEE | Própria Account; PIX/transfer pessoais; sem admin financeiro |

Roles `ADMIN` e `AUDITOR` estão **descontinuadas** no produto.

## 6. Propriedade financeira

- 1 user × 1 org → no máximo 1 Account (`UNIQUE organization_id, owner_user_id`).
- 1 Account → 1 Wallet → 1 Asaas Subaccount.
- Operações pessoais (wallet, PIX, beneficiários, extrato) só na **própria** Account.
- OWNER **não** acessa saldo/PIX/extrato de outros usuários.

## 7. Criação de usuários (OWNER)

OWNER cria `FINANCE` ou `EMPLOYEE`. Sistema cria:

User → Membership → Role → Account → Wallet → Asaas Subaccount

Provisionamento Asaas idempotente. Soft suspend/remove preserva histórico financeiro.

## 8. PIX pessoal

- Sem ApprovalPolicy. Sem `PENDING_APPROVAL`.
- Requer bind Asaas utilizável, saldo, limites, Idempotency-Key em transfer.
- Sem bind / status ≠ `ACTIVE` / sem apiKey → **422** `ASAAS_ERROR` com mensagem distinta.
- Criação de chave (`POST /api/v1/pix/keys`): somente `type=EVP` (chave aleatória). A API Asaas não cria CPF, CNPJ, e-mail ou telefone. Outros tipos → **422**. Destino de transferência / beneficiário continua com `CPF`, `CNPJ`, `EMAIL`, `PHONE`, `EVP`.

## 9. Payment Order

Instrução administrativa: FINANCE cria → OWNER aprova/rejeita.

- **Não** possui dinheiro próprio.
- Origem v1: **sempre** Account do OWNER da org.
- Destino: Account da mesma Organization.
- Create **não** debita e **não** exige saldo; approve revalida saldo Asaas/ledger (insuficiente → **409**).
- FINANCE pode cancelar enquanto `PENDING_APPROVAL`.
- Criador não aprova a própria order.

Estados: `PENDING_APPROVAL` → `APPROVED` → `PROCESSING` → `COMPLETED` | `FAILED` | `REJECTED` | `CANCELLED`.

## 10. Limites

- Continuam como segurança (por operação / diário / hierárquicos).
- Aplicados na Account correspondente (PIX pessoal e origem de PaymentOrder).
- `account_limit` (max por operação + diário PIX): provisionado automaticamente na criação da Account (defaults 5000 / 10000) e backfill para contas existentes; sem CRUD HTTP. Auto-ensure no assert do PIX se a linha faltar.
- `transaction_limit` (UI `/limites`): camada hierárquica org/conta/usuário/role por tipo e período — complementar ao `account_limit`.

## 11. Split

- Default: `enabled=true`, `percent=0`, `fixedAmount=0`.
- Só PLATFORM OWNER altera (`/admin/splits`) + auditoria.
- Aplicar só onde Asaas suporte (cobrança). Não fingir em transfer/QR estático.

## 12. Permissions (RBAC)

Ver seed Flyway V28. Frontend deve preferir `permissions.includes(...)` em vez de `role ==`.

Backend é a fonte definitiva: rota sem permissão → **403**.

## 13. Fluxo-mãe

```
PLATFORM OWNER → ORG → OWNER → cria FINANCE/EMPLOYEE
  → User+Membership+Role+Account+Wallet+Asaas

FINANCE → PaymentOrder PENDING → OWNER APPROVE
  → debit OWNER Account → credit destination → COMPLETED
```
