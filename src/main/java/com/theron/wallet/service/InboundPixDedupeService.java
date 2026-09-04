package com.theron.wallet.service;

import com.theron.wallet.dto.response.InboundDedupeResponse;

import java.util.UUID;

/**
 * Reverses duplicate Master→Theron inbound credits ({@code asaas:pix:in:pay_*})
 * when a matching Platform PIX credit already exists on the Account.
 */
public interface InboundPixDedupeService {

    InboundDedupeResponse dedupeAccount(UUID accountId);
}
