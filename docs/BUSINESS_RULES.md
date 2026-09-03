# REGRAS DE NEGÓCIO OFICIAIS — THERON WALLET BACKEND

Este documento é a **fonte canônica** do domínio. Substitui o modelo anterior com roles `ADMIN`/`AUDITOR`, carteira coletiva da Organization e `ApprovalPolicy` genérica para PIX pessoal.

**Fluxogramas visuais:** [`FLUXOGRAMA.md`](FLUXOGRAMA.md)

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
- PIX da Platform Account: `GET/POST/DELETE /admin/platform-account/pix/keys`, `GET /admin/platform-account/pix/keys/lookup`, `POST /admin/platform-account/pix/qr-codes`, `POST /admin/platform-account/pix/qr-codes/pay` (copia e cola) e `GET/POST /admin/platform-account/pix/transfers` com `ASAAS_API_KEY` (Master). Criação de chave só `EVP`. Transfers por chave e pay QR exigem `Idempotency-Key` e são persistidos em `platform_pix_transfer` para autorização externa Asaas — ver **§2.1**. Webhooks `TRANSFER_*` da Master usam `ASAAS_WEBHOOK_TOKEN`; destino chave Theron ACTIVE credita `TRANSFER_IN` + wallet local. Backfill: `POST /admin/platform-account/pix/reconcile-credits`.
- PIX produto: `GET /pix/keys/lookup?accountId&type&key` consulta destino com a apiKey da subconta no modal de envio (exige bind ACTIVE + `pix.transfer`).
- **Saldo exibido** (produto e admin): Asaas `GET /finance/balance` (Master ou apiKey da subconta). `wallet.balance` / ledger permanecem espelho interno; dashboard inclui `ledgerBalance` para auditoria. PIX recebido na subconta (QR estático ou chave) credita o ledger local via webhook `PAYMENT_RECEIVED` / `PAYMENT_CONFIRMED` sem cobrança Theron prévia (`TRANSFER_IN` COMPLETED, idempotency `asaas:pix:in:{paymentId}`); reenvio não duplica.
- Não pertence a Organization.
- Platform Account recebe splits; **não** é subconta filha.

### 2.1 Autorização externa Asaas (`transfer-validation`)

Saídas PIX da Master exigem aprovação via `POST /webhooks/asaas/transfer-validation` (token `ASAAS_TRANSFER_VALIDATION_TOKEN`; URL `ASAAS_TRANSFER_VALIDATION_URL` no painel Asaas Master).

| Operação | `type` | Objeto | Campo chave |
|----------|--------|--------|-------------|
| Transfer por chave (Master) | `TRANSFER` | `transfer` | `transfer.id` → `asaas_transfer_id` |
| Pay QR copia e cola (Master) | `PIX_QR_CODE` | `pixQrCode` | `pixQrCode.id` → `asaas_pix_transaction_id` |
| Pay QR copia e cola (subconta) | `PIX_QR_CODE` | `pixQrCode` | `pixQrCode.id` → `pix_transaction.asaas_pix_transaction_id` |

**Sequência pay QR:** pré-registro em `platform_pix_transfer` (`status=PROCESSING`, `asaas_transfer_id` null) → chamada Asaas `POST /pix/qrCodes/pay` → atualiza `asaas_pix_transaction_id` (+ `asaas_transfer_id` se disponível) → Asaas chama `transfer-validation` com `type=PIX_QR_CODE` → `APPROVED` → webhook `TRANSFER_*` confirma.

**Fallbacks de lookup:** `externalReference` (= idempotency key) ou pending QR pay recente (valor + janela de 10 min). Sem registro prévio, o Asaas recusa com *"Autorização externa foi recusada"*.

**Ops:** URL de validação = `{BACKEND}/api/v1/webhooks/asaas/transfer-validation`; token = `ASAAS_TRANSFER_VALIDATION_TOKEN` no Railway.

## 3. Organization

- Tenant administrativo (isolamento, memberships, roles).
- Documento **CNPJ** (14 dígitos). CPF não é suportado — regra Asaas BaaS para subcontas.
- Status: `ACTIVE` | `SUSPENDED` | `INACTIVE`.
- Operações normais exigem `ACTIVE`.
- **Não** tem Wallet/saldo coletivo.

## 3.1 Asaas subconta e onboarding financeiro

- **Organization** continua exigindo **CNPJ** (14 dígitos) — tenant administrativo.
- **Subconta Asaas** (titular da Account): **CPF (PF)** ou **CNPJ (PJ)**, escolhido pelo titular no wizard self-service (`POST/PUT /api/v1/asaas/onboarding/*`).
- **Sem auto-provision:** criar Account, membro ou Owner **não** cria subconta Asaas automaticamente. Titular conclui onboarding após login.
- `POST /api/v1/accounts/{id}/asaas-subaccount` está **descontinuado** — usar onboarding.
- Operações PIX/transfer/PaymentOrder (origem) exigem subconta `ACTIVE` **e** onboarding `APPROVED` (`financialResourcesEnabled=true` em `AccountResponse`).
- Subcontas legadas (auto-provisionadas antes da migration) têm `legacy_auto_provisioned=true` e seguem liberadas se `ACTIVE`.
- `ASAAS_WEBHOOK_URL` é **opcional** em dev/sandbox; necessário em produção. Registrar eventos `ACCOUNT_STATUS_*` além de `PAYMENT_*` e `TRANSFER_*`.
- No Sandbox o BE pode chamar `POST /accounts/{id}/approve` após submit; status final via webhook ou `GET /myAccount/status`.
- Se documentação pendente, `onboardingUrl` é exposto na resposta do onboarding.

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
- Pay copia e cola (`POST /api/v1/pix/qr-codes/pay`): body `accountId`, `payload`, `amount?`, `description?` + `Idempotency-Key`. Roles com `pix.transfer` (**OWNER, FINANCE, EMPLOYEE**) na **própria Account** — mesma UX em `/transferencias` (colar EMV no campo de chave troca para copia e cola). Pré-registra `Transaction` + `PixTransaction` antes do Asaas; validação externa usa `type=PIX_QR_CODE` (ver **§2.1**). Poll: `GET /api/v1/pix/transactions/{asaasPixTransactionId}?accountId=`. O débito é na origem; o crédito no destino (QR/chave da subconta) chega pelo webhook `PAYMENT_RECEIVED` da cobrança auto-criada no Asaas e vira `TRANSFER_IN` + ledger local.

## 9. Payment Order

Instrução administrativa: FINANCE cria → OWNER aprova/rejeita.

- **Não** possui dinheiro próprio.
- Origem na **aprovação**: Account do **OWNER que aprova** (não há origem fixa no create; `sourceAccountId` vem null em `PENDING_APPROVAL`).
- Destino: Account da mesma Organization.
- Create **não** debita e **não** exige saldo; approve revalida saldo Asaas/ledger (insuficiente → **409**).
- Approve chama Asaas `POST /transfers` (account-to-account, `walletId` destino) com API key da subconta do OWNER aprovador; confirma com `GET /transfers/{id}` e só aceita `DONE` antes de debitar ledger local.
- FINANCE pode cancelar enquanto `PENDING_APPROVAL`.
- Criador não aprova a própria order.
- Ordens legadas `PROCESSING`: webhook `TRANSFER_*` ou `POST /admin/payment-orders/{id}/sync`.

Estados: `PENDING_APPROVAL` → `PROCESSING` (legado) → `COMPLETED` | `FAILED` | `REJECTED` | `CANCELLED`.

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
