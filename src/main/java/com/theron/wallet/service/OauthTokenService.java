package com.theron.wallet.service;

import com.theron.wallet.dto.response.OauthTokenResponse;

public interface OauthTokenService {

    OauthTokenResponse issueClientCredentialsToken(String clientId, String clientSecret, String grantType);
}
