package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.AssignOrganizationAdminRequest;
import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateOrganizationEmployeeRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.OrganizationEmployeeResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.integration.AsaasSubaccountClient;
import com.theron.wallet.repository.SubaccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformHierarchyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private SubaccountRepository subaccountRepository;
    @Autowired
    private AsaasSubaccountClient asaasSubaccountClient;

    @Test
    @DisplayName("platform admin, org admin and employee hierarchy")
    void hierarchy() throws Exception {
        String adminToken = bearer(adminAccessToken());

        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").isString())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        OrganizationResponse orgA = createOrg(adminToken, "Acme A", "11122233000181");
        OrganizationResponse orgB = createOrg(adminToken, "Acme B", "11122233000182");

        UserResponse joao = userService.create(CreateUserRequest.builder()
                .name("Joao").email("joao-hier@theron.test").password("SenhaForte1!").build());
        UserResponse outsider = userService.create(CreateUserRequest.builder()
                .name("Maria OrgB").email("maria-hier@theron.test").password("SenhaForte1!").build());

        mockMvc.perform(post("/api/v1/admin/organizations/{id}/admin", orgA.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AssignOrganizationAdminRequest.builder()
                                .userId(joao.getId()).build())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/organizations/{id}/admin", orgB.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AssignOrganizationAdminRequest.builder()
                                .userId(outsider.getId()).build())))
                .andExpect(status().isCreated());

        String joaoToken = bearer(productAccessToken(joao.getEmail()));
        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", joaoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateOrganizationRequest.builder()
                                .legalName("Hacker Org")
                                .document("11122233000183")
                                .documentType(DocumentType.CNPJ)
                                .build())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/organizations").header("Authorization", joaoToken))
                .andExpect(status().isForbidden());

        int before = capturedCreateCalls();
        MvcResult accountResult = mockMvc.perform(post("/api/v1/organization/accounts")
                        .header("Authorization", joaoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAccountRequest.builder()
                                .name("Joao MAIN")
                                .type(AccountType.MAIN)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(joao.getId().toString()))
                .andReturn();
        AccountResponse joaoAccount = objectMapper.readValue(
                accountResult.getResponse().getContentAsString(), AccountResponse.class);
        assertThat(capturedCreateCalls()).isGreaterThan(before);

        int afterBind = capturedCreateCalls();
        mockMvc.perform(post("/api/v1/accounts/{id}/asaas-subaccount", joaoAccount.getId())
                        .header("Authorization", joaoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(capturedCreateCalls()).isEqualTo(afterBind);

        MvcResult employeeResult = mockMvc.perform(post("/api/v1/organization/members")
                        .header("Authorization", joaoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateOrganizationEmployeeRequest.builder()
                                .name("Pedro")
                                .email("pedro-hier@theron.test")
                                .password("SenhaForte1!")
                                .document("22233344000155")
                                .documentType(DocumentType.CNPJ)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(orgA.getId().toString()))
                .andExpect(jsonPath("$.account.ownerUserId").isString())
                .andExpect(jsonPath("$.asaasBind.status").value("ACTIVE"))
                .andReturn();
        OrganizationEmployeeResponse employee = objectMapper.readValue(
                employeeResult.getResponse().getContentAsString(), OrganizationEmployeeResponse.class);
        String pedroToken = bearer(productAccessToken("pedro-hier@theron.test"));

        mockMvc.perform(get("/api/v1/organizations/{id}", orgB.getId())
                        .header("Authorization", joaoToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/accounts/{id}", joaoAccount.getId())
                        .header("Authorization", pedroToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/accounts/{id}", employee.getAccount().getId())
                        .header("Authorization", pedroToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/organizations").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(patch("/api/v1/admin/splits")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateSplitConfigRequest.builder()
                                .percent(new BigDecimal("1.50"))
                                .fixedAmount(BigDecimal.ZERO)
                                .enabled(true)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(1.50))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(patch("/api/v1/admin/splits")
                        .header("Authorization", joaoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        AccountResponse unbound = accountService.create(orgA.getId(), CreateAccountRequest.builder()
                .name("Unbound")
                .type(AccountType.RESERVE)
                .build());
        subaccountRepository.findByAccount_Id(unbound.getId()).ifPresent(sub -> {
            sub.setEncryptedApiKey(null);
            sub.setAsaasAccountId(null);
            sub.setAsaasWalletId(null);
            sub.transitionTo(SubaccountStatus.FAILED, "unlinked");
            subaccountRepository.saveAndFlush(sub);
        });
        mockMvc.perform(post("/api/v1/pix/keys")
                        .header("Authorization", joaoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAccountPixKeyRequest.builder()
                                .accountId(unbound.getId())
                                .type(PixKeyType.EVP)
                                .build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASAAS_ERROR"))
                .andExpect(jsonPath("$.message").value("Account is not linked to an Asaas subaccount"));
    }

    private int capturedCreateCalls() {
        return (int) org.mockito.Mockito.mockingDetails(asaasSubaccountClient)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("createSubaccount"))
                .count();
    }

    private OrganizationResponse createOrg(String adminToken, String name, String document) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/organizations")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateOrganizationRequest.builder()
                                .legalName(name)
                                .document(document)
                                .documentType(DocumentType.CNPJ)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), OrganizationResponse.class);
    }
}
