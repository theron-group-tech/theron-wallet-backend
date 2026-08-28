package com.theron.wallet.security;

/**
 * Canonical permission codes (must match Flyway seeds V14+V28).
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
    public static final String MEMBERS_CREATE = "members.create";
    public static final String MEMBERS_UPDATE = "members.update";
    public static final String MEMBERS_SUSPEND = "members.suspend";
    public static final String MEMBERS_ACTIVATE = "members.activate";
    public static final String MEMBERS_REMOVE = "members.remove";
    public static final String MEMBERS_ROLE_UPDATE = "members.role.update";

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

    /** @deprecated PIX personal path no longer uses ApprovalPolicy; kept for legacy tables. */
    public static final String APPROVAL_READ = "approval.read";
    /** @deprecated */
    public static final String APPROVAL_CREATE = "approval.create";
    /** @deprecated */
    public static final String APPROVAL_APPROVE = "approval.approve";
    /** @deprecated */
    public static final String APPROVAL_REJECT = "approval.reject";

    public static final String PAYMENT_ORDERS_CREATE = "payment_orders.create";
    public static final String PAYMENT_ORDERS_READ = "payment_orders.read";
    /** Alias kept for compatibility with payment-order service naming. */
    public static final String PAYMENT_ORDERS_VIEW = PAYMENT_ORDERS_READ;
    public static final String PAYMENT_ORDERS_CANCEL = "payment_orders.cancel";
    public static final String PAYMENT_ORDERS_APPROVE = "payment_orders.approve";
    public static final String PAYMENT_ORDERS_REJECT = "payment_orders.reject";

    public static final String PROFILE_READ = "profile.read";
    public static final String PROFILE_UPDATE = "profile.update";

    private PermissionCodes() {
    }
}
