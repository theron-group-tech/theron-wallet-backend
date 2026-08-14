package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateBeneficiaryRequest;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.repository.BeneficiaryRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BeneficiaryServiceIntegrationTest extends BaseIntegrationTest {

    private static final String ACTOR_HEADER = "X-Actor-User-Id";
    private static final String PIX_KEY = "12345678901";

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMembershipService membershipService;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationResponse orgA;
    private OrganizationResponse orgB;
    private UserResponse ownerA;
    private UserResponse employeeA;
    private UserResponse ownerB;

    @BeforeEach
    void setUpBeneficiaries() {
        orgA = createOrg("11122233000101");
        orgB = createOrg("11122233000102");

        ownerA = createUser("owner-a@theron.test");
        employeeA = createUser("employee-a@theron.test");
        ownerB = createUser("owner-b@theron.test");

        membershipService.addMember(orgA.getId(), member(ownerA.getId()));
        membershipService.addMember(orgA.getId(), member(employeeA.getId()));
        membershipService.addMember(orgB.getId(), member(ownerB.getId()));

        roleAssignmentService.assignRolesInternal(orgA.getId(), ownerA.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(orgB.getId(), ownerB.getId(), List.of(RoleCode.OWNER.name()));
    }

    @Test
    @DisplayName("1. CRUD — POST/GET list/GET id/PATCH name")
    void crud() throws Exception {
        MvcResult createdResult = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(orgA.getId(), "Fornecedor ACME", PIX_KEY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.organizationId").value(orgA.getId().toString()))
                .andExpect(jsonPath("$.createdByUserId").value(ownerA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Fornecedor ACME"))
                .andExpect(jsonPath("$.pixKey").value(PIX_KEY))
                .andExpect(jsonPath("$.pixKeyType").value("CPF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        BeneficiaryResponse created = read(createdResult);

        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerA.getId())
                        .param("organizationId", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(created.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Fornecedor ACME"));

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Fornecedor ACME"));

        mockMvc.perform(patch("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateBeneficiaryRequest.builder()
                                .name("Nome atualizado")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome atualizado"))
                .andExpect(jsonPath("$.organizationId").value(orgA.getId().toString()));
    }

    @Test
    @DisplayName("2. Duplicidade — mesma org + mesma pix key → 409")
    void duplicatePixKey() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(orgA.getId(), "Primeiro", PIX_KEY))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(orgA.getId(), "Segundo", PIX_KEY))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("3. Inativo — DELETE lógico; saque com beneficiaryId → 422; GET ainda 200 INACTIVE")
    void inactiveCannotBeUsedInWithdraw() throws Exception {
        BeneficiaryResponse created = createPixBeneficiary(ownerA.getId(), orgA.getId(), "Inativo", PIX_KEY);
        Subaccount subaccount = persistFundedSubaccount();

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(WithdrawRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("10.00"))
                                .beneficiaryId(created.getId())
                                .build())))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("4. Cross-tenant — list org B vazio; GET id de A com ator B → 403")
    void crossTenantIsolation() throws Exception {
        BeneficiaryResponse created = createPixBeneficiary(ownerA.getId(), orgA.getId(), "De A", PIX_KEY);

        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerB.getId())
                        .param("organizationId", orgB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Sem permission — EMPLOYEE POST ou DELETE → 403")
    void employeeCannotCreateOrDelete() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, employeeA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(orgA.getId(), "Bloqueado", PIX_KEY))))
                .andExpect(status().isForbidden());

        BeneficiaryResponse created = createPixBeneficiary(ownerA.getId(), orgA.getId(), "Do owner", PIX_KEY);

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, employeeA.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, employeeA.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Usado em transferência — saque com beneficiaryId grava FK; DELETE lógico ainda 204")
    void usedInWithdrawThenLogicalDelete() throws Exception {
        BeneficiaryResponse created = createPixBeneficiary(ownerA.getId(), orgA.getId(), "Pix destino", PIX_KEY);
        Subaccount subaccount = persistFundedSubaccount();
        stubAsaasTransfer("transfer_beneficiary");

        mockMvc.perform(post("/api/v1/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(WithdrawRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("25.00"))
                                .beneficiaryId(created.getId())
                                .pixAddressKey("should-be-ignored")
                                .pixAddressKeyType("EMAIL")
                                .build())))
                .andExpect(status().isCreated());

        assertThat(transactionRepository.existsByBeneficiary_Id(created.getId())).isTrue();

        ArgumentCaptor<AsaasTransferRequest> captor = ArgumentCaptor.forClass(AsaasTransferRequest.class);
        verify(asaasTransferClient).createTransfer(anyString(), captor.capture());
        assertThat(captor.getValue().getPixAddressKey()).isEqualTo(PIX_KEY);
        assertThat(captor.getValue().getPixAddressKeyType()).isEqualTo("CPF");

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isNoContent());
        assertThat(beneficiaryRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("7. Inexistente — GET/PATCH/DELETE UUID aleatório → 404; withdraw → 404")
    void missingResource() throws Exception {
        UUID missing = UUID.randomUUID();
        Subaccount subaccount = persistFundedSubaccount();

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", missing)
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/beneficiaries/{id}", missing)
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateBeneficiaryRequest.builder()
                                .name("x")
                                .build())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", missing)
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/withdraws")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(WithdrawRequest.builder()
                                .subaccountId(subaccount.getId())
                                .amount(new BigDecimal("10.00"))
                                .beneficiaryId(missing)
                                .build())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("8. Delete lógico — unique ainda bloqueia; PATCH status=ACTIVE reativa")
    void logicalDeleteKeepsUniqueAndCanReactivate() throws Exception {
        BeneficiaryResponse created = createPixBeneficiary(ownerA.getId(), orgA.getId(), "Reativavel", PIX_KEY);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId()))
                .andExpect(status().isNoContent());

        assertThat(beneficiaryRepository.findById(created.getId())).isPresent();
        assertThat(beneficiaryRepository.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(BeneficiaryStatus.INACTIVE);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(orgA.getId(), "Clone", PIX_KEY))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/beneficiaries/{id}", created.getId())
                        .header(ACTOR_HEADER, ownerA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateBeneficiaryRequest.builder()
                                .status(BeneficiaryStatus.ACTIVE)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private BeneficiaryResponse createPixBeneficiary(UUID actorId, UUID organizationId, String name, String pixKey)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header(ACTOR_HEADER, actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pixRequest(organizationId, name, pixKey))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result);
    }

    private static CreateBeneficiaryRequest pixRequest(UUID organizationId, String name, String pixKey) {
        return CreateBeneficiaryRequest.builder()
                .organizationId(organizationId)
                .name(name)
                .pixKey(pixKey)
                .pixKeyType(PixKeyType.CPF)
                .build();
    }

    private Subaccount persistFundedSubaccount() {
        Subaccount subaccount = subaccountRepository.save(TestFixtures.aSubaccount(SubaccountStatus.ACTIVE));
        walletRepository.save(TestFixtures.aWalletWithBalance(subaccount, new BigDecimal("500.00")));
        when(asaasApiKeyResolver.resolveForSubaccount(any())).thenReturn("root-api-key");
        return subaccount;
    }

    private void stubAsaasTransfer(String transferId) {
        when(asaasTransferClient.createTransfer(anyString(), any()))
                .thenReturn(AsaasTransferResponse.builder()
                        .id(transferId)
                        .value(new BigDecimal("25.00"))
                        .netValue(new BigDecimal("24.00"))
                        .status("PENDING")
                        .operationType("PIX")
                        .build());
    }

    private BeneficiaryResponse read(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), BeneficiaryResponse.class);
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
