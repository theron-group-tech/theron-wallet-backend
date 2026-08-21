# Theron Wallet Backend

Spring Boot API on port `8080` with prefix `/api/v1`.

**Regras de negócio oficiais:** [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md)

## Run locally

1. Start Postgres (`docker compose up postgres` or the full stack).
2. Copy environment variable **names** below into `.env` (never commit real secrets).
3. `mvn spring-boot:run` or `docker compose up --build`.

## Domain model

```
PLATFORM OWNER → Platform Account → Asaas MASTER
                      ↓
                 Organization (tenant only — no collective wallet)
                      ↓
              OWNER / FINANCE / EMPLOYEE
                      ↓
         each: Account → Wallet → Asaas Subaccount
```

| Actor | JWT | Can create Organization? | Scope |
|---|---|---|---|
| Platform owner | `adminId` | Yes — `POST /api/v1/admin/organizations` | `/api/v1/admin/**` |
| OWNER | product `userId` | No | Org admin + own Account; approves PaymentOrders |
| FINANCE | product `userId` | No | Own Account; creates/cancels PaymentOrders |
| EMPLOYEE | product `userId` | No | Own Account only (personal PIX/transfer) |

`POST /api/v1/organizations` with a product JWT returns **403**.

Personal PIX does **not** require ApprovalPolicy. Administrative payroll uses **PaymentOrder** (FINANCE creates → OWNER approves; source = OWNER Account).

## Environment variable names

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` (aliases: `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD`)
- `ASAAS_API_BASE_URL`, `ASAAS_API_KEY` (master key — server only)
- `ASAAS_MASTER_WALLET_ID` (Platform Account / split destination)
- `ASAAS_WEBHOOK_TOKEN`, `ASAAS_WEBHOOK_URL`
- `ENCRYPTION_AES_KEY`
- `PLATFORM_SPLIT_PERCENT`, `PLATFORM_SPLIT_FIXED_AMOUNT`, `PLATFORM_SPLIT_ENABLED` (defaults: 0 / 0 / true)
- `ASAAS_SUBACCOUNT_DEFAULT_*` (KYC placeholders for sandbox provisioning)

Do not put `ASAAS_API_KEY` in a frontend `.env`.

## Asaas split

Configured only by platform owner (`GET/PATCH /api/v1/admin/splits`). Starts at **0% enabled**. Applied on Asaas **payment/cobrança** only — not on static PIX QR or PIX transfers.

## Wallets

`GET /accounts/{id}/wallet` is the product balance (own Account only). `POST /deposits` is a legacy rail.

## Tests

`mvn test` (Postgres `theron_wallet_test` on localhost). Asaas is mocked.
