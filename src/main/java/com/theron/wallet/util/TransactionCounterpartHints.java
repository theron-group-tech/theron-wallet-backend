package com.theron.wallet.util;

import com.theron.wallet.entity.Beneficiary;
import com.theron.wallet.entity.Transaction;

public final class TransactionCounterpartHints {

    private TransactionCounterpartHints() {
    }

    public static String resolve(Transaction transaction) {
        Beneficiary beneficiary = transaction.getBeneficiary();
        if (beneficiary != null) {
            if (beneficiary.getName() != null && !beneficiary.getName().isBlank()) {
                return beneficiary.getName();
            }
            if (beneficiary.getPixKey() != null && !beneficiary.getPixKey().isBlank()) {
                return beneficiary.getPixKey();
            }
        }

        if (transaction.getExternalReference() != null && !transaction.getExternalReference().isBlank()) {
            return transaction.getExternalReference();
        }
        if (transaction.getReference() != null && !transaction.getReference().isBlank()) {
            return transaction.getReference();
        }
        return null;
    }
}
