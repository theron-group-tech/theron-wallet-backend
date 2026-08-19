# Prompt — Frontend Theron Wallet

Cole este documento inteiro em um agente de código num **repositório frontend separado**. Não altere o backend. Não invente rotas, campos nem comportamentos que não estejam aqui.

Você é um engenheiro frontend sênior. Sua tarefa é **criar do zero** o app web/mobile-responsive **Theron Wallet**, consumindo a API já existente do backend Java (`theron-wallet-backend`, Spring Boot, porta `8080`, prefixo `/api/v1`).

---

## 1. Papel e restrições

- Stack **obrigatória:** Next.js (App Router) + TypeScript + Tailwind CSS.
- Idioma da UI: **português (Brasil)**. Códigos e `message` da API vêm em **inglês** — mostre `message` ao usuário; não traduza `code`.
- Autenticação: JWT de **usuário de produto**. Nunca use o fluxo admin (`adminId` / `role` no login).
- Ator: somente o Bearer. **Não envie** `X-Actor-User-Id`.
- O backend **não tem CORS**. O browser não pode chamar `localhost:8080` direto. Use **rewrite/proxy** do Next.
- Não implemente webhooks Asaas. Não chame `GET /api/v1/subaccounts` (sempre 403).
- Não invente endpoint de “creditar conta”, bind Asaas ou “tornar OWNER ao criar org”.

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
  (app)/aprovacoes/page.tsx
  (app)/beneficiarios/page.tsx
  (app)/equipe/page.tsx
  (app)/limites/page.tsx
  (app)/relatorios/page.tsx
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
- Header **`Idempotency-Key`**: UUID **obrigatório** em `POST /deposits`, `POST /withdraws`, `POST /pix/transfers`. Opcional em `POST /transfers/internal` e approve/reject/cancel.
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
| Dashboard | `GET /me/dashboard?accountId=` | Saldo, entradas/saídas hoje, bloqueado, pendentes, recentes |
| Contas | `GET /organizations/{id}/accounts` + `GET /accounts/{id}/wallet` | MAIN / EMPLOYEE / RESERVE |
| PIX | `GET/POST /pix/keys`, QR `POST /pix/qr-codes` | Precisa `pix.create` para criar |
| Transferências | `POST /pix/transfers` | Preferir PIX de **Account**, não depósito de subconta |
| Extrato | `GET /accounts/{id}/statement` ou `GET /me/transactions` | Filtros: from, to, type, status, min/max |
| Aprovações | `GET /approvals?accountId=` | Aprovar/rejeitar; badge no menu = count PENDING |
| Beneficiários | CRUD `/beneficiaries` | PIX ou dados bancários |
| Equipe | members + `PUT .../roles` | `members.manage` |
| Limites | `/limits` | `limits.read` / `limits.manage` |
| Relatórios | **agregar** dashboard + extrato | Sem endpoint novo |
| Configurações | `PATCH /users/{id}` (self) + `PATCH /organizations/{id}` se `organization.update` | |

Quick actions do dashboard: Nova transação / Enviar PIX / Adicionar beneficiário — só se a permissão existir. “Cobrar” = criar QR PIX (`POST /pix/qr-codes`) se `pix.create`.

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
   200: use **`accessToken`** (alias `token` é o mesmo JWT). Guarde `refreshToken`, `userId`, `name`, `email`, `expiresIn` (ms, access ~15 min).  
   Se a resposta tiver `adminId`, **não** entre no app de produto.  
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
- `GET /api/v1/accounts/{id}/ledger-balance` → conferência.

**Não** use `POST /deposits` para encher este saldo. Depósito credita wallet de **subconta**, outro trilho.

### Passo 6 — PIX (Account)

Exige Account com subconta Asaas **já vinculada no banco** (não há HTTP de bind). 422 = mostrar `message`, não fingir sucesso.

- `GET /api/v1/pix/keys?accountId=` (`pix.read`)
- `POST /api/v1/pix/keys` `{ "accountId", "type": "CPF"|"CNPJ"|"EMAIL"|"PHONE"|"EVP" }` (`pix.create`) → 201; chave gerada no Asaas.
- `DELETE /api/v1/pix/keys/{id}` → 204
- `POST /api/v1/pix/qr-codes` `{ "accountId", "pixKeyId", "value?", "description?" }`
- `POST /api/v1/pix/transfers` header `Idempotency-Key`  
  Body: `{ "accountId", "amount", "beneficiaryId" }` **ou** `{ "destinationPixKey", "destinationPixKeyType" }`  
  Perm `pix.transfer`. 201 com `status`: `PROCESSING` **ou** `PENDING_APPROVAL`.  
  Mesma key + mesmo body → mesma operação. Mesma key + body diferente → 409.

