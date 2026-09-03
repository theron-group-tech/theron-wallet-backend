# Prompt — Frontend Theron Wallet

Cole este documento inteiro em um agente de código num **repositório frontend separado**. Não altere o backend. Não invente rotas, campos nem comportamentos que não estejam aqui.

**Fonte canônica de domínio:** `docs/BUSINESS_RULES.md` no backend. Roles de produto: **somente** `OWNER` | `FINANCE` | `EMPLOYEE` (sem `ADMIN`/`AUDITOR`). PIX pessoal **sem** ApprovalPolicy. Folha/pagamento administrativo = **PaymentOrder**.

Você é um engenheiro frontend sênior. Sua tarefa é **criar do zero** o app web/mobile-responsive **Theron Wallet**, consumindo a API já existente do backend Java (`theron-wallet-backend`, Spring Boot, porta `8080`, prefixo `/api/v1`).

---

## 1. Papel e restrições

- Stack **obrigatória:** Next.js (App Router) + TypeScript + Tailwind CSS.
- Idioma da UI: **português (Brasil)**. Códigos e `message` da API vêm em **inglês** — mostre `message` ao usuário; não traduza `code`.
- Autenticação: JWT de **usuário de produto** (`userId`) ou **APP_ADMIN** (`adminId`). Login único em `/login` — se a resposta tiver `adminId`, gravar sessão admin e ir para `/admin`; senão, sessão produto e `/dashboard`. Sessões: `theron-session` vs `theron-admin-session`.
- Ator: somente o Bearer. **Não envie** `X-Actor-User-Id`.
- O backend **não tem CORS**. O browser não pode chamar `localhost:8080` direto. Use **rewrite/proxy** do Next.
- Não implemente webhooks Asaas. Não chame `GET /api/v1/subaccounts` (listagem é admin-only / 403 no produto).
- Bind Asaas do produto: `POST /api/v1/accounts/{accountId}/asaas-subaccount` (própria Account). Não invente outras rotas de creditar conta.
- `POST /api/v1/organizations` com JWT de produto → **403**. Org só via plataforma.

---

## 2. Marca e visual

O mockup de referência usa roxo. **Ignore o roxo.** Primária = ouro Satin Sheen Gold.

### Paleta (Tailwind tokens)

| Token | Hex | Uso |
|---|---|---|
| `white` | `#FFFFFF` | Fundo de cards, texto invertido sobre azul |
| `anti-flash` | `#F3F6F7` | Fundo da página, respiro |
| `gold` | `#CCA000` | CTAs, links ativos, badges de valor, ícones de destaque |
| `prussian` | `#003249` | Sidebar desktop, header mobile, texto de marca |
| `eerie` | `#1D1D1B` | Texto principal, ícones de linha |
| `black` | `#000000` | Títulos de autoridade, overlay |

Estados: sucesso em verde sóbrio só para valores de entrada; saídas em vermelho sóbrio. Status: Pendente = ouro suave; Ativo = prussian.

### Tipografia

O brief da marca não nomeou família. Use:

- **Manrope** — UI (nav, tabelas, forms, labels).
- **Playfair Display** — saldo em destaque e títulos de prestígio (dashboard “Olá, João”, valor R$).

Carregue via `next/font/google`.

### Ativos da marca (motivos, se não houver SVG oficial)

Trate como geometria SVG simples, stroke fino, ouro ou branco sobre prussian:

- Cruz Modular — equilíbrio (logo mark / empty states).
- Geométrica N / R / HE — grids, cards, fundos discretos.
- Círculo Duplo — elo cliente–consultor (avatar ring, loading).
- Arco / Curva — charts e transições.
- Estrela — valor, clareza (destaque de saldo, favoritos).

Layout: sidebar escura (`prussian`) à esquerda no desktop; conteúdo em `anti-flash`; cards brancos, cantos arredondados, sombras leves. Mobile: bottom nav + telas cheias (Home, Aprovações, Enviar PIX).

---

## 3. Arquitetura do app

```
app/
  (auth)/login/page.tsx
  (auth)/signup/page.tsx
  (app)/layout.tsx          # sidebar + org switcher + user
  (app)/dashboard/page.tsx
  (app)/contas/page.tsx
  (app)/pix/page.tsx
  (app)/transferencias/page.tsx
  (app)/extrato/page.tsx
  (app)/ordens-pagamento/page.tsx   # PaymentOrders (FINANCE/OWNER) — NÃO ApprovalPolicy de PIX
  (app)/beneficiarios/page.tsx
  (app)/equipe/page.tsx
  (app)/limites/page.tsx
  (app)/perfil/page.tsx
  (app)/configuracoes/page.tsx
lib/
  api.ts                    # fetch wrapper
  auth.ts                   # tokens + refresh
  types.ts                  # DTOs
  permissions.ts            # hide/show
```

