package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.UserOrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse findById(UUID id);

    UserResponse update(UUID id, UpdateUserRequest request);

    List<UserOrganizationResponse> listOrganizations(UUID userId);
}
