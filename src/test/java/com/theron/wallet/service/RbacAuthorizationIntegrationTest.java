package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.TenantAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RbacAuthorizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMembershipService membershipService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TenantAccessGuard tenantAccessGuard;

    @Autowired
    private MockMvc mockMvc;

    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private UserResponse ownerA;
    private UserResponse employeeA;
    private UserResponse financeA;
    private UserResponse adminA;
    private UserResponse auditorA;
    private UserResponse employeeB;
    private String tokenOwnerA;

    @BeforeEach
    void setUpRbac() {
        orgA = createOrg("11122233000101");
        orgB = createOrg("11122233000102");

        ownerA = createUser("owner-a@theron.test");
        employeeA = createUser("employee-a@theron.test");
        financeA = createUser("finance-a@theron.test");
        adminA = createUser("admin-a@theron.test");
        auditorA = createUser("auditor-a@theron.test");
        employeeB = createUser("employee-b@theron.test");

        membershipService.addMember(orgA.getId(), member(ownerA.getId()));
        membershipService.addMember(orgA.getId(), member(employeeA.getId()));
        membershipService.addMember(orgA.getId(), member(financeA.getId()));
        membershipService.addMember(orgA.getId(), member(adminA.getId()));
        membershipService.addMember(orgA.getId(), member(auditorA.getId()));
        membershipService.addMember(orgB.getId(), member(employeeB.getId()));

        // Default EMPLOYEE from addMember — elevate as needed via internal assign
        roleAssignmentService.assignRolesInternal(orgA.getId(), ownerA.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), financeA.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), adminA.getId(), List.of(RoleCode.ADMIN.name()));
        roleAssignmentService.assignRolesInternal(orgA.getId(), auditorA.getId(), List.of(RoleCode.AUDITOR.name()));
        // employeeA keeps EMPLOYEE; employeeB keeps EMPLOYEE in orgB
        tokenOwnerA = productAccessToken(ownerA.getEmail());
    }

    @Nested
    @DisplayName("Permission matrix")
    class MatrixTests {

        @Test
        @DisplayName("EMPLOYEE can wallet.read but not wallet.transfer")
        void employeeWalletPermissions() {
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), employeeA.getId(), PermissionCodes.WALLET_READ))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> authorizationService.requirePermission(
                    orgA.getId(), employeeA.getId(), PermissionCodes.WALLET_TRANSFER))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("FINANCE can wallet.transfer")
        void financeCanTransfer() {
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), financeA.getId(), PermissionCodes.WALLET_TRANSFER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ADMIN can manage users/members")
        void adminCanManageUsers() {
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), adminA.getId(), PermissionCodes.USERS_CREATE))
                    .doesNotThrowAnyException();
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), adminA.getId(), PermissionCodes.MEMBERS_MANAGE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AUDITOR can transactions.read but not wallet.transfer")
        void auditorReadOnly() {
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), auditorA.getId(), PermissionCodes.TRANSACTIONS_READ))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> authorizationService.requirePermission(
                    orgA.getId(), auditorA.getId(), PermissionCodes.WALLET_TRANSFER))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Cross-tenant and privilege escalation")
    class IsolationTests {

        @Test
        @DisplayName("permissions do not leak across organizations")
        void crossTenantIsolation() {
            assertThatThrownBy(() -> authorizationService.requirePermission(
                    orgB.getId(), employeeA.getId(), PermissionCodes.WALLET_READ))
                    .isInstanceOf(ForbiddenException.class);

            assertThatThrownBy(() -> authorizationService.requirePermission(
                    orgA.getId(), employeeB.getId(), PermissionCodes.WALLET_READ))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("EMPLOYEE cannot elevate self to ADMIN")
        void employeeCannotElevate() {
            assertThatThrownBy(() -> roleAssignmentService.replaceRoles(
                    employeeA.getId(),
                    orgA.getId(),
                    employeeA.getId(),
                    List.of(RoleCode.ADMIN.name())))
                    .isInstanceOf(ForbiddenException.class);

            assertThat(authorizationService.listRoles(orgA.getId(), employeeA.getId()))
                    .containsExactly(RoleCode.EMPLOYEE.name());
        }

        @Test
        @DisplayName("ADMIN cannot grant OWNER")
        void adminCannotGrantOwner() {
            assertThatThrownBy(() -> roleAssignmentService.replaceRoles(
                    adminA.getId(),
                    orgA.getId(),
                    employeeA.getId(),
                    List.of(RoleCode.OWNER.name())))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Only OWNER");
        }

        @Test
        @DisplayName("OWNER can grant FINANCE")
        void ownerCanGrantFinance() {
            List<String> roles = roleAssignmentService.replaceRoles(
                    ownerA.getId(),
                    orgA.getId(),
                    employeeA.getId(),
                    List.of(RoleCode.FINANCE.name()));
            assertThat(roles).containsExactly(RoleCode.FINANCE.name());
            assertThatCode(() -> authorizationService.requirePermission(
                    orgA.getId(), employeeA.getId(), PermissionCodes.WALLET_TRANSFER))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Malicious IDs")
    class MaliciousIdTests {

        @Test
        @DisplayName("forged organizationId in requirePermission is denied")
        void forgedOrganizationId() {
            assertThatThrownBy(() -> authorizationService.requirePermission(
                    orgB.getId(), ownerA.getId(), PermissionCodes.MEMBERS_MANAGE))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("HTTP body cannot change path organization — actor of orgA cannot manage orgB")
        void httpPathIsTenantSource() throws Exception {
            mockMvc.perform(put("/api/v1/organizations/{organizationId}/members/{userId}/roles",
                            orgB.getId(), employeeB.getId())
                            .header("Authorization", bearer(tokenOwnerA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roleCodes\":[\"ADMIN\"],\"organizationId\":\"" + orgA.getId() + "\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("forged userId cannot obtain another user's permissions via list without authz")
        void forgedUserIdOnAssign() {
            assertThatThrownBy(() -> roleAssignmentService.replaceRoles(
                    employeeB.getId(),
                    orgA.getId(),
                    employeeA.getId(),
                    List.of(RoleCode.ADMIN.name())))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("TenantAccessGuard rejects forged accountId/walletId org ownership")
        void forgedAccountAndWalletOrg() {
            UUID walletId = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();

            assertThatThrownBy(() -> tenantAccessGuard.requireResourceInOrganization(
                    orgA.getId(), walletId, orgB.getId()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Resource does not belong");

            assertThatThrownBy(() -> tenantAccessGuard.requireResourceInOrganization(
                    orgA.getId(), accountId, orgB.getId()))
                    .isInstanceOf(ForbiddenException.class);

            assertThatCode(() -> tenantAccessGuard.requireResourceInOrganization(
                    orgA.getId(), walletId, orgA.getId()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Defaults")
    class DefaultRoleTests {

        @Test
        @DisplayName("new member receives EMPLOYEE by default")
        void defaultEmployeeRole() {
            UserResponse newbie = createUser("newbie@theron.test");
            membershipService.addMember(orgA.getId(), member(newbie.getId()));
            assertThat(authorizationService.listRoles(orgA.getId(), newbie.getId()))
                    .containsExactly(RoleCode.EMPLOYEE.name());
        }
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }

    private UserResponse createUser(String email) {
        return userService.create(CreateUserRequest.builder()
                .name(email)
                .email(email)
                .password("SenhaForte1!")
                .build());
    }

    private static AddOrganizationMemberRequest member(UUID userId) {
        return AddOrganizationMemberRequest.builder().userId(userId).build();
    }
}