### Passo 7 — Beneficiários

- `POST /api/v1/beneficiaries` `{ "organizationId", "name", "pixKey", "pixKeyType" }` ou dados bancários (`bankCode`, `branch`, `account`, `accountType`: `CHECKING`|`SAVINGS`).
- `GET /api/v1/beneficiaries?organizationId=` (query obrigatória)
- GET/PATCH/DELETE por id. DELETE = inativação (204).

### Passo 8 — Aprovações

Políticas (OWNER/FINANCE, `approval.create`):

`POST /api/v1/approvals/policies` `{ "accountId", "amountMin", "amountMax?", "requiredApprovals" }`

Lista: `GET /api/v1/approvals?accountId=`

- `POST /api/v1/approvals/{id}/approve` body `{}` ou `{ "comment" }` — **requester não pode se auto-aprovar** (403).
- `POST .../reject` `{ "comment?" }`
- `POST .../cancel` (requester)

Após approve completo, a tx vai a `PROCESSING` (Asaas). O frontend não confirma dinheiro; o webhook é servidor. Faça poll em `GET /pix/transfers/{id}` ou no extrato até `COMPLETED` / `FAILED`.

### Passo 9 — Equipe

- `GET /api/v1/organizations/{organizationId}/members`
- `POST .../members` `{ "userId" }` — entra como **EMPLOYEE**. O usuário precisa já existir (`POST /users` é público).
- `PUT .../members/{userId}/roles` `{ "roleCodes": ["FINANCE"] }` — só OWNER concede OWNER.
- `GET .../members/{userId}/roles` e `/permissions`
- `PATCH .../members/{userId}` `{ "status" }`; `DELETE` = REMOVED.

Catálogo: `GET /api/v1/roles`, `GET /api/v1/permissions`.

**Criar organização:** `POST /api/v1/organizations` `{ "legalName", "document", "documentType": "CNPJ"|"CPF", "tradeName?" }` **não** adiciona membership nem OWNER. Não prometa “você é dono” após o 201. Equipe/OWNER só existe se o backend já tiver membership (seed/ops). A UI deve tratar org sem membership: 403 e copy honesta.

### Passo 10 — Limites

- `POST /api/v1/limits` `{ "organizationId", "transactionType": "PIX"|"TRANSFER"|"WITHDRAWAL"|"PAYMENT", "period": "PER_TRANSACTION"|"DAILY"|"MONTHLY", "maxAmount", "accountId?", "userId?", "roleId?" }`
- `GET /api/v1/limits?organizationId=`
- `PATCH /api/v1/limits/{id}` `{ "maxAmount?", "enabled?" }`

Há também limite por conta (`account_limit`) provisionado no backend, sem CRUD HTTP dedicado neste catálogo. PIX sem limite de conta → 422.

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