### Proxy (obrigatório)

`next.config.ts`:

```ts
async rewrites() {
  return [{ source: "/api/v1/:path*", destination: "http://localhost:8080/api/v1/:path*" }];
}
```

O cliente chama **sempre** `/api/v1/...` (same-origin). Base URL pública do backend: `http://localhost:8080` (sem `context-path`).

### Cliente HTTP único

- `Content-Type: application/json`.
- `Authorization: Bearer <accessToken>`.
- `X-Correlation-Id`: UUID por request (aparece como `traceId` no erro).
- Header **`Idempotency-Key`**: UUID **obrigatório** em `POST /deposits`, `POST /withdraws`, `POST /pix/transfers`, `POST /pix/qr-codes/pay`. Opcional em `POST /transfers/internal` e approve/reject/cancel.
- Em **401** (exceto login): tentar `POST /auth/refresh` com o `refreshToken`; se falhar, logout e ir para login.
- Rate limit: login/refresh podem devolver **429** `RATE_LIMITED` (20 tentativas / 300s por IP).

Persistência: `accessToken`, `refreshToken`, `userId`, `expiresIn` em memória + `sessionStorage` ou cookie httpOnly se implementar route handlers; não logar tokens.

### Seletor de organização

`GET /me` devolve `organizations[]` **sem roles**. Org ativa no estado global.

Para cada org ativa, carregue:

`GET /api/v1/organizations/{organizationId}/members/{userId}/permissions`

Esconda botões com essa lista. Recarregue ao trocar de org.

---

## 4. Telas (mapear o mockup)

| Tela | Fonte de dados | Notas |
|---|---|---|
| Login / Signup | `POST /auth/login`, `POST /users` | Signup público; senha mín. 8 |
| Dashboard | `GET /me/dashboard?accountId=` | **Só Account própria** (owner_user_id = eu) |
| Contas | Account própria + wallet | Sem visão de saldo alheio, mesmo OWNER |
| PIX | `GET/POST /pix/keys`, QR, transfers | Pessoal; **sem** tela de aprovação |
| Extrato | `GET /accounts/{id}/statement` | Só Account própria |
| Ordens de pagamento | `/payment-orders` | FINANCE cria/cancela; OWNER aprova/rejeita |
| Beneficiários | CRUD `/beneficiaries` | Escopo da própria Account |
| Equipe | `/organization/members` | OWNER: cria FINANCE/EMPLOYEE + Account+Asaas |
| Limites | `/limits` | `limits.read` / `limits.manage` |
| Perfil | `PATCH` self com `profile.update` | Nome/email/telefone |
| Configurações | `PATCH /organizations/{id}` se `organization.update` | |

Quick actions do dashboard: Nova transação / Enviar PIX / Adicionar beneficiário — só se a permissão existir. “Cobrar” = criar QR PIX (`POST /pix/qr-codes`) se `pix.create`. Enviar PIX (`/transferencias`): Destino só **Chave PIX avulsa** ou **Copia e cola** (sem Beneficiário).

---

## 5. Passo a passo de implementação (siga nesta ordem)

### Passo 0 — Projeto

Crie o Next.js, Tailwind, tokens de cor, fontes, layout shell (sidebar prussian + conteúdo). Ainda sem API.

### Passo 1 — Health e proxy

`GET /api/v1/health` via rewrite. Esperado: `{ "status": "UP", "service": "theron-wallet-service", "timestamp": "..." }`. Se falhar, o rewrite está errado.

### Passo 2 — Signup e login

1. `POST /api/v1/users`  
   Body: `{ "name", "email", "password" }` (opt `phone`).  
   201 `UserResponse`. 409 genérico se e-mail duplicado.

2. `POST /api/v1/auth/login`  
   Body: `{ "email", "password", "deviceId", "deviceName", "platform": "web" }`.  
   `deviceId` estável (UUID no browser).  
   200: use **`accessToken`** (alias `token` é o mesmo JWT). Guarde `refreshToken`, `expiresIn` (ms, access ~15 min).  
   - Se `adminId` presente → sessão **admin** → `/admin` (não misturar com sessão produto).  
   - Senão → sessão **produto** (`userId`, `name`, `email`) → `/dashboard`.  
   `/admin/login` apenas redireciona para `/login`.  
   401: `"Invalid email or password"` (não enumere e-mail). 429: rate limit.

