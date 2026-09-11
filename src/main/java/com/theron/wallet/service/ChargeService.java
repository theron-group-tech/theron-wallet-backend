package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateChargeRequest;
import com.theron.wallet.dto.response.ChargeResponse;
import com.theron.wallet.security.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ChargeService {

    ChargeResponse create(Actor actor, CreateChargeRequest request);

    Page<ChargeResponse> list(Actor actor, Pageable pageable);

    ChargeResponse get(Actor actor, UUID chargeId);

    ChargeResponse cancel(Actor actor, UUID chargeId);
}