**LoginResponse:** `token`, `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `userId`, `name`, `email` (produto). Admin: `adminId`, `role` — ignore.

**MeResponse:** `id`, `name`, `email`, `phone`, `status`, `lastLoginAt`, `organizations[]` (`organizationId`, `legalName`, `tradeName`, `organizationStatus`, `membershipStatus`).

**DashboardResponse:** ver passo 4.

**AccountResponse:** `id`, `organizationId`, `name`, `type`, `status`, `currency`, timestamps.

**WalletResponse:** `id`, `subaccountId?`, `accountId?`, `balance`, `currency`, `active`.

**TransactionResponse:** `id`, `walletId`, `organizationId`, `accountId`, `type`, `status`, `amount`, `currency`, `reference`, `description`, `asaasPaymentId`, `createdAt`, `updatedAt`, `completedAt`.

**PixTransferResponse:** `id`, `transactionId`, `accountId`, `amount`, `status`, `destinationPixKey`, `destinationPixKeyType`, `providerReference`, `description`, timestamps.

**NotificationResponse:** `id`, `userId`, `organizationId`, `type`, `title`, `message`, `data`, `resourceId`, `readAt`, `createdAt`.

### Enums

- `TransactionStatus`: `PENDING`, `PENDING_APPROVAL`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `REVERSED`
- `TransactionType`: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_IN`, `TRANSFER_OUT`, `TRANSFER`, `PIX`, `PAYMENT`, `REFUND`, `FEE`
- `PixKeyType`: `CPF`, `CNPJ`, `EMAIL`, `PHONE`, `EVP`
- `AccountType`: `MAIN`, `EMPLOYEE`, `RESERVE`
- `AccountStatus`: `ACTIVE`, `SUSPENDED`, `CLOSED`
- `RoleCode`: `OWNER`, `ADMIN`, `FINANCE`, `EMPLOYEE`, `AUDITOR`
- `DocumentType`: `CPF`, `CNPJ`
- `OrganizationStatus`: `ACTIVE`, `SUSPENDED`, `BLOCKED`
- `UserStatus`: `ACTIVE`, `SUSPENDED`
- `MembershipStatus`: `ACTIVE`, `INVITED`, `SUSPENDED`, `REMOVED`
- `ApprovalRequestStatus`: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`
- `LimitPeriod`: `PER_TRANSACTION`, `DAILY`, `MONTHLY`
- `LimitTransactionType`: `PIX`, `TRANSFER`, `WITHDRAWAL`, `PAYMENT`
- `BankAccountType`: `CHECKING`, `SAVINGS`

---

## 8. RBAC — esconder botões

`GET /me` **não** traz papéis. Use `GET /organizations/{orgId}/members/{userId}/permissions`.

| Permissão | OWNER | ADMIN | FINANCE | EMPLOYEE | AUDITOR |
|---|---|---|---|---|---|
| organization.read | sim | sim | sim | sim | sim |
| organization.update | sim | sim | | | |
| members.read | sim | sim | | | sim |
| members.manage | sim | sim | | | |
| wallet.read | sim | sim | sim | sim | sim |
| wallet.transfer | sim | | sim | | |
| transactions.read | sim | sim | sim | sim | sim |
| transactions.create | sim | | sim | | |
| pix.read | sim | sim | sim | sim | sim |
| pix.create | sim | | sim | | |
| pix.transfer | sim | | sim | | |
| beneficiaries.read | sim | sim | sim | sim | sim |
| beneficiaries.create/update/delete | sim | sim | sim | | |
| audit.read | sim | sim | | | sim |
| limits.read | sim | sim | sim | | sim |
| limits.manage | sim | sim | | | |
| approval.read | sim | sim | sim | | sim |
| approval.create | sim | | sim | | |
| approval.approve / reject | sim | | sim | | |

**Telas:**

- Todos autenticados com `wallet.read`: dashboard, contas (leitura), extrato, PIX (leitura), beneficiários (leitura), inbox.
- OWNER: tudo. Único que concede OWNER.
- ADMIN: org, contas, equipe, limites CRUD, auditoria, beneficiários. **Sem** enviar PIX, depositar, sacar, transferir, criar política, aprovar.
- FINANCE: PIX (criar/enviar), transferir, beneficiários CRUD, políticas, **aprovar/rejeitar**. Sem equipe, sem `limits.manage`, sem auditoria, sem editar org.
- EMPLOYEE: só leitura operacional.
- AUDITOR: todos os `*.read`. Sem mutação.

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

Mostre `message` + `code`. Em 403, não revele se o recurso existe em outro tenant.

### Paginação

- **PageResponse** (Me, statement): campo `page` (0-based).
- **Spring Page** (orgs, members, deposits, pix transfers, notifications, audit, `GET /transactions`): campo **`number`** (0-based), não `page`. Ambos têm `content`, `totalElements`, `totalPages`, `size`, `first`, `last`.

---

## 10. O que o frontend NÃO deve assumir

1. `POST /deposits` **não** credita a wallet da Account do dashboard/PIX.
2. **Não há** HTTP para vincular subconta Asaas à Account. PIX 422 até ops/seed ligar no banco — mostre o erro.
3. `POST /organizations` **não** cria membership nem OWNER.
4. `X-Actor-User-Id` é ignorado.
5. Sem CORS: só same-origin via rewrite.
6. Dois mundos: Account (`/pix`, `/accounts`, `/me`) vs subconta (`/deposits`, `/withdraws`, `/transfers/internal`). O app do mockup vive no mundo **Account**.
7. Criar Account **não** provisiona Asaas.
8. Relatórios: sem API nova — compose dashboard + extrato + (se `audit.read`) audit-logs.

---

## 11. Critérios de pronto

- Login/signup/refresh/logout funcionam contra o backend local via rewrite.
- Dashboard mostra saldo real de `GET /me/dashboard`.
- Enviar PIX usa `Idempotency-Key` e trata `PENDING_APPROVAL`.
- Maria (outra org) não vê contas de João (403 tratado).
- EMPLOYEE não vê botão Enviar PIX.
- FINANCE vê Aprovar; requester não aprova a própria tx.
- Paleta ouro/prussian, sem roxo do mockup.
- Nenhum `any` solto; tipos gerados a partir desta spec.
- README do frontend: como subir Next (`npm run dev`) + backend na 8080.

Comece pelo Passo 0 e não pule o Passo 1 (health). Implemente tela a tela na ordem dos passos 2–12.