### Passo 3 — Sessão

- `POST /api/v1/auth/refresh` body `{ "refreshToken" }` → novo par de tokens.
- `POST /api/v1/auth/logout` body opcional `{ "refreshToken" }` → 204.
- `POST /api/v1/auth/logout-all` (JWT) → 204.
- `GET /api/v1/auth/sessions` / `DELETE /api/v1/auth/sessions/{id}` para Configurações > dispositivos.

### Passo 4 — Me e dashboard

- `GET /api/v1/me` → perfil + orgs. Monte o org switcher.
- `GET /api/v1/me/dashboard?organizationId=&accountId=`  
  Campos: `balance`, `availableBalance`, `blockedBalance`, `currency`, `todayIncome`, `todayExpenses`, `pendingTransactions[]`, `recentTransactions[]`, `organizationId`, `accountId`.
- `GET /api/v1/notifications/unread-count` para o sino.

### Passo 5 — Contas e saldo

- `GET /api/v1/organizations/{organizationId}/accounts` (perm `wallet.read`).
- `POST /api/v1/organizations/{organizationId}/accounts` `{ "name", "type": "MAIN"|"EMPLOYEE"|"RESERVE" }` (perm `organization.update`).
- `GET /api/v1/accounts/{id}`  
- `GET /api/v1/accounts/{id}/wallet` → `balance` da **Account** (é este o saldo do dashboard/PIX).  
- `GET /api/v1/accounts/{id}/ledger-balance` → conferência. PIX recebido em QR/chave da subconta credita `ledgerBalance` via webhook `PAYMENT_RECEIVED` (`TRANSFER_IN`); a UI só lê o saldo, não credita localmente.

**Não** use `POST /deposits` para encher este saldo. Depósito credita wallet de **subconta**, outro trilho.

### Passo 6 — PIX (Account)

Exige Account com subconta Asaas **ACTIVE** (`asaasStatus` em `AccountResponse`). Sem bind ACTIVE, mostre o Modal de criação e chame `POST /api/v1/accounts/{accountId}/asaas-subaccount` (dono da Account). 422 = mostrar `message`, não fingir sucesso.

- `GET /api/v1/pix/keys?accountId=` (`pix.read`)
- `POST /api/v1/pix/keys` `{ "accountId", "type": "EVP" }` (`pix.create`) → 201; chave aleatória gerada no Asaas. **Somente EVP** na UI de criação (CPF/CNPJ/EMAIL/PHONE → **422**). Destino de transferência continua aceitando os 5 tipos.
- A chamada Asaas é `POST /v3/pix/addressKeys` com a **API key da subconta** da Account — **não** com a Master (`ASAAS_API_KEY`). No console Asaas, abra a **subconta** (não a conta raiz Theron) para ver a chave.
- `DELETE /api/v1/pix/keys/{id}` → 204
- `POST /api/v1/pix/qr-codes` `{ "accountId", "pixKeyId", "value?", "description?" }`
- `POST /api/v1/pix/qr-codes/pay` `{ "accountId", "payload", "amount?", "description?" }` + **Idempotency-Key** (`pix.transfer`). Pay copia e cola na subconta para **OWNER, FINANCE e EMPLOYEE** (Account própria). Em `/transferencias`, Destino é só chave avulsa ou copia e cola; colar EMV (`000201…`) no campo de chave **troca automaticamente** para copia e cola e preenche o valor se o QR tiver tag 54. Enviar o EMV ao Asaas **sem remover espaços** (nome/cidade/CRC). Poll: `GET /pix/transactions/{asaasId}?accountId=`.
- `POST /api/v1/pix/transfers` header `Idempotency-Key`  
  Body: `{ "accountId", "amount", "destinationPixKey", "destinationPixKeyType" }`. UI `/transferencias` **não** usa `beneficiaryId`.  
  Perm `pix.transfer`. 201 com `status` tipicamente `PROCESSING` (PIX pessoal **não** vai para ApprovalPolicy).  
  Sem bind Asaas → **422** `ASAAS_ERROR`. Mesma key + mesmo body → mesma operação. Mesma key + body diferente → 409.

### Passo 7 — Beneficiários

- `POST /api/v1/beneficiaries` `{ "organizationId", "name", "pixKey", "pixKeyType" }` ou dados bancários (`bankCode`, `branch`, `account`, `accountType`: `CHECKING`|`SAVINGS`).
- `GET /api/v1/beneficiaries?organizationId=` (query obrigatória)
- GET/PATCH/DELETE por id. DELETE = inativação (204).

