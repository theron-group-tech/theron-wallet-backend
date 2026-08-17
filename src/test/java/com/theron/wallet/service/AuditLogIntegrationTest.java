package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateTransactionLimitRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.UpdateTransactionLimitRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.TransactionLimitResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.AuditLog;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.LimitPeriod;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.repository.AuditLogRepository;
import com.theron.wallet.repository.SubaccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditLogIntegrationTest extends BaseIntegrationTest {

    private static final String ACTOR = "X-Actor-User-Id";
    private static final String PASSWORD = "SenhaForte1!";

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private TransactionLimitService transactionLimitService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private OrganizationResponse orgB;
    private UserResponse owner;
    private UserResponse employee;
    private UserResponse auditor;
    private UserResponse ownerB;
    private AccountResponse account;

    @BeforeEach
    void setUp() {
        org = createOrg("66778899000122");
        orgB = createOrg("66778899000123");
        owner = createUser("aud-owner@theron.test");
        employee = createUser("aud-emp@theron.test");
        auditor = createUser("aud-auditor@theron.test");
        ownerB = createUser("aud-owner-b@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(employee.getId()));
        membershipService.addMember(org.getId(), member(auditor.getId()));
        membershipService.addMember(orgB.getId(), member(ownerB.getId()));
        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), employee.getId(), List.of(RoleCode.EMPLOYEE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), auditor.getId(), List.of(RoleCode.AUDITOR.name()));
        roleAssignmentService.assignRolesInternal(orgB.getId(), ownerB.getId(), List.of(RoleCode.OWNER.name()));

        account = accountService.create(org.getId(), CreateAccountRequest.builder()
                .name("Audit Account").type(AccountType.MAIN).build());
        linkAsaas(account.getId(), "66778899901");
        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("encrypted-resolved-key");
    }

    @Test
    @DisplayName("1. Eventos — login, member, PIX key, beneficiary, limit")
    void eventsAreRecorded() throws Exception {
        login(owner.getEmail(), "device-audit", null);
        stubPixKey("pix_audit", "evp-audit-1");
        mockMvc.perform(post("/api/v1/pix/keys")
                        .header(ACTOR, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                .accountId(account.getId())
                                .type(PixKeyType.EVP)
                                .build())))
                .andExpect(status().isCreated());

        beneficiaryService.create(owner.getId(), CreateBeneficiaryRequest.builder()
                .organizationId(org.getId())
                .name("Fornecedor Audit")
                .pixKey("12345678901")
                .pixKeyType(PixKeyType.CPF)
                .build());

        transactionLimitService.create(owner.getId(), CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .transactionType(LimitTransactionType.PIX)
                .period(LimitPeriod.PER_TRANSACTION)
                .maxAmount(new BigDecimal("100.00"))
                .build());

        Set<AuditAction> actions = auditLogRepository.findByActionWithDetails(AuditAction.LOGIN).stream()
                .map(AuditLog::getAction)
                .collect(Collectors.toSet());
        assertThat(actions).contains(AuditAction.LOGIN);

        List<AuditAction> all = List.of(
                AuditAction.USER_CREATED,
                AuditAction.MEMBER_ADDED,
                AuditAction.LOGIN,
                AuditAction.PIX_KEY_CREATED,
                AuditAction.BENEFICIARY_CREATED,
                AuditAction.LIMIT_CHANGED);
        for (AuditAction action : all) {
            assertThat(auditLogRepository.findByActionWithDetails(action))
                    .as("expected action %s", action)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("2. Metadata — PATCH de limite grava maxAmount antigo/novo; sem senha")
    void metadataContainsLimitChangeWithoutPassword() {
        TransactionLimitResponse created = transactionLimitService.create(owner.getId(), CreateTransactionLimitRequest.builder()
                .organizationId(org.getId())
                .transactionType(LimitTransactionType.PIX)
                .period(LimitPeriod.DAILY)
                .maxAmount(new BigDecimal("100.00"))
                .build());
        transactionLimitService.update(owner.getId(), created.getId(), UpdateTransactionLimitRequest.builder()
                .maxAmount(new BigDecimal("50.00"))
                .build());

        AuditLog patchLog = auditLogRepository.findByActionWithDetails(AuditAction.LIMIT_CHANGED).stream()
                .filter(l -> l.getMetadata() != null && "update".equals(String.valueOf(l.getMetadata().get("operation"))))
                .findFirst()
                .orElseThrow();
        assertThat(patchLog.getMetadata().get("previousMaxAmount").toString()).contains("100");
        assertThat(patchLog.getMetadata().get("maxAmount").toString()).contains("50");
        assertThat(objectMapper.valueToTree(patchLog.getMetadata()).toString().toLowerCase()).doesNotContain("password");
    }

    @Test
    @DisplayName("3. Organization — organizationId do evento = org da operação")
    void organizationIdMatchesTenant() {
        AuditLog member = auditLogRepository.findByActionWithDetails(AuditAction.MEMBER_ADDED).stream()
                .filter(l -> owner.getId().equals(l.getUser() != null ? l.getUser().getId() : null))
                .findFirst()
                .orElseThrow();
        assertThat(member.getOrganization().getId()).isEqualTo(org.getId());
    }

    @Test
    @DisplayName("4. User — userId = ator/sujeito esperado")
    void userIdMatchesSubject() throws Exception {
        login(owner.getEmail(), "device-user", null);
        AuditLog loginLog = auditLogRepository.findByUserIdWithDetails(owner.getId()).stream()
                .filter(l -> l.getAction() == AuditAction.LOGIN)
                .findFirst()
                .orElseThrow();
        assertThat(loginLog.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("5. IP — X-Forwarded-For no login aparece em ip")
    void ipFromForwardedFor() throws Exception {
        login(owner.getEmail(), "device-ip", "203.0.113.10");
        AuditLog loginLog = auditLogRepository.findByUserIdWithDetails(owner.getId()).stream()
                .filter(l -> l.getAction() == AuditAction.LOGIN)
                .findFirst()
                .orElseThrow();
        assertThat(loginLog.getIp()).isEqualTo("203.0.113.10");
        assertThat(loginLog.getDeviceId()).isEqualTo("device-ip");
    }

    @Test
    @DisplayName("6. Append-only — JDBC UPDATE/DELETE falha; sem PATCH/DELETE HTTP")
    void appendOnlyEnforced() throws Exception {
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM audit_log LIMIT 1", UUID.class);
        assertThat(id).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_log SET action = 'LOGIN' WHERE id = ?", id))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", id))
                .hasStackTraceContaining("append-only");

        mockMvc.perform(patch("/api/v1/audit-logs")
                        .header(ACTOR, owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/audit-logs")
                        .header(ACTOR, owner.getId()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("7. Sensitive data — metadata não contém password, tokens nem API key")
    void sensitiveDataStripped() throws Exception {
        login(owner.getEmail(), "device-secret", null);
        AuditLog loginLog = auditLogRepository.findByUserIdWithDetails(owner.getId()).stream()
                .filter(l -> l.getAction() == AuditAction.LOGIN)
                .findFirst()
                .orElseThrow();
        String loginJson = objectMapper.writeValueAsString(loginLog.getMetadata());
        assertThat(loginJson.toLowerCase())
                .doesNotContain("password")
                .doesNotContain("accesstoken")
                .doesNotContain("refreshtoken")
                .doesNotContain(PASSWORD.toLowerCase());
        assertThat(loginLog.getMetadata()).containsEntry("email", owner.getEmail());

        auditLogService.record(
                AuditAction.USER_CREATED,
                org.getId(),
                owner.getId(),
                "User",
                owner.getId(),
                Map.of(
                        "password", "should-not-persist",
                        "apiKey", "asaas-secret",
                        "refreshToken", "tok",
                        "safe", "ok"));
        AuditLog sanitized = auditLogRepository.findByActionWithDetails(AuditAction.USER_CREATED).stream()
                .filter(l -> l.getMetadata() != null && l.getMetadata().containsKey("safe"))
                .findFirst()
                .orElseThrow();
        assertThat(sanitized.getMetadata()).containsEntry("safe", "ok");
        assertThat(sanitized.getMetadata().keySet()).noneMatch(k ->
                k.toLowerCase().contains("password")
                        || k.toLowerCase().contains("token")
                        || k.toLowerCase().contains("apikey"));
    }

    @Test
    @DisplayName("8. Cross-tenant — GET da org A com ator da org B → 403")
    void crossTenantForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(ACTOR, ownerB.getId())
                        .param("organizationId", org.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Consulta — OWNER/AUDITOR GET 200; EMPLOYEE 403")
    void queryRequiresAuditRead() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(ACTOR, owner.getId())
                        .param("organizationId", org.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(ACTOR, auditor.getId())
                        .param("organizationId", org.getId().toString())
                        .param("action", "MEMBER_ADDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].organizationId").value(org.getId().toString()));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(ACTOR, employee.getId())
                        .param("organizationId", org.getId().toString()))
                .andExpect(status().isForbidden());
    }

    private void login(String email, String deviceId, String forwardedFor) throws Exception {
        var request = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "JUnit-Audit")
                .content(objectMapper.writeValueAsString(LoginRequest.builder()
                        .email(email)
                        .password(PASSWORD)
                        .deviceId(deviceId)
                        .deviceName("JUnit")
                        .platform("test")
                        .build()));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private void stubPixKey(String providerId, String key) {
        when(asaasPixClient.createPixKey(anyString(), any()))
                .thenReturn(AsaasPixKeyResponse.builder()
                        .id(providerId)
                        .key(key)
                        .type("EVP")
                        .status("ACTIVE")
                        .build());
    }

    private void linkAsaas(UUID accountId, String cpf) {
        Subaccount sub = TestFixtures.aSubaccount(cpf, SubaccountStatus.ACTIVE);
        sub.setAsaasAccountId("asaas_acc_" + cpf);
        sub.setAsaasWalletId("asaas_wal_" + cpf);
        sub.setEncryptedApiKey(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        sub = subaccountRepository.saveAndFlush(sub);
        jdbcTemplate.update("UPDATE subaccount SET account_id = ? WHERE id = ?", accountId, sub.getId());
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
                .password(PASSWORD)
                .build());
    }

    private static AddOrganizationMemberRequest member(UUID userId) {
        return AddOrganizationMemberRequest.builder().userId(userId).build();
    }
}
