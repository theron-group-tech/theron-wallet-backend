package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateAnticipationRequest;
import com.theron.wallet.dto.response.AnticipationResponse;
import com.theron.wallet.security.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AnticipationService {

    AnticipationResponse simulate(Actor actor, CreateAnticipationRequest request);

    AnticipationResponse create(Actor actor, CreateAnticipationRequest request);

    Page<AnticipationResponse> list(Actor actor, Pageable pageable);

    AnticipationResponse get(Actor actor, UUID id);
}
