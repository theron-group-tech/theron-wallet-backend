# BaaS Onboarding — Deploy Railway (staging)

Branch de deploy: **`refactor/baas-onboarding`** (não mergear em `master` até aprovação).

## 1. Serviço Backend

1. No Railway, altere **Source Branch** para `refactor/baas-onboarding`.
2. Variáveis obrigatórias (além das existentes):
   - `ASAAS_API_KEY` — Sandbox ou produção
   - `ASAAS_WEBHOOK_URL` — `https://<seu-app>.up.railway.app/api/v1/webhooks/asaas`
   - `ASAAS_WEBHOOK_TOKEN` — token compartilhado com painel Asaas
   - `ASAAS_TRANSFER_VALIDATION_URL` — `https://<seu-app>.up.railway.app/api/v1/webhooks/asaas/transfer-validation`
   - `ASAAS_TRANSFER_VALIDATION_TOKEN` — token de validação de saque no painel Asaas Master
   - `ASAAS_AUTO_APPROVE_SUBACCOUNTS=true` — apenas Sandbox
   - `ENCRYPTION_AES_KEY` — chave AES para apiKey das subcontas

3. Deploy → Flyway aplica `V34` e `V35` automaticamente.

## 2. Webhooks Asaas

No painel Asaas (conta pai), registrar URL acima com eventos:

- `PAYMENT_*`, `TRANSFER_*` (existentes)
- Todos os eventos de **Situação da conta** (`ACCOUNT_STATUS_*`), conforme painel Asaas:
  - `ACCOUNT_STATUS_BANK_ACCOUNT_INFO_*`
  - `ACCOUNT_STATUS_COMMERCIAL_INFO_*`
  - `ACCOUNT_STATUS_DOCUMENT_*`
  - `ACCOUNT_STATUS_GENERAL_APPROVAL_*`

Header/token conforme `ASAAS_WEBHOOK_TOKEN`.

**Validação de saque (PIX Master):** no painel Asaas Master, configurar URL de validação = `ASAAS_TRANSFER_VALIDATION_URL` e token = `ASAAS_TRANSFER_VALIDATION_TOKEN`. Pay QR copia e cola usa `type=PIX_QR_CODE`; transfer por chave usa `type=TRANSFER`.

## 3. Frontend

Deploy do frontend apontando `NEXT_PUBLIC_API_URL` para o backend Railway na mesma branch (`refactor/baas-onboarding` no repo frontend).

## 4. Smoke test pós-deploy

1. Criar Owner via admin API.
2. Login produto → modal onboarding.
3. Completar wizard CPF ou CNPJ.
4. Verificar `GET /api/v1/asaas/subaccount/status` → `financialResourcesEnabled`.
5. Criar chave PIX EVP.
6. Admin → pay QR copia e cola (ex. R$ 50) → logs devem mostrar `type=PIX_QR_CODE` + `APPROVED` (sem `TRANSFER_FAILED` subsequente).

## 5. Rollback

Reverter branch Railway para `master` **somente** após merge aprovado e migrations compatíveis.

Subcontas legadas (`legacy_auto_provisioned=true`) continuam operando na master antiga; novos usuários exigem onboarding.
