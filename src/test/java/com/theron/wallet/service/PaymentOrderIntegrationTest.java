package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.SubaccountRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentOrderIntegrationTest extends BaseIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private OrganizationService organizationService;
    @Autowired private OrganizationMembershipService membershipService;
    @Autowired private RoleAssignmentService roleAssignmentService;
    @Autowired private AccountService accountService;
    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private SubaccountRepository subaccountRepository;
    @Autowired private AsaasTransferClient asaasTransferClient;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationResponse org;
    private UserResponse owner;
    private UserResponse secondOwner;
    private UserResponse finance;
    private UserResponse employee;
    private AccountResponse ownerAccount;
    private AccountResponse secondOwnerAccount;
    private AccountResponse financeAccount;
    private AccountResponse employeeAccount;
    private String tokenOwner;
    private String tokenSecondOwner;
    private String tokenFinance;
    private String tokenEmployee;

    @BeforeEach
    void setUp() {
        org = organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Payment Order Org")
                .document("33445566000199")
                .documentType(DocumentType.CNPJ)
                .build());
        owner = createUser("po-owner@theron.test");
        secondOwner = createUser("po-owner-2@theron.test");
        finance = createUser("po-finance@theron.test");
        employee = createUser("po-emp@theron.test");

        membershipService.addMember(org.getId(), member(owner.getId()));
        membershipService.addMember(org.getId(), member(secondOwner.getId()));
        membershipService.addMember(org.getId(), member(finance.getId()));
        membershipService.addMember(org.getId(), member(employee.getId()));

        roleAssignmentService.assignRolesInternal(org.getId(), owner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), secondOwner.getId(), List.of(RoleCode.OWNER.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), finance.getId(), List.of(RoleCode.FINANCE.name()));
        roleAssignmentService.assignRolesInternal(org.getId(), employee.getId(), List.of(RoleCode.EMPLOYEE.name()));

        ownerAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Owner Wallet").type(AccountType.MAIN).build(),
                owner.getId(), "33445566701");
        secondOwnerAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Second Owner Wallet").type(AccountType.MAIN).build(),
                secondOwner.getId(), "33445566704");
        financeAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Finance Wallet").type(AccountType.MAIN).build(),
                finance.getId(), "33445566702");
        employeeAccount = accountService.create(org.getId(),
                CreateAccountRequest.builder().name("Employee Wallet").type(AccountType.EMPLOYEE).build(),
                employee.getId(), "33445566703");

        fundWallet(ownerAccount.getId(), "5000.00");
        fundWallet(secondOwnerAccount.getId(), "3000.00");
        fundWallet(financeAccount.getId(), "100.00");
        fundWallet(employeeAccount.getId(), "50.00");

        tokenOwner = productAccessToken(owner.getEmail());
        tokenSecondOwner = productAccessToken(secondOwner.getEmail());
        tokenFinance = productAccessToken(finance.getEmail());
        tokenEmployee = productAccessToken(employee.getEmail());

        when(asaasTransferClient.createAccountTransfer(any(), any(), any())).thenAnswer(invocation -> {
            String transferId = "tr_" + UUID.randomUUID();
            return AsaasTransferResponse.builder()
                    .id(transferId)
                    .status("DONE")
                    .value(invocation.getArgument(1, com.theron.wallet.dto.asaas.AsaasAccountTransferRequest.class)
                            .getValue())
                    .operationType("INTERNAL")
                    .walletId("wal_destination")
                    .build();
        });
        when(asaasTransferClient.retrieveTransfer(any(), any())).thenAnswer(invocation ->
                AsaasTransferResponse.builder()
                        .id(invocation.getArgument(1))
                        .status("DONE")
                        .operationType("INTERNAL")
                        .build());
    }

    @Test
    @DisplayName("EMPLOYEE cannot create PaymentOrder → 403")
    void employeeCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenEmployee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderBody(employeeAccount.getId(), "100.00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE creates PENDING_APPROVAL")
    void financeCreatesPending() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderBody(financeAccount.getId(), "200.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.sourceAccountId").doesNotExist())
                .andExpect(jsonPath("$.destinationAccountId").value(financeAccount.getId().toString()));

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(secondOwnerAccount.getId(), "3000.00");
        assertBalance(financeAccount.getId(), "100.00");
    }

    @Test
    @DisplayName("FINANCE cannot approve → 403")
    void financeCannotApprove() throws Exception {
        UUID orderId = createOrder(tokenFinance, financeAccount.getId(), "150.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE can cancel PENDING_APPROVAL")
    void financeCanCancelPending() throws Exception {
        UUID orderId = createOrder(tokenFinance, financeAccount.getId(), "120.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(tokenFinance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(secondOwnerAccount.getId(), "3000.00");
    }

    @Test
    @DisplayName("OWNER approves → debit OWNER credit destination")
    void ownerApprovesDebitAndCredit() throws Exception {
        UUID orderId = createOrder(tokenFinance, employeeAccount.getId(), "300.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceAccountId").value(ownerAccount.getId().toString()));

        assertBalance(ownerAccount.getId(), "4700.00");
        assertBalance(secondOwnerAccount.getId(), "3000.00");
        assertBalance(employeeAccount.getId(), "350.00");
    }

    @Test
    @DisplayName("Approving OWNER is the payment source; another OWNER balance is untouched")
    void approvingOwnerIsPaymentSource() throws Exception {
        UUID orderId = createOrder(tokenFinance, employeeAccount.getId(), "700.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenSecondOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved by second owner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceAccountId").value(secondOwnerAccount.getId().toString()))
                .andExpect(jsonPath("$.decidedByUserId").value(secondOwner.getId().toString()));

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(secondOwnerAccount.getId(), "2300.00");
        assertBalance(employeeAccount.getId(), "750.00");

        Subaccount secondOwnerSubaccount = subaccountRepository.findByAccount_Id(secondOwnerAccount.getId())
                .orElseThrow();
        ArgumentCaptor<String> apiKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(asaasTransferClient).createAccountTransfer(apiKeyCaptor.capture(), any(), any());
        assertThat(apiKeyCaptor.getValue()).isNotBlank();
        assertThat(secondOwnerSubaccount.getEncryptedApiKey()).isNotNull();
    }

    @Test
    @DisplayName("Create succeeds without debit even when amount exceeds OWNER balance")
    void createDoesNotRequireBalance() throws Exception {
        mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(tokenFinance))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderBody(financeAccount.getId(), "99999.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(secondOwnerAccount.getId(), "3000.00");
    }

    @Test
    @DisplayName("Approve with insufficient balance → 409")
    void insufficientBalanceOnApproveConflict() throws Exception {
        UUID orderId = createOrder(tokenFinance, financeAccount.getId(), "99999.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(secondOwnerAccount.getId(), "3000.00");
        assertBalance(financeAccount.getId(), "100.00");
    }

    @Test
    @DisplayName("Approve fails when Asaas transfer stays PENDING")
    void approveFailsWhenAsaasPending() throws Exception {
        when(asaasTransferClient.createAccountTransfer(any(), any(), any())).thenReturn(
                AsaasTransferResponse.builder().id("tr_pending").status("PENDING").build());
        when(asaasTransferClient.retrieveTransfer(any(), eq("tr_pending"))).thenReturn(
                AsaasTransferResponse.builder().id("tr_pending").status("PENDING").build());

        UUID orderId = createOrder(tokenFinance, employeeAccount.getId(), "100.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(employeeAccount.getId(), "50.00");
    }

    @Test
    @DisplayName("Approve fails when Asaas transfer is not found after create")
    void approveFailsWhenAsaasTransferNotFound() throws Exception {
        when(asaasTransferClient.createAccountTransfer(any(), any(), any())).thenReturn(
                AsaasTransferResponse.builder().id("tr_missing").status("DONE").build());
        when(asaasTransferClient.retrieveTransfer(any(), eq("tr_missing")))
                .thenThrow(new com.theron.wallet.exception.AsaasApiException("not found", 404, ""));

        UUID orderId = createOrder(tokenFinance, employeeAccount.getId(), "80.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        assertBalance(ownerAccount.getId(), "5000.00");
        assertBalance(employeeAccount.getId(), "50.00");
    }

    @Test
    @DisplayName("Creator cannot self-approve → 403")
    void creatorCannotSelfApprove() throws Exception {
        UUID orderId = createOrder(tokenOwner, financeAccount.getId(), "80.00");

        mockMvc.perform(post("/api/v1/payment-orders/{id}/approve", orderId)
                        .header("Authorization", bearer(tokenOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private UUID createOrder(String token, UUID destinationAccountId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payment-orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderBody(destinationAccountId, amount))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PaymentOrderResponse.class).getId();
    }

    private CreatePaymentOrderRequest orderBody(UUID destinationAccountId, String amount) {
        return CreatePaymentOrderRequest.builder()
                .organizationId(org.getId())
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal(amount))
                .description("payment order test")
                .build();
    }

    private void assertBalance(UUID accountId, String expected) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal(expected));
    }

    private void fundWallet(UUID accountId, String balance) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId).orElseThrow();
        BigDecimal target = new BigDecimal(balance);
        BigDecimal delta = target.subtract(wallet.getBalance());
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(wallet.getId(), delta);
        }
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
