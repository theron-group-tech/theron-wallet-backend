package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.CreateOrganizationRequest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.OrganizationResponse;
import com.theron.wallet.dto.response.UserOrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserMembershipIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMembershipService membershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private CreateUserRequest validUserRequest(String email) {
        return CreateUserRequest.builder()
                .name("Maria Silva")
                .email(email)
                .phone("11999998888")
                .password("SenhaForte1!")
                .build();
    }

    private OrganizationResponse createOrg(String document) {
        return organizationService.create(CreateOrganizationRequest.builder()
                .legalName("Org " + document)
                .tradeName("Trade")
                .document(document)
                .documentType(DocumentType.CNPJ)
                .build());
    }

    @Nested
    @DisplayName("User CRUD")
    class UserCrudTests {

        @Test
        @DisplayName("should create a valid user with hashed password")
        void shouldCreateUser() {
            UserResponse response = userService.create(validUserRequest("maria@empresa.com.br"));

            assertThat(response.getId()).isNotNull();
            assertThat(response.getEmail()).isEqualTo("maria@empresa.com.br");
            assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(userRepository.findById(response.getId())).isPresent().get()
                    .satisfies(u -> assertThat(passwordEncoder.matches("SenhaForte1!", u.getPasswordHash())).isTrue());
        }

        @Test
        @DisplayName("should reject duplicate email")
        void shouldRejectDuplicateEmail() {
            userService.create(validUserRequest("dup@empresa.com.br"));

            assertThatThrownBy(() -> userService.create(validUserRequest("DUP@empresa.com.br")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("should find existing and throw for missing user")
        void shouldFindAndNotFound() {
            UserResponse created = userService.create(validUserRequest("find@empresa.com.br"));

            assertThat(userService.findById(created.getId()).getEmail()).isEqualTo("find@empresa.com.br");
            assertThatThrownBy(() -> userService.findById(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should update user without physical delete endpoint")
        void shouldUpdateUser() throws Exception {
            UserResponse created = userService.create(validUserRequest("upd@empresa.com.br"));

            UserResponse updated = userService.update(created.getId(), UpdateUserRequest.builder()
                    .name("Maria Atualizada")
                    .status(UserStatus.SUSPENDED)
                    .build());

            assertThat(updated.getName()).isEqualTo("Maria Atualizada");
            assertThat(updated.getStatus()).isEqualTo(UserStatus.SUSPENDED);

            mockMvc.perform(delete("/api/v1/users/{id}", created.getId()))
                    .andExpect(status().isMethodNotAllowed());
            assertThat(userRepository.existsById(created.getId())).isTrue();
        }

        @Test
        @DisplayName("should reject missing required fields via HTTP")
        void shouldRejectMissingFieldsViaHttp() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Membership")
    class MembershipTests {

        @Test
        @DisplayName("should add membership and reject duplicate ACTIVE")
        void shouldAddAndRejectDuplicate() {
            OrganizationResponse org = createOrg("11122233000144");
            UserResponse user = userService.create(validUserRequest("m1@empresa.com.br"));

            OrganizationMembershipResponse membership = membershipService.addMember(
                    org.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());

            assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(membership.getOrganizationId()).isEqualTo(org.getId());
            assertThat(membership.getUserId()).isEqualTo(user.getId());

            assertThatThrownBy(() -> membershipService.addMember(
                    org.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build()))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should allow same user in two organizations")
        void shouldAllowUserInTwoOrganizations() {
            OrganizationResponse orgA = createOrg("11122233000155");
            OrganizationResponse orgB = createOrg("11122233000166");
            UserResponse user = userService.create(validUserRequest("two@empresa.com.br"));

            membershipService.addMember(orgA.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());
            membershipService.addMember(orgB.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());

            List<UserOrganizationResponse> orgs = userService.listOrganizations(user.getId());
            assertThat(orgs).hasSize(2);
            assertThat(orgs).extracting(UserOrganizationResponse::getOrganizationId)
                    .containsExactlyInAnyOrder(orgA.getId(), orgB.getId());
        }

        @Test
        @DisplayName("should list members scoped to organization")
        void shouldListMembers() {
            OrganizationResponse org = createOrg("11122233000177");
            UserResponse u1 = userService.create(validUserRequest("l1@empresa.com.br"));
            UserResponse u2 = userService.create(validUserRequest("l2@empresa.com.br"));

            membershipService.addMember(org.getId(),
                    AddOrganizationMemberRequest.builder().userId(u1.getId()).build());
            membershipService.addMember(org.getId(),
                    AddOrganizationMemberRequest.builder().userId(u2.getId()).build());

            Page<OrganizationMembershipResponse> page = membershipService.listMembers(
                    org.getId(), null, PageRequest.of(0, 20));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("should soft-remove member and revoke access")
        void shouldRemoveMemberAndLoseAccess() {
            OrganizationResponse org = createOrg("11122233000188");
            UserResponse user = userService.create(validUserRequest("rm@empresa.com.br"));

            membershipService.addMember(org.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());
            membershipService.assertActiveMembership(org.getId(), user.getId());

            OrganizationMembershipResponse removed = membershipService.removeMember(org.getId(), user.getId());
            assertThat(removed.getStatus()).isEqualTo(MembershipStatus.REMOVED);
            assertThat(userRepository.existsById(user.getId())).isTrue();

            assertThatThrownBy(() -> membershipService.assertActiveMembership(org.getId(), user.getId()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("should isolate members between organizations")
        void shouldIsolateBetweenOrganizations() {
            OrganizationResponse orgA = createOrg("11122233000199");
            OrganizationResponse orgB = createOrg("11122233000200");
            UserResponse userA = userService.create(validUserRequest("isoA@empresa.com.br"));
            UserResponse userB = userService.create(validUserRequest("isoB@empresa.com.br"));

            membershipService.addMember(orgA.getId(),
                    AddOrganizationMemberRequest.builder().userId(userA.getId()).build());
            membershipService.addMember(orgB.getId(),
                    AddOrganizationMemberRequest.builder().userId(userB.getId()).build());

            Page<OrganizationMembershipResponse> membersA = membershipService.listMembers(
                    orgA.getId(), null, PageRequest.of(0, 20));
            Page<OrganizationMembershipResponse> membersB = membershipService.listMembers(
                    orgB.getId(), null, PageRequest.of(0, 20));

            assertThat(membersA.getContent()).extracting(OrganizationMembershipResponse::getUserId)
                    .containsExactly(userA.getId())
                    .doesNotContain(userB.getId());
            assertThat(membersB.getContent()).extracting(OrganizationMembershipResponse::getUserId)
                    .containsExactly(userB.getId())
                    .doesNotContain(userA.getId());

            assertThatThrownBy(() -> membershipService.assertActiveMembership(orgA.getId(), userB.getId()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("should throw when organization or user does not exist")
        void shouldThrowWhenOrgOrUserMissing() {
            OrganizationResponse org = createOrg("11122233000211");
            UserResponse user = userService.create(validUserRequest("nf@empresa.com.br"));
            UUID missing = UUID.randomUUID();

            assertThatThrownBy(() -> membershipService.addMember(
                    missing,
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization");

            assertThatThrownBy(() -> membershipService.addMember(
                    org.getId(),
                    AddOrganizationMemberRequest.builder().userId(missing).build()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        @DisplayName("should reactivate REMOVED membership instead of duplicating row")
        void shouldReactivateRemovedMembership() {
            OrganizationResponse org = createOrg("11122233000222");
            UserResponse user = userService.create(validUserRequest("re@empresa.com.br"));

            OrganizationMembershipResponse first = membershipService.addMember(
                    org.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());
            membershipService.removeMember(org.getId(), user.getId());

            OrganizationMembershipResponse reactivated = membershipService.addMember(
                    org.getId(),
                    AddOrganizationMemberRequest.builder().userId(user.getId()).build());

            assertThat(reactivated.getId()).isEqualTo(first.getId());
            assertThat(reactivated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(membershipRepository.count()).isEqualTo(1);
        }
    }
}
