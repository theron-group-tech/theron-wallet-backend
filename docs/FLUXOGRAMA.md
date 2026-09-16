# Fluxograma do Sistema Theron Wallet (estado atual)

Fonte canônica de domínio: [`BUSINESS_RULES.md`](BUSINESS_RULES.md). Atualizado com controllers e implementação de Payment Order (provider-first Asaas).

---

## 1. Hierarquia e modelo de dados

```mermaid
flowchart TB
  subgraph platform [Plataforma]
    PO[PlatformOwner]
    PA[PlatformAccount]
    AM[AsaasMaster]
    PO --> PA
    PA --> AM
  end

  subgraph tenant [Tenant]
    ORG[Organization_CNPJ]
    MEM[Membership_ACTIVE]
    ROLE[Role_OWNER_FINANCE_EMPLOYEE]
    ORG --> MEM
    MEM --> ROLE
  end

  subgraph financial [Recursos financeiros]
    ACC[Account_individual]
    WAL[Wallet_ledger]
    SUB[AsaasSubaccount]
    ACC --> WAL
    ACC --> SUB
  end

  PO -->|cria_org_e_OWNER| ORG
  ROLE --> ACC
  AM -->|splits| PA
  SUB -->|apiKey| AM
```

**Regra central:** Organization **não tem saldo**. Dinheiro pertence à **Account individual** (1 user × 1 org × 1 Account × 1 Wallet × 1 Subconta Asaas).

---

## 2. Atores, autenticação e autorização

```mermaid
flowchart LR
  LOGIN[POST_auth_login] --> CHECK{Credenciais}
  CHECK -->|produto| JWT_USER[JWT userId]
  CHECK -->|admin| JWT_ADMIN[JWT adminId]

  JWT_USER --> PROD["/api/v1/produto/**"]
  JWT_ADMIN --> ADMIN["/api/v1/admin/**"]

  PROD --> ME[GET_me]
  ME --> ORGS[organizations]
  ORGS --> PERMS["GET .../members/{userId}/permissions"]
  PERMS --> RBAC{permission.includes?}
  RBAC -->|403| BLOCK[Bloqueio]
  RBAC -->|ok| ROUTE[Rota permitida]
```

| Ator | Escopo | Frontend |
|------|--------|----------|
| Platform Owner | Admin plataforma, orgs, Master PIX, splits | `/admin` |
| OWNER | Org admin + própria Account + aprova PaymentOrder | `/dashboard`, `/equipe`, `/ordens-pagamento` |
| FINANCE | Própria Account + cria PaymentOrder | `/ordens-pagamento` |
| EMPLOYEE | Só operações pessoais (PIX, extrato) | `/pix`, `/transferencias` |

---

## 3. Provisionamento (onboarding)

```mermaid
sequenceDiagram
  participant Admin as PlatformOwner
  participant BE as Backend
  participant Asaas as Asaas_API
  participant Owner as OWNER_produto

  Admin->>BE: POST /admin/organizations
  Admin->>BE: POST /admin/organizations/{id}/owners
  BE->>BE: User + Membership OWNER + Account + Wallet
  BE->>Asaas: POST /accounts (subconta CNPJ org)
  Asaas-->>BE: apiKey + walletId

  Owner->>BE: POST /organization/members (FINANCE/EMPLOYEE)
  BE->>BE: User + Membership + Role + Account + Wallet
  BE->>Asaas: POST /accounts (CNPJ próprio MEI/filial)
  Asaas-->>BE: apiKey + walletId

  Note over Owner,BE: Bind idempotente; sandbox chama approve + sync status
```

**Saldo exibido na UI:** Asaas `GET /finance/balance` (fonte de verdade para o usuário). `wallet.balance` / `ledgerBalance` = espelho interno para auditoria.

---

## 4. PIX pessoal (sem aprovação)

```mermaid
flowchart TD
  START[Usuario EMPLOYEE_OWNER_FINANCE] --> OWN{Account propria?}
  OWN -->|nao| F403[403]
  OWN -->|sim| BIND{Subconta ACTIVE?}
  BIND -->|nao| F422[422 ASAAS_ERROR]
  BIND -->|sim| LIMIT[Validar limites account_limit]
  LIMIT --> BAL[Validar saldo Asaas + ledger]
  BAL --> IDEM[Header Idempotency-Key]
  IDEM --> ASAAS[Asaas POST PIX transfer]
  ASAAS --> LOCAL[Debit wallet + ledger + Transaction]
  LOCAL --> WH{Webhook TRANSFER_*}
  WH -->|DONE| COMP[COMPLETED]
  WH -->|FAILED| FAIL[FAILED]
```

- Chaves PIX criadas: só **EVP** (aleatória).
- Lookup destino: `GET /pix/keys/lookup` (modal de envio).
- **Sem** ApprovalPolicy para PIX pessoal.

---

## 5. Payment Order (folha / pagamento administrativo)

```mermaid
sequenceDiagram
  participant FIN as FINANCE
  participant OWN as OWNER_aprovador
  participant BE as PaymentOrderService
  participant Asaas as Asaas_API
  participant Local as Wallet_Ledger

  FIN->>BE: POST /payment-orders
  BE->>BE: status PENDING_APPROVAL, sourceAccountId null
  Note over BE: Nao debita, nao exige saldo

  OWN->>BE: POST /payment-orders/{id}/approve
  BE->>BE: source = Account do OWNER que aprova
  BE->>BE: validar saldo Asaas + ledger
  BE->>Asaas: POST /transfers walletId destino
  Asaas-->>BE: id + status
  BE->>Asaas: GET /transfers/{id} mesma apiKey origem
  alt status DONE
    BE->>Local: debit origem / credit destino + ledger
    BE->>BE: status COMPLETED
  else PENDING ou 404
    BE-->>OWN: 422 sem alterar ledger
  end
```

