package com.theron.wallet.service;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;

public interface InternalTransferService {

    /**
     * Transfers funds between two wallets belonging to different customers on the same platform.
     * The operation is atomic and idempotent: providing the same {@code idempotencyKey} on repeated
     * calls returns the original result without re-processing.
     *
     * @param request transfer parameters including sender, receiver, amount, and optional idempotency key
     * @return a response containing both transaction IDs, wallet IDs, transferred amount, and status
     */
    InternalTransferResponse transfer(InternalTransferRequest request);
}