### Passo 8 — Ordens de pagamento (PaymentOrder)

**Não** use `/approvals` para PIX pessoal. Use PaymentOrder para liberação administrativa:

- `POST /api/v1/payment-orders` — FINANCE ou OWNER (`payment_orders.create`). Body: `{ organizationId, destinationAccountId, amount, description? }`. Status inicial `PENDING_APPROVAL`. **Não debita** e **não exige saldo** na criação. `sourceAccountId` null até approve.
- `GET /api/v1/payment-orders?organizationId=` — `payment_orders.read`
- `GET /api/v1/payment-orders/destinations?organizationId=` — contas destino **sem saldo** (`payment_orders.create`)
- `POST .../{id}/cancel` — FINANCE criador / perm cancel; só `PENDING_APPROVAL`
- `POST .../{id}/approve` — OWNER (`payment_orders.approve`); debita a **própria Account OWNER** aprovadora; transfer Asaas account-to-account; revalida saldo Asaas/ledger; saldo insuficiente → **409**; Asaas não confirmado → **422**. Criador não aprova a própria.
- `POST .../{id}/reject` — OWNER (`payment_orders.reject`)

### Passo 9 — Equipe (OWNER)

- Preferir `POST /api/v1/organization/members` com body completo: `{ "name", "email", "password?", "phone?", "role": "FINANCE"|"EMPLOYEE", "document", "documentType", ... }` — cria User + membership + Account + Wallet + Asaas.
- `GET /api/v1/organization/members` / `PATCH` suspend/activate / `DELETE` soft-remove.
- Troca de role: `FINANCE` ↔ `EMPLOYEE` (não promover a OWNER pelo produto).
- Catálogo: `GET /api/v1/roles`, `GET /api/v1/permissions` (só OWNER/FINANCE/EMPLOYEE atribuíveis).

**Criar organização:** produto **não** cria org (`POST /organizations` → 403). OWNER vem atribuído pela plataforma.

### Passo 10 — Limites

- `POST /api/v1/limits` `{ "organizationId", "transactionType": "PIX"|"TRANSFER"|"WITHDRAWAL"|"PAYMENT", "period": "PER_TRANSACTION"|"DAILY"|"MONTHLY", "maxAmount", "accountId?", "userId?", "roleId?" }`
- `GET /api/v1/limits?organizationId=`
- `PATCH /api/v1/limits/{id}` `{ "maxAmount?", "enabled?" }`

Há também limite operacional por conta (`account_limit`: max por operação + diário), **provisionado automaticamente** na criação da Account (defaults `5000` / `10000`, configuráveis via `theron.account-limit.*`) e com backfill Flyway. Sem CRUD HTTP dedicado. A tela `/limites` gerencia `transaction_limit` (camada hierárquica extra). PIX exige ambos os checks quando aplicáveis; `account_limit` ausente é auto-criado no assert.

### Passo 11 — Notificações

- `GET /api/v1/notifications` e `GET /api/v1/me/notifications`
- `GET /api/v1/notifications/unread-count`
- `POST /api/v1/notifications/{id}/read`
- `POST /api/v1/notifications/read-all`

Tipos: `PIX_RECEIVED`, `PIX_SENT`, `TRANSFER_RECEIVED`, `TRANSFER_SENT`, `TRANSFER_APPROVAL_REQUIRED`, `TRANSFER_APPROVED`, `TRANSFER_REJECTED`, `LOGIN_NEW_DEVICE`, `PASSWORD_CHANGED`.

### Passo 12 — Extrato

Preferir `GET /api/v1/accounts/{accountId}/statement` (PageResponse).

Alternativa: `GET /api/v1/me/transactions?accountId=&from=&to=&type=&status=&minAmount=&maxAmount=`

`GET /api/v1/transactions` exige `walletId` **ou** `subaccountId` (senão 422). `GET /api/v1/transactions/{id}` para detalhe.

---

## 6. Catálogo de rotas (produto)

Auth em todas, salvo as marcadas **público**. Status típicos: 400 validação, 401 JWT, 403 RBAC, 404, 409 conflito/saldo, 422 regra de negócio, 429 login/refresh.

### Público

| METHOD | Path | Body | Sucesso |
|---|---|---|---|
| GET | `/api/v1/health` | — | 200 |
| POST | `/api/v1/users` | name, email, password, phone? | 201 |
| POST | `/api/v1/auth/login` | email, password, device*? | 200 |
| POST | `/api/v1/auth/refresh` | refreshToken | 200 |
| POST | `/api/v1/auth/logout` | refreshToken? | 204 |

### Auth autenticado

