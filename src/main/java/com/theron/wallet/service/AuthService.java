package com.theron.wallet.service;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}