**Estados:** `PENDING_APPROVAL` → `COMPLETED` | `FAILED` | `REJECTED` | `CANCELLED`

**Legado:** ordens em `PROCESSING` → webhook `TRANSFER_*` ou `POST /admin/payment-orders/{id}/sync`

**Regras:**

- FINANCE cria/cancela; OWNER aprova/rejeita
- Criador **não** auto-aprova
- Saldo insuficiente no approve → **409**

---

## 6. Transferência interna (Theron → Theron)

```mermaid
flowchart LR
  A[Account_A] -->|POST /transfers/internal| BE[InternalTransferService]
  BE --> ASAAS_A[Asaas debit subconta A]
  BE --> LOCAL[Wallet + ledger A e B]
  BE --> TX_B[TRANSFER_IN em B via externalReference]
  ASAAS_A --> WH[Webhook confirma]
```

Crédito local do destinatário vinculado via `externalReference = senderTx.id`.

---

## 7. Platform Account (Master Asaas)

```mermaid
flowchart TD
  ADMIN[PlatformOwner] --> KEYS[GET/POST platform-account/pix/keys EVP]
  ADMIN --> QRGEN[POST platform-account/pix/qr-codes]
  ADMIN --> QRPAY[POST platform-account/pix/qr-codes/pay]
  ADMIN --> SEND[POST platform-account/pix/transfers]
  QRPAY --> PREREG[Pre-registro platform_pix_transfer]
  SEND --> PERSIST[platform_pix_transfer]
  PREREG --> ASAAS_PAY[Asaas pay QR]
  PERSIST --> ASAAS_TRF[Asaas transfer chave]
  ASAAS_PAY --> VAL_QR["transfer-validation PIX_QR_CODE"]
  ASAAS_TRF --> VAL_TRF["transfer-validation TRANSFER"]
  VAL_QR -->|APPROVED| WH[Webhook TRANSFER_*]
  VAL_TRF -->|APPROVED| WH
  WH -->|destino chave Theron| CREDIT[TRANSFER_IN local + wallet]
  WH --> RECON[POST reconcile-credits backfill]
```

---

## 8. Webhooks Asaas (sincronização)

```mermaid
flowchart TD
  ASAAS[Asaas] --> WH_IN[POST /webhooks/asaas]
  WH_IN --> TYPE{Tipo evento}

  TYPE -->|PAYMENT_*| PAY[Atualiza cobranca/deposito]
  TYPE -->|TRANSFER_*| TRF[Atualiza Transaction]
  TRF --> PO{reference payment-order:*?}
  PO -->|sim PROCESSING| SYNC[Atualiza PaymentOrder COMPLETED/FAILED]
  PO -->|nao| STD[Fluxo padrao transfer]

  TYPE -->|transfer-validation| VAL_TYPE{Tipo payload}
  VAL_TYPE -->|TRANSFER| VAL_TRF[Lookup transfer.id em platform_pix_transfer]
  VAL_TYPE -->|PIX_QR_CODE| VAL_QR[Lookup pixQrCode.id em platform_pix_transfer]
  VAL_TRF --> DECIDE{Registro pendente?}
  VAL_QR --> DECIDE
  DECIDE -->|sim| APPROVED[APPROVED]
  DECIDE -->|nao| REFUSED[REFUSED]
```

---

## 9. Frontend (Next.js) — visão de navegação

```mermaid
flowchart TB
  LOGIN[login] --> SESSION{Sessao}
  SESSION -->|adminId| ADMIN_UI["/admin"]
  SESSION -->|userId| APP["/(app)"]

  APP --> DASH[dashboard saldo Asaas + ledgerBalance]
  APP --> PIX[pix / transferencias]
  APP --> ORD[ordens-pagamento FINANCE/OWNER]
  APP --> EQ[equipe OWNER]
  APP --> CONT[contas propria]
  APP --> EXT[extrato proprio]

  ORD --> PO_FLOW[PaymentOrder flow acima]
  PIX --> PIX_FLOW[PIX pessoal flow acima]
```

Proxy Next.js: `/api/v1/*` → backend `:8080` (same-origin, sem CORS).

---

## 10. Fluxo-mãe (resumo executivo)

```mermaid
flowchart TD
  A[PlatformOwner cria Organization] --> B[Define OWNER inicial + Account + Asaas]
  B --> C[OWNER cria FINANCE e EMPLOYEE]
  C --> D[Cada um opera so a propria Account]

  D --> E[EMPLOYEE/FINANCE: PIX pessoal direto]
  D --> F[FINANCE: PaymentOrder PENDING]
  F --> G[OWNER aprova: Asaas transfer DONE + ledger]
  G --> H[Destino ve saldo Asaas subir]

  D --> I[PlatformOwner: PIX Master + splits + auditoria divergencias]
```

---

## Referências no código

| Fluxo | Arquivos principais |
|-------|---------------------|
| Payment Order | `PaymentOrderServiceImpl.java`, `PaymentOrderController.java` |
| PIX pessoal | `PixServiceImpl.java` |
| Webhooks | `WebhookServiceImpl.java`, `WebhookController.java` |
| Platform Master | `AdminPlatformController.java`, `PlatformPixServiceImpl.java` |
| Saldo / divergência | `AsaasBalanceServiceImpl.java`, `MeServiceImpl.java` |
| RBAC | `ResourceAuthorization.java`, seed Flyway V28 |
