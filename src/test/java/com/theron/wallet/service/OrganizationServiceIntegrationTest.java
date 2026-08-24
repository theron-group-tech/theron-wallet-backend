package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateOrganizationRequest;
import com.theron.wallet.dto.request.UpdateOrganizationStatusRequest;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.OrganizationStatusResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrganizationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    private CreateOrganizationRequest validCnpjRequest() {
        return CreateOrganizationRequest.builder()
                .legalName("Acme Tecnologia LTDA")
                .tradeName("Acme")
                .document("12.345.678/0001-99")
                .documentType(DocumentType.CNPJ)
                .build();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create a valid organization with ACTIVE status and normalized document")
        void shouldCreateValidOrganization() {
            OrganizationResponse response = organizationService.create(validCnpjRequest());

            assertThat(response.getId()).isNotNull();
            assertThat(response.getLegalName()).isEqualTo("Acme Tecnologia LTDA");
            assertThat(response.getTradeName()).isEqualTo("Acme");
            assertThat(response.getDocument()).isEqualTo("12345678000199");
            assertThat(response.getDocumentType()).isEqualTo(DocumentType.CNPJ);
            assertThat(response.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
            assertThat(organizationRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should reject duplicate document")
        void shouldRejectDuplicateDocument() {
            organizationService.create(validCnpjRequest());

            assertThatThrownBy(() -> organizationService.create(validCnpjRequest()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("document");
        }

        @Test
        @DisplayName("should reject CPF organization document type")
        void shouldRejectCpfOrganization() {
            CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                    .legalName("Pessoa Fisica")
                    .document("52998224725")
                    .documentType(DocumentType.CPF)
                    .build();

            assertThatThrownBy(() -> organizationService.create(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("CNPJ");
        }

        @Test
        @DisplayName("should reject required fields via HTTP validation")
        void shouldRejectMissingRequiredFieldsViaHttp() throws Exception {
            UserResponse user = userService.create(CreateUserRequest.builder()
                    .name("Org Actor")
                    .email("org-http@theron.test")
                    .password("SenhaForte1!")
                    .build());
            mockMvc.perform(post("/api/v1/organizations")
                            .header("Authorization", bearer(productAccessToken(user.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @DisplayName("product JWT cannot create an organization")
        void productCannotCreateOrganizationViaHttp() throws Exception {
            UserResponse user = userService.create(CreateUserRequest.builder()
                    .name("Org Actor")
                    .email("org-forbidden@theron.test")
                    .password("SenhaForte1!")
                    .build());
            mockMvc.perform(post("/api/v1/organizations")
                            .header("Authorization", bearer(productAccessToken(user.getEmail())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"legalName":"Nope","document":"22345678000191","documentType":"CNPJ"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("findById() / findAll()")
    class FindTests {

        @Test
        @DisplayName("should find existing organization")
        void shouldFindExisting() {
            OrganizationResponse created = organizationService.create(validCnpjRequest());

            OrganizationResponse found = organizationService.findById(created.getId());

            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getDocument()).isEqualTo("12345678000199");
        }

        @Test
        @DisplayName("should throw when organization does not exist")
        void shouldThrowWhenNotFound() {
            UUID missingId = UUID.randomUUID();

            assertThatThrownBy(() -> organizationService.findById(missingId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization");
        }

        @Test
        @DisplayName("should list organizations filtered by status")
        void shouldListByStatus() {
            organizationService.create(validCnpjRequest());
            OrganizationResponse second = organizationService.create(CreateOrganizationRequest.builder()
                    .legalName("Beta LTDA")
                    .document("98765432000111")
                    .documentType(DocumentType.CNPJ)
                    .build());
            organizationService.updateStatus(second.getId(),
                    UpdateOrganizationStatusRequest.builder().status(OrganizationStatus.SUSPENDED).build());

            Page<OrganizationResponse> active = organizationService.findAll(
                    OrganizationStatus.ACTIVE, PageRequest.of(0, 20));
            Page<OrganizationResponse> suspended = organizationService.findAll(
                    OrganizationStatus.SUSPENDED, PageRequest.of(0, 20));

            assertThat(active.getTotalElements()).isEqualTo(1);
            assertThat(suspended.getTotalElements()).isEqualTo(1);
            assertThat(suspended.getContent().getFirst().getId()).isEqualTo(second.getId());
        }
    }

    @Nested
    @DisplayName("update() / status")
    class UpdateTests {

        @Test
        @DisplayName("should update legalName and tradeName without changing document")
        void shouldUpdateOrganization() {
            OrganizationResponse created = organizationService.create(validCnpjRequest());

            OrganizationResponse updated = organizationService.update(created.getId(),
                    UpdateOrganizationRequest.builder()
                            .legalName("Acme Tecnologia S.A.")
                            .tradeName("Acme Pay")
                            .build());

            assertThat(updated.getLegalName()).isEqualTo("Acme Tecnologia S.A.");
            assertThat(updated.getTradeName()).isEqualTo("Acme Pay");
            assertThat(updated.getDocument()).isEqualTo("12345678000199");
            assertThat(updated.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        }

        @Test
        @DisplayName("should change organization status")
        void shouldChangeStatus() {
            OrganizationResponse created = organizationService.create(validCnpjRequest());

            OrganizationStatusResponse statusResponse = organizationService.updateStatus(
                    created.getId(),
                    UpdateOrganizationStatusRequest.builder().status(OrganizationStatus.BLOCKED).build());

            assertThat(statusResponse.getStatus()).isEqualTo(OrganizationStatus.BLOCKED);
            assertThat(organizationService.getStatus(created.getId()).getStatus())
                    .isEqualTo(OrganizationStatus.BLOCKED);
            assertThat(organizationService.findById(created.getId()).getStatus())
                    .isEqualTo(OrganizationStatus.BLOCKED);
        }
    }

    @Nested
    @DisplayName("delete / uniqueness")
    class ConstraintsTests {

        @Test
        @DisplayName("physical DELETE via HTTP must be forbidden (405)")
        void shouldForbidPhysicalDeleteViaHttp() throws Exception {
            OrganizationResponse created = organizationService.create(validCnpjRequest());
            UserResponse user = userService.create(CreateUserRequest.builder()
                    .name("Org Delete Actor")
                    .email("org-delete@theron.test")
                    .password("SenhaForte1!")
                    .build());

            mockMvc.perform(delete("/api/v1/organizations/{id}", created.getId())
                            .header("Authorization", bearer(productAccessToken(user.getEmail()))))
                    .andExpect(status().isMethodNotAllowed());

            assertThat(organizationRepository.existsById(created.getId())).isTrue();
        }

        @Test
        @DisplayName("database unique constraint on document must be enforced")
        void shouldEnforceUniqueDocumentConstraint() {
            organizationRepository.save(Organization.builder()
                    .legalName("Org One")
                    .document("11122233000144")
                    .documentType(DocumentType.CNPJ)
                    .status(OrganizationStatus.ACTIVE)
                    .build());

            Organization duplicate = Organization.builder()
                    .legalName("Org Two")
                    .document("11122233000144")
                    .documentType(DocumentType.CNPJ)
                    .status(OrganizationStatus.ACTIVE)
                    .build();

            assertThatThrownBy(() -> {
                organizationRepository.saveAndFlush(duplicate);
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
