package com.theron.wallet.service;

import com.theron.wallet.dto.response.PlatformAccountResponse;

public interface PlatformAccountService {

    PlatformAccountResponse get();

    PlatformAccountResponse syncMasterWalletId(String masterWalletId);
}
