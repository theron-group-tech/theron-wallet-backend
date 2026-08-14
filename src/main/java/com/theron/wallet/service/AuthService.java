package com.theron.wallet.service;

import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.response.AuthSessionResponse;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface AuthService {

    LoginResponse login(LoginRequest request, String ip, String userAgent);

    LoginResponse refresh(String refreshToken, String ip, String userAgent);

    void logout(UserPrincipal principal, String refreshToken);

    void logoutAll(UserPrincipal principal);

    Object me(UserPrincipal principal);

    List<AuthSessionResponse> listSessions(UserPrincipal principal);

    void revokeSession(UserPrincipal principal, UUID sessionId);
}