| METHOD | Path | Notas |
|---|---|---|
| POST | `/api/v1/auth/logout-all` | 204 |
| GET | `/api/v1/auth/me` | UserResponse produto |
| GET | `/api/v1/auth/sessions` | lista |
| DELETE | `/api/v1/auth/sessions/{id}` | 204 |

### Me (paginação `PageResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`)

| METHOD | Path |
|---|---|
| GET | `/api/v1/me` |
| GET | `/api/v1/me/dashboard` |
| GET | `/api/v1/me/accounts` |
| GET | `/api/v1/me/wallets` |
| GET | `/api/v1/me/transactions` |
| GET | `/api/v1/me/notifications` |

Query padrão: `page`, `size` (default 20, max 100), `sort=createdAt,desc`.

### Org / membros / roles

| METHOD | Path | Permissão |
|---|---|---|
| POST | `/api/v1/organizations` | JWT (sem membership) |
| GET | `/api/v1/organizations` | filtra `organization.read` |
| GET/PATCH | `/api/v1/organizations/{id}` | read / update |
| PATCH | `/api/v1/organizations/{id}/status` | update |
| GET | `/api/v1/organizations/{id}/status` | read |
| POST/GET | `/api/v1/organizations/{organizationId}/members` | manage / read |
| PATCH/DELETE | `.../members/{userId}` | manage |
| GET/PUT | `.../members/{userId}/roles` | read / manage |
| GET | `.../members/{userId}/permissions` | read (ou self) |
| GET | `/api/v1/roles` | JWT |
| GET | `/api/v1/permissions` | JWT |

### Contas / wallets / txs

| METHOD | Path | Permissão |
|---|---|---|
| POST/GET | `/api/v1/organizations/{organizationId}/accounts` | update / wallet.read |
| GET/PATCH | `/api/v1/accounts/{id}` | wallet.read / organization.update |
| GET | `/api/v1/accounts/{id}/wallet` | wallet.read |
| GET | `/api/v1/accounts/{id}/ledger-balance` | wallet.read |
| GET | `/api/v1/accounts/{accountId}/statement` | membership |
| GET | `/api/v1/wallets/{walletId}` | wallet.read |
| GET | `/api/v1/wallets/subaccount/{subaccountId}` | wallet.read |
| GET | `/api/v1/wallets/{walletId}/transactions` | transactions.read |
| GET | `/api/v1/transactions` | **req** walletId ou subaccountId |
| GET | `/api/v1/transactions/{transactionId}` | transactions.read |

### PIX Account (preferir no app)

| METHOD | Path | Header | Permissão |
|---|---|---|---|
| POST/GET | `/api/v1/pix/keys` | | create / read |
| DELETE | `/api/v1/pix/keys/{id}` | | pix.create |
| POST | `/api/v1/pix/transfers` | **Idempotency-Key** | pix.transfer |
| GET | `/api/v1/pix/transfers/{id}` | | pix.read |
| GET | `/api/v1/pix/transfers?accountId=` | | pix.read |
| POST | `/api/v1/pix/qr-codes` | | pix.create |
| POST | `/api/v1/pix/qr-codes/pay` | **Idempotency-Key** | pix.transfer — pay copia e cola da subconta (OWNER, FINANCE, EMPLOYEE, Account própria). Body: `accountId`, `payload`, `amount?`, `description?`. UI `/transferencias` detecta EMV colado na chave e envia o payload **com espaços**. Resposta: `id` (Asaas pix tx), `transactionId`, `pixTransactionId`, `status`, destinatário |
| GET | `/api/v1/pix/transactions/{id}?accountId=` | | pix.read — poll após pay QR |

### Beneficiários / aprovações / limites / notificações / audit

| METHOD | Path | Permissão |
|---|---|---|
| CRUD | `/api/v1/beneficiaries` | beneficiaries.* |
| POST/GET | `/api/v1/approvals/policies` | approval.create / read |
| GET | `/api/v1/approvals?accountId=` | approval.read |
| GET | `/api/v1/approvals/{id}` | approval.read |
| POST | `/api/v1/approvals/{id}/approve` | approval.approve |
| POST | `/api/v1/approvals/{id}/reject` | approval.reject |
| POST | `/api/v1/approvals/{id}/cancel` | requester ou approval.create |
| POST/GET/PATCH | `/api/v1/limits` | limits.manage / read |
| GET/POST | `/api/v1/notifications` (+ read, read-all, unread-count) | JWT |
| GET | `/api/v1/audit-logs?organizationId=` | audit.read |

### Trilho subconta (não usar no dashboard principal)

