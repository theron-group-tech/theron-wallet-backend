package com.theron.wallet.service;

import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.TransactionStatus;

public interface TransactionLifecycleService {

    Transaction transition(Transaction transaction, TransactionStatus target);
}
