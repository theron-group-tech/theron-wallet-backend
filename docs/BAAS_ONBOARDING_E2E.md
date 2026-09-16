# BaaS Onboarding — Teste E2E local

Branch: `refactor/baas-onboarding`

## Pré-requisitos

- Docker (PostgreSQL via `docker-compose.yml`)
- Java 21 + Maven **ou** Docker Maven (como no CI)
- Frontend sibling: `theron-wallet-frontend`
- Credenciais Asaas Sandbox (`ASAAS_API_KEY`, webhook token)

## Backend

```bash
cd theron-wallet-backend
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Migrations `V34` (asaas_onboarding) e `V35` (subaccount link) rodam no startup.

## Frontend

```bash
cd ../theron-wallet-frontend
npm install
npm run dev
```

Proxy/API base apontando para `http://localhost:8080`.

## Fluxo manual

1. **Platform Admin** cria Organization + Owner (`POST /admin/organizations`, `POST /admin/organizations/{id}/owner`) — **sem** subconta automática.
2. Owner faz login no produto → modal **Configure sua conta financeira**.
3. Wizard `/onboarding-financeiro`:
   - Tipo CPF ou CNPJ
   - Dados pessoais/empresa, endereço, renda
   - Submit → subconta criada no Asaas Sandbox
4. Se docs pendentes: botão **Continuar documentação no Asaas** (`onboardingUrl`).
5. Webhook `ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED` → `financialResourcesEnabled=true`.
6. Validar PIX (`/pix`), transferências (`/transferencias`), cobrança no dashboard.

## Fluxo PJ (CNPJ)

Repetir com `personType=COMPANY`, CNPJ válido, `companyType=LIMITED|MEI`.

## Retomada

Interromper no step Endereço, fazer logout/login → wizard continua em `FINANCIAL`.

## Idempotência

Dois cliques em Submit (ou retry com mesmo `Idempotency-Key`) não devem criar duas subcontas.

## Owner cria membro

`POST /organization/members` **sem** documento → membro faz onboarding próprio após login.

## Checklist

- [ ] Zero defaults Av. Paulista / telefone mock no payload Asaas
- [ ] `apiKey` ausente em responses e logs
- [ ] Legacy `POST /accounts/{id}/asaas-subaccount` retorna 422
- [ ] Gates PIX bloqueiam até `APPROVED`
- [ ] Pay QR copia e cola Master aprovado via `transfer-validation` (`PIX_QR_CODE`)
