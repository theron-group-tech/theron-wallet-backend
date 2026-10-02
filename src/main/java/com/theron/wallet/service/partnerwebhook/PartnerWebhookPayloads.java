package com.theron.wallet.service.partnerwebhook;

import com.theron.wallet.entity.Charge;
import com.theron.wallet.entity.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PartnerWebhookPayloads {

    private PartnerWebhookPayloads() {
    }

    public static Map<String, Object> fromTransaction(Transaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (transaction == null) {
            return data;
        }
        data.put("transactionId", transaction.getId() != null ? transaction.getId().toString() : null);
        data.put("type", transaction.getType() != null ? transaction.getType().name() : null);
        data.put("status", transaction.getStatus() != null ? transaction.getStatus().name() : null);
        data.put("amount", transaction.getAmount() != null ? transaction.getAmount().toPlainString() : null);
        data.put("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "BRL");
        data.put("asaasPaymentId", transaction.getAsaasPaymentId());
        data.put("externalReference", transaction.getExternalReference());
        data.put("description", transaction.getDescription());
        if (transaction.getAccount() != null && transaction.getAccount().getId() != null) {
            data.put("accountId", transaction.getAccount().getId().toString());
        } else if (transaction.getWallet() != null
                && transaction.getWallet().getAccount() != null
                && transaction.getWallet().getAccount().getId() != null) {
            data.put("accountId", transaction.getWallet().getAccount().getId().toString());
        }
        return data;
    }

    public static Map<String, Object> fromCharge(Charge charge) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (charge == null) {
            return data;
        }
        data.put("chargeId", charge.getId() != null ? charge.getId().toString() : null);
        data.put("status", charge.getStatus() != null ? charge.getStatus().name() : null);
        data.put("billingType", charge.getBillingType() != null ? charge.getBillingType().name() : null);
        data.put("amount", charge.getValue() != null ? charge.getValue().toPlainString() : null);
        data.put("netValue", charge.getNetValue() != null ? charge.getNetValue().toPlainString() : null);
        data.put("currency", "BRL");
        data.put("asaasPaymentId", charge.getAsaasPaymentId());
        data.put("externalReference", charge.getExternalReference());
        data.put("description", charge.getDescription());
        if (charge.getAccount() != null && charge.getAccount().getId() != null) {
            data.put("accountId", charge.getAccount().getId().toString());
        }
        if (charge.getOrganization() != null && charge.getOrganization().getId() != null) {
            data.put("organizationId", charge.getOrganization().getId().toString());
        }
        return data;
    }

    public static UUID organizationId(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        if (transaction.getOrganization() != null) {
            return transaction.getOrganization().getId();
        }
        if (transaction.getAccount() != null && transaction.getAccount().getOrganization() != null) {
            return transaction.getAccount().getOrganization().getId();
        }
        if (transaction.getWallet() != null
                && transaction.getWallet().getAccount() != null
                && transaction.getWallet().getAccount().getOrganization() != null) {
            return transaction.getWallet().getAccount().getOrganization().getId();
        }
        return null;
    }

    public static UUID organizationId(Charge charge) {
        if (charge == null) {
            return null;
        }
        if (charge.getOrganization() != null) {
            return charge.getOrganization().getId();
        }
        if (charge.getAccount() != null && charge.getAccount().getOrganization() != null) {
            return charge.getAccount().getOrganization().getId();
        }
        return null;
    }
}
