package com.theron.wallet.enums;

/**
 * Product roles: OWNER, FINANCE, EMPLOYEE.
 * ADMIN/AUDITOR are legacy catalog codes — not assignable in product.
 */
public enum RoleCode {
    OWNER,
    FINANCE,
    EMPLOYEE,
    /** @deprecated Not assignable in product; migrated to EMPLOYEE. */
    ADMIN,
    /** @deprecated Not assignable in product; migrated to EMPLOYEE. */
    AUDITOR;

    public boolean isProductAssignable() {
        return this == FINANCE || this == EMPLOYEE;
    }

    public boolean isProductRole() {
        return this == OWNER || this == FINANCE || this == EMPLOYEE;
    }
}