Só se você implementar um módulo avançado explícito de subconta Asaas:

- `POST /api/v1/deposits` — **Idempotency-Key**; body `subaccountId`, `amount`. **Não** atualiza `GET /accounts/{id}/wallet`.
- `POST /api/v1/withdraws` — **Idempotency-Key**; `wallet.transfer`.
- `POST /api/v1/transfers/internal` — `senderSubaccountId`, `receiverSubaccountId`.

PIX legado: `/api/v1/subaccounts/{subaccountId}/pix/...`.

---

## 7. DTOs essenciais (campos)

**LoginResponse:** `token`, `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `userId`, `name`, `email` (produto). Admin: `adminId`, `role` — rota única `/login` diferencia pelo campo `adminId`.

**MeResponse:** `id`, `name`, `email`, `phone`, `status`, `lastLoginAt`, `organizations[]` (`organizationId`, `legalName`, `tradeName`, `organizationStatus`, `membershipStatus`).

**DashboardResponse:** ver passo 4.

**AccountResponse:** campos anteriores + `onboardingStatus?`, `financialResourcesEnabled?`.

**Onboarding financeiro (produto):** endpoints em `/api/v1/asaas/onboarding/*` — wizard self-service CPF ou CNPJ. Após login, se `financialResourcesEnabled !== true`, abrir `FinancialSetupModal` → `/onboarding-financeiro`. `POST /accounts/{id}/asaas-subaccount` está **descontinuado** (422).

**Sandbox Asaas:** após submit o BE pode aprovar sandbox e sincronizar status. PIX/transfer exigem `financialResourcesEnabled=true` (onboarding `APPROVED`). Se docs pendentes, mostrar `onboardingUrl`.

**Criar membro:** `POST /organization/members` **sem** documento/CNPJ — titular faz onboarding após login.

**Webhooks:** `ASAAS_WEBHOOK_URL` é opcional em dev/sandbox. Sem URL, a subconta é criada sem webhooks inline; configure URL + painel Asaas quando for receber eventos de pagamento/transfer.

**WalletResponse:** `id`, `subaccountId?`, `accountId?`, `balance`, `currency`, `active`.

**TransactionResponse:** `id`, `walletId`, `organizationId`, `accountId`, `type`, `status`, `amount`, `currency`, `reference`, `description`, `asaasPaymentId`, `createdAt`, `updatedAt`, `completedAt`.

**AdminTransactionResponse** (extrato global admin): além dos campos de transação — `organizationName`, `accountName`, `ownerName`, `counterpartHint`. Timestamps em ISO; FE formata com `timeZone: "America/Sao_Paulo"`.

**PlatformAccountResponse:** `label`, `asaasMasterWalletId`, `balance`, `currency`, `updatedAt` (saldo Master Asaas).

**AdminOrganizationDetailResponse:** `{ organization, accounts[] }`.

**AdminOwnerResponse:** `userId`, `name`, `email`, `organizationId`, `membershipStatus`, `accountId`, `asaasBind`.

**PixTransferResponse:** `id`, `transactionId`, `accountId`, `amount`, `status`, `destinationPixKey`, `destinationPixKeyType`, `providerReference`, `description`, timestamps.

**NotificationResponse:** `id`, `userId`, `organizationId`, `type`, `title`, `message`, `data`, `resourceId`, `readAt`, `createdAt`.

### Enums

- `TransactionStatus`: `PENDING`, `PENDING_APPROVAL`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `REVERSED`
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_IN`, `TRANSFER_OUT`, `TRANSFER`, `PIX`, `PAYMENT`, `REFUND`, `FEE`
- `PixKeyType`: `CPF`, `CNPJ`, `EMAIL`, `PHONE`, `EVP` (criação de chave na Account: somente `EVP`)
- `AccountType`: `MAIN`, `EMPLOYEE`, `RESERVE`
- `AccountStatus`: `ACTIVE`, `SUSPENDED`, `CLOSED`
- `RoleCode`: `OWNER`, `FINANCE`, `EMPLOYEE` (ADMIN/AUDITOR descontinuados)
- `DocumentType`: `CNPJ` (Organization e subconta Asaas; CPF rejeitado no provision)
- `OrganizationStatus`: `ACTIVE`, `SUSPENDED`, `BLOCKED`
- `UserStatus`: `ACTIVE`, `SUSPENDED`
- `MembershipStatus`: `ACTIVE`, `INVITED`, `SUSPENDED`, `REMOVED`
- `PaymentOrderStatus`: `PENDING_APPROVAL`, `APPROVED`, `PROCESSING`, `COMPLETED`, `FAILED`, `REJECTED`, `CANCELLED`
- `LimitPeriod`: `PER_TRANSACTION`, `DAILY`, `MONTHLY`
- `LimitTransactionType`: `PIX`, `TRANSFER`, `WITHDRAWAL`, `PAYMENT`
- `BankAccountType`: `CHECKING`, `SAVINGS`

---

## 8. RBAC — esconder botões

`GET /me` **não** traz papéis. Use `GET /organizations/{orgId}/members/{userId}/permissions`.

| Permissão | OWNER | FINANCE | EMPLOYEE |
|---|---|---|---|
| organization.read / update | sim / sim | sim / | sim / |
| members.* | sim | | |
| wallet.read / transfer | sim / sim | sim / sim | sim / sim |
| transactions.read / create | sim / sim | sim / sim | sim / sim |
| pix.read / create / transfer | sim | sim | sim |
| beneficiaries.* | sim | sim | sim (read+create/update próprios) |
| payment_orders.create / read / cancel | sim | sim | |
| payment_orders.approve / reject | sim | | |
| profile.read / update | sim | sim | sim |
| limits.read / manage | sim | read | |
| audit.read | sim | | |

**Telas:**

- Todos com Account própria: dashboard, PIX (enviar), extrato, beneficiários, perfil.
- OWNER: equipe + PaymentOrders (aprovar) + limites + org.
- FINANCE: PaymentOrders (criar/cancelar); **sem** approve.
- EMPLOYEE: só operações pessoais; sem equipe e sem PaymentOrders.
- Ninguém vê saldo de outro usuário (nem OWNER).

---

## 9. Erros

Envelope:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "error": "Validation Failed",
  "message": "...",
  "path": "/api/v1/...",
  "traceId": "...",
  "fieldErrors": [{ "field": "password", "message": "..." }]
}
```

`rejectedValue` de senha/token **não** vem. Campos nulos omitidos.

Códigos: `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`, `INVALID_REQUEST`, `RATE_LIMITED`, `ASAAS_ERROR`, `INTERNAL_ERROR`.

Mostre `message` + `code`. Em 403, não revele se o recurso existe em outro tenant. PaymentOrder/saldo insuficiente → **409**.

### Paginação

- **PageResponse** (Me, statement): campo `page` (0-based).
- **Spring Page** (orgs, members, deposits, pix transfers, notifications, audit, `GET /transactions`): campo **`number`** (0-based), não `page`. Ambos têm `content`, `totalElements`, `totalPages`, `size`, `first`, `last`.

---

## 9b. Painel Admin (plataforma Theron)

Rotas UI: `/admin`, `/admin/organizacoes`, `/admin/organizacoes/[id]`, `/admin/carteira`, `/admin/extrato`. Entrada só por `/login` (APP_ADMIN).

| Método | Path | Uso |
|--------|------|-----|
| GET | `/api/v1/admin/me` | Perfil admin |
| GET/POST | `/api/v1/admin/organizations` | Listar / criar org |
| GET | `/api/v1/admin/organizations/{id}` | `{ organization, accounts[] }` — unwrap no FE |
| POST | `/api/v1/admin/organizations/{id}/owners` | Criar OWNER completo: user + membership OWNER + Account + Asaas. Body: `name`, `email`, `password`, `phone?`, `document`, `documentType` |
| POST | `/api/v1/admin/organizations/{id}/admin` | Legado: assign OWNER a `userId` já existente |
| GET | `/api/v1/admin/platform-account` | Carteira Master: `label`, `asaasMasterWalletId`, `balance` (Asaas), `currency` |
| GET | `/api/v1/admin/platform-account/balance-divergences` | Contas ACTIVE com diferença Asaas vs ledger (`divergedCount`, `items[]`) |
| GET/POST/DELETE | `/api/v1/admin/platform-account/pix/keys` | Chaves PIX Master (`ASAAS_API_KEY`). POST só `{ "type": "EVP" }`. Resposta: `id` (Asaas string), `type`, `key`, `status` — sem `accountId` |
| GET | `/api/v1/admin/platform-account/pix/keys/lookup?type&key` | Lookup automático no modal de envio PIX Master (Asaas external): `ownerName`, `ownerCpfCnpj`, `institutionName`, `institutionCode`, `ispb` — sem passo separado no formulário |
| POST | `/api/v1/admin/platform-account/pix/qr-codes` | QR estático Master (cobrança). Body: `pixKeyId`, `value` (obrigatório), `description?` |
| POST | `/api/v1/admin/platform-account/pix/qr-codes/pay` | Pay copia e cola Master. Body: `payload`, `amount`, `description?` + `Idempotency-Key`. **Pré-registra** em `platform_pix_transfer` antes do Asaas. Asaas valida com `type=PIX_QR_CODE` e `pixQrCode.id` = `id` da resposta (pix transaction id, **não** `transferId`). Sem registro → Asaas `REFUSED` / *Autorização externa foi recusada*. Resposta: `id`, `amount`, `status`, `providerStatus`, `transferId`, `refusalReason`, destinatário |
| GET | `/api/v1/admin/platform-account/pix/transactions/{id}` | Poll status PIX Asaas após pay (`PROCESSING` → `COMPLETED`) |
| GET | `/api/v1/pix/keys/lookup?accountId&type&key` | Idem no produto (modal de envio; apiKey da subconta; `pix.transfer` + bind ACTIVE) |
| GET/POST | `/api/v1/admin/platform-account/pix/transfers` | PIX Master por chave. POST body: `amount`, `destinationPixKey`, `destinationPixKeyType`, `description?` + header `Idempotency-Key`. Persistido em `platform_pix_transfer`; Asaas valida com `type=TRANSFER` + `transfer.id`. GET paginado. Resposta: `id` Asaas string, `amount`, `status`, destino — sem `accountId`. COMPLETED para chave Theron credita `TRANSFER_IN` local |
| POST | `/api/v1/webhooks/asaas/transfer-validation` | **Interno (Asaas → BE, não consumido pelo FE).** Token `ASAAS_TRANSFER_VALIDATION_TOKEN`. Tipos: `TRANSFER` (transfer por chave) ou `PIX_QR_CODE` (pay QR copia e cola). Resposta: `{ "status": "APPROVED" \| "REFUSED", "refuseReason"?: string }` |
| POST | `/api/v1/admin/platform-account/pix/reconcile-credits` | Backfill créditos locais de transfers Master COMPLETED sem `credit_transaction_id` → `{ credited }` |
| POST | `/api/v1/admin/payment-orders/{id}/sync` | Reconciliar ordem `PROCESSING` com `GET /transfers/{id}` Asaas; 404 → `FAILED` (sem auto-reverter ledger) |
| GET | `/api/v1/admin/transactions` | Extrato global paginado (`organizationId`, `accountId`, `from`, `to`, `type`, `status`) — `AdminTransactionResponse` |
| GET/PATCH | `/api/v1/admin/splits` | Config de split |

Logo: `public/brand/theron-mark.png` no `LogoMark`. Timestamps do extrato admin: `Intl` com `timeZone: "America/Sao_Paulo"`.

---

## 10. O que o frontend NÃO deve assumir

1. `POST /deposits` **não** credita a wallet da Account do dashboard/PIX.
2. Use `POST /accounts/{accountId}/asaas-subaccount` para criar/reparar o bind Asaas da própria Account. PIX 422 até `asaasStatus === ACTIVE`. No Sandbox, o BE aprova a subconta automaticamente após create (ou no retry se já existir `asaasAccountId`).
3. `POST /organizations` com JWT de produto → **403**.
4. `X-Actor-User-Id` é ignorado.
5. Sem CORS: só same-origin via rewrite.
6. PIX pessoal **não** passa por ApprovalPolicy / tela de aprovações genéricas.
7. Wallet/extrato/PIX: só Account `owner_user_id = eu`.
8. Relatórios: compose dashboard + extrato + (se `audit.read`) audit-logs.

---

## 11. Critérios de pronto

- Login/signup/refresh/logout funcionam contra o backend local via rewrite.
- Dashboard mostra saldo real de `GET /me/dashboard` da Account própria.
- Enviar PIX usa `Idempotency-Key` e **não** espera `PENDING_APPROVAL` por policy.
- Copia e cola em `/transferencias` funciona para OWNER, FINANCE e EMPLOYEE (não só Admin Master): colar EMV reconhece o payload, **preserva espaços** e chama `POST /pix/qr-codes/pay`. Destino sem opção Beneficiário.
- Maria (outra org) não vê contas de João (403 tratado).
- EMPLOYEE envia PIX da própria Account; não vê PaymentOrders.
- FINANCE cria PaymentOrder; OWNER aprova; FINANCE não aprova.
- Paleta ouro/prussian, sem roxo do mockup.
- Nenhum `any` solto; tipos gerados a partir desta spec.
- README do frontend: como subir Next (`npm run dev`) + backend na 8080.

Comece pelo Passo 0 e não pule o Passo 1 (health). Implemente tela a tela na ordem dos passos 2–12.
