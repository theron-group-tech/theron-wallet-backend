package com.theron.wallet.security;

/**
 * Canonical permission codes (must match Flyway seed in V14).
 */
public final class PermissionCodes {

    public static final String ORGANIZATION_READ = "organization.read";
    public static final String ORGANIZATION_UPDATE = "organization.update";

    public static final String USERS_READ = "users.read";
    public static final String USERS_CREATE = "users.create";
    public static final String USERS_UPDATE = "users.update";
    public static final String USERS_DISABLE = "users.disable";

    public static final String MEMBERS_READ = "members.read";
    public static final String MEMBERS_MANAGE = "members.manage";

    public static final String WALLET_READ = "wallet.read";
    public static final String WALLET_TRANSFER = "wallet.transfer";

    public static final String TRANSACTIONS_READ = "transactions.read";
    public static final String TRANSACTIONS_CREATE = "transactions.create";

    public static final String PIX_READ = "pix.read";
    public static final String PIX_CREATE = "pix.create";
    public static final String PIX_TRANSFER = "pix.transfer";

    public static final String BENEFICIARIES_READ = "beneficiaries.read";
    public static final String BENEFICIARIES_CREATE = "beneficiaries.create";
    public static final String BENEFICIARIES_UPDATE = "beneficiaries.update";
    public static final String BENEFICIARIES_DELETE = "beneficiaries.delete";

    public static final String AUDIT_READ = "audit.read";

    public static final String LIMITS_READ = "limits.read";
    public static final String LIMITS_MANAGE = "limits.manage";

    public static final String APPROVAL_READ = "approval.read";
    public static final String APPROVAL_CREATE = "approval.create";
    public static final String APPROVAL_APPROVE = "approval.approve";
    public static final String APPROVAL_REJECT = "approval.reject";

    private PermissionCodes() {
    }
}
