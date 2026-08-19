# Theron Wallet Backend

Spring Boot API on port `8080` with prefix `/api/v1`.

## Run locally

1. Start Postgres (`docker compose up postgres` or the full stack).
2. Copy environment variable **names** below into `.env` (never commit real secrets).
3. `mvn spring-boot:run` or `docker compose up --build`.

## Actors

| Actor | JWT | Can create Organization? | Scope |
|---|---|---|---|
| Platform admin | `adminId` | Yes — `POST /api/v1/admin/organizations` | `/api/v1/admin/**` |
| Organization admin | product `userId` + membership `OWNER` | No | Own organization |
| Employee | product `userId` + `EMPLOYEE` | No | Own Account only |

`POST /api/v1/organizations` with a product JWT returns **403**. The platform admin assigns the org admin with `POST /api/v1/admin/organizations/{id}/admin`.

The org admin then creates **their own** Account (`POST /api/v1/organization/accounts`). That call provisions an Asaas **subaccount child of the MASTER** (`ASAAS_API_KEY`). After the bind is `ACTIVE`, they can onboard employees with `POST /api/v1/organization/members` (user + membership + Account + wallet + Asaas subaccount). Subaccounts are **never shared**.

## Environment variable names

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` (aliases: `DEFAULT_ADMIN_EMAIL` / `DEFAULT_ADMIN_PASSWORD`)
- `ASAAS_API_BASE_URL`, `ASAAS_API_KEY` (master key — server only)
- `ASAAS_MASTER_WALLET_ID` (wallet that receives platform split; do not invent a value)
- `ASAAS_WEBHOOK_TOKEN`, `ASAAS_WEBHOOK_URL`
- `ENCRYPTION_AES_KEY`
- `PLATFORM_SPLIT_PERCENT`, `PLATFORM_SPLIT_FIXED_AMOUNT`, `PLATFORM_SPLIT_ENABLED`
- `ASAAS_SUBACCOUNT_DEFAULT_MOBILE`, `ASAAS_SUBACCOUNT_DEFAULT_POSTAL_CODE`, `ASAAS_SUBACCOUNT_DEFAULT_ADDRESS`, `ASAAS_SUBACCOUNT_DEFAULT_ADDRESS_NUMBER`, `ASAAS_SUBACCOUNT_DEFAULT_PROVINCE`, `ASAAS_SUBACCOUNT_DEFAULT_INCOME_VALUE`, `ASAAS_SUBACCOUNT_DEFAULT_COMPANY_TYPE`, `ASAAS_SUBACCOUNT_DEFAULT_BIRTH_DATE`

Do not put `ASAAS_API_KEY` in a frontend `.env`. API responses never include Asaas keys.

## Asaas split

Platform split is configured only by the platform admin (`GET/PATCH /api/v1/admin/splits`) and stored in `platform_split_config`.

It is sent on Asaas **payment/cobrança** (`POST /payments`, used by the legacy deposit flow). **Static PIX QR** and **PIX transfers** do not receive a `split` field because those Asaas endpoints do not support it.

## Wallets

`GET /accounts/{id}/wallet` is the product dashboard balance (local Account wallet + ledger). `POST /deposits` credits a **legacy** subaccount wallet and is not the dashboard rail.

## Tests

`mvn test` (Postgres `theron_wallet_test` on localhost). Asaas is mocked.
