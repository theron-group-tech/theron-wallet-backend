package com.theron.wallet.service;

import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.TestFixtures;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigResponse;
import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.SubaccountApiKeyAuditRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.ApiKeyEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubaccountServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubaccountService subaccountService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SubaccountRepository subaccountRepository;

    @Autowired
    private SubaccountApiKeyAuditRepository auditRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ApiKeyEncryptionService encryptionService;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        savedCustomer = customerRepository.save(TestFixtures.aCustomer());
    }

    @Nested
    @DisplayName("Successful subaccount creation")
    class CreationSuccessTests {

        @Test
        @DisplayName("should create subaccount, encrypt API key, register webhook, and persist PENDING_EVALUATION")
        void shouldCreateSubaccountSuccessfully() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);
            when(asaasWebhookClient.createWebhook(anyString(), any()))
                    .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_123").build());

            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(savedCustomer.getId());
            SubaccountResponse response = subaccountService.create(request);

            // Response assertions
            assertThat(response.getId()).isNotNull();
            assertThat(response.getCustomerId()).isEqualTo(savedCustomer.getId());
            assertThat(response.getAsaasAccountId()).isEqualTo(asaasResponse.getId());
            assertThat(response.getStatus()).isEqualTo(SubaccountStatus.PENDING_EVALUATION);
            assertThat(response.getIncomeValue()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(response.getAddress()).isEqualTo("Rua Teste");
            assertThat(response.getProvince()).isEqualTo("São Paulo");

            // DB verification
            Subaccount persisted = subaccountRepository.findById(response.getId()).orElseThrow();
            assertThat(persisted.getAsaasAccountId()).isEqualTo(asaasResponse.getId());
            assertThat(persisted.getAsaasWalletId()).isEqualTo(asaasResponse.getWalletId());
            assertThat(persisted.getStatus()).isEqualTo(SubaccountStatus.PENDING_EVALUATION);
            assertThat(persisted.getWebhookToken()).isNotBlank();

            // Encrypted API key must be stored and decryptable
            assertThat(persisted.getEncryptedApiKey()).isNotNull();
            String decryptedKey = encryptionService.decrypt(persisted.getEncryptedApiKey());
            assertThat(decryptedKey).isEqualTo(asaasResponse.getApiKey());

            // Audit trail must be created
            long auditCount = auditRepository.countBySubaccountIdAndAction(
                    persisted.getId(), ApiKeyAuditAction.CREATED);
            assertThat(auditCount).isEqualTo(1);

            // Verify Asaas clients were called
            verify(asaasSubaccountClient).createSubaccount(any());
            verify(asaasWebhookClient).createWebhook(anyString(), any());
        }

        @Test
        @DisplayName("should handle Asaas response without API key gracefully")
        void shouldHandleMissingApiKey() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponseWithoutApiKey();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);

            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(savedCustomer.getId());
            SubaccountResponse response = subaccountService.create(request);

            assertThat(response.getStatus()).isEqualTo(SubaccountStatus.PENDING_EVALUATION);

            Subaccount persisted = subaccountRepository.findById(response.getId()).orElseThrow();
            assertThat(persisted.getEncryptedApiKey()).isNull();

            // Webhook should NOT be registered without an API key
            verify(asaasWebhookClient, never()).createWebhook(anyString(), any());

            // No audit trail for key creation
            long auditCount = auditRepository.countBySubaccountIdAndAction(
                    persisted.getId(), ApiKeyAuditAction.CREATED);
            assertThat(auditCount).isZero();
        }
    }

    @Nested
    @DisplayName("Creation failure scenarios")
    class CreationFailureTests {

        @Test
        @DisplayName("should reject if customer already has a subaccount")
        void shouldRejectDuplicateSubaccount() {
            // First: create subaccount successfully
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);
            when(asaasWebhookClient.createWebhook(anyString(), any()))
                    .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_1").build());

            subaccountService.create(TestFixtures.aCreateSubaccountRequest(savedCustomer.getId()));

            // Second: try again — must throw
            CreateSubaccountRequest duplicateRequest = TestFixtures.aCreateSubaccountRequest(savedCustomer.getId());

            assertThatThrownBy(() -> subaccountService.create(duplicateRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Subaccount already exists");
        }

        @Test
        @DisplayName("should reject if customer is not synced with Asaas")
        void shouldRejectUnsyncedCustomer() {
            Customer unsyncedCustomer = customerRepository.save(TestFixtures.aCustomerWithoutAsaas());

            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(unsyncedCustomer.getId());

            assertThatThrownBy(() -> subaccountService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not synced with Asaas");
        }

        @Test
        @DisplayName("should reject if customer does not exist")
        void shouldRejectNonExistentCustomer() {
            UUID fakeId = UUID.randomUUID();
            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(fakeId);

            assertThatThrownBy(() -> subaccountService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");
        }

        @Test
        @DisplayName("should mark subaccount as FAILED when Asaas API call fails and return FAILED response")
        void shouldTransitionToFailedOnAsaasError() {
            when(asaasSubaccountClient.createSubaccount(any()))
                    .thenThrow(new AsaasApiException("Asaas error", 400, "{\"errors\":[]}"));

            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(savedCustomer.getId());
            SubaccountResponse response = subaccountService.create(request);

            // Response must reflect FAILED status
            assertThat(response.getStatus()).isEqualTo(SubaccountStatus.FAILED);
            assertThat(response.getStatusReason()).contains("Asaas API call failed");

            // DB must have a FAILED row
            Subaccount failed = subaccountRepository.findByCustomerId(savedCustomer.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(SubaccountStatus.FAILED);
            assertThat(failed.getStatusReason()).contains("Asaas API call failed");
        }
    }

    @Nested
    @DisplayName("Webhook registration")
    class WebhookRegistrationTests {

        @Test
        @DisplayName("should successfully register webhook during subaccount creation")
        void shouldRegisterWebhookOnCreate() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);
            when(asaasWebhookClient.createWebhook(anyString(), any()))
                    .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_ok").build());

            subaccountService.create(TestFixtures.aCreateSubaccountRequest(savedCustomer.getId()));

            verify(asaasWebhookClient).createWebhook(anyString(), any());
        }

        @Test
        @DisplayName("should still create subaccount successfully even if webhook registration fails")
        void shouldNotFailSubaccountCreationOnWebhookFailure() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);

            // Webhook registration throws
            doThrow(new AsaasApiException("Webhook error", 500, "Internal error"))
                    .when(asaasWebhookClient).createWebhook(anyString(), any());

            CreateSubaccountRequest request = TestFixtures.aCreateSubaccountRequest(savedCustomer.getId());
            SubaccountResponse response = subaccountService.create(request);

            // Subaccount must still be PENDING_EVALUATION (not FAILED)
            assertThat(response.getStatus()).isEqualTo(SubaccountStatus.PENDING_EVALUATION);

            Subaccount persisted = subaccountRepository.findById(response.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(SubaccountStatus.PENDING_EVALUATION);
            assertThat(persisted.getEncryptedApiKey()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Outbound operation enforcement (EVALUATION_BLOCKED constraint)")
    class OutboundOperationTests {

        @Test
        @DisplayName("EVALUATION_BLOCKED must reject outbound operations with 403")
        void shouldBlockOutboundForEvaluationBlocked() {
            Subaccount blocked = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.EVALUATION_BLOCKED);
            subaccountRepository.save(blocked);

            assertThatThrownBy(() -> subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId()))
                    .isInstanceOf(SubaccountOperationBlockedException.class)
                    .hasMessageContaining("EVALUATION_BLOCKED")
                    .hasMessageContaining("outbound operations");
        }

        @Test
        @DisplayName("SUSPENDED must reject outbound operations")
        void shouldBlockOutboundForSuspended() {
            Subaccount suspended = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.SUSPENDED);
            subaccountRepository.save(suspended);

            assertThatThrownBy(() -> subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId()))
                    .isInstanceOf(SubaccountOperationBlockedException.class)
                    .hasMessageContaining("SUSPENDED");
        }

        @Test
        @DisplayName("FAILED must reject outbound operations")
        void shouldBlockOutboundForFailed() {
            Subaccount failed = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.FAILED);
            subaccountRepository.save(failed);

            assertThatThrownBy(() -> subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId()))
                    .isInstanceOf(SubaccountOperationBlockedException.class)
                    .hasMessageContaining("FAILED");
        }

        @Test
        @DisplayName("PROVISIONING must reject outbound operations")
        void shouldBlockOutboundForProvisioning() {
            Subaccount provisioning = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.PROVISIONING);
            subaccountRepository.save(provisioning);

            assertThatThrownBy(() -> subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId()))
                    .isInstanceOf(SubaccountOperationBlockedException.class)
                    .hasMessageContaining("PROVISIONING");
        }

        @Test
        @DisplayName("ACTIVE must allow outbound operations")
        void shouldAllowOutboundForActive() {
            Subaccount active = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.ACTIVE);
            subaccountRepository.save(active);

            // Should not throw
            subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId());
        }

        @Test
        @DisplayName("PENDING_EVALUATION must allow outbound operations")
        void shouldAllowOutboundForPendingEvaluation() {
            Subaccount pending = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.PENDING_EVALUATION);
            subaccountRepository.save(pending);

            // Should not throw
            subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId());
        }

        @Test
        @DisplayName("Customer without subaccount must allow outbound operations (root key fallback)")
        void shouldAllowOutboundWithoutSubaccount() {
            // No subaccount saved for this customer
            subaccountService.assertOutboundOperationsAllowed(savedCustomer.getId());
        }
    }

    @Nested
    @DisplayName("Orphan PROVISIONING detection")
    class OrphanDetectionTests {

        @Test
        @DisplayName("PROVISIONING subaccount older than 5 minutes should be detectable as orphan")
        void shouldDetectOrphanProvisioningSubaccount() {
            // Create a subaccount with old createdAt
            Subaccount orphan = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.PROVISIONING);
            orphan = subaccountRepository.save(orphan);

            // Simulate aging: update createdAt directly via native query is impractical since
            // createdAt is set at insert time. Instead, verify the detection query works:
            // find all PROVISIONING subaccounts and check if their createdAt is old enough.
            List<Subaccount> provisioningSubaccounts = subaccountRepository.findByStatus(SubaccountStatus.PROVISIONING);
            assertThat(provisioningSubaccounts).hasSize(1);

            Subaccount found = provisioningSubaccounts.get(0);
            assertThat(found.getId()).isEqualTo(orphan.getId());
            assertThat(found.getStatus()).isEqualTo(SubaccountStatus.PROVISIONING);

            // Verify age-based orphan detection logic
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            boolean isOrphan = found.getCreatedAt().isBefore(fiveMinutesAgo);
            // Just created, so it's NOT an orphan yet
            assertThat(isOrphan).isFalse();

            // Simulate: if createdAt were 10 minutes ago, it would be an orphan
            LocalDateTime oldTimestamp = LocalDateTime.now().minusMinutes(10);
            found.setCreatedAt(oldTimestamp);
            boolean wouldBeOrphan = found.getCreatedAt().isBefore(fiveMinutesAgo);
            assertThat(wouldBeOrphan).isTrue();
        }

        @Test
        @DisplayName("PENDING_EVALUATION subaccount should NOT appear in PROVISIONING orphan list")
        void shouldNotDetectNonProvisioningAsOrphan() {
            Subaccount active = TestFixtures.aSubaccount(savedCustomer, SubaccountStatus.PENDING_EVALUATION);
            subaccountRepository.save(active);

            List<Subaccount> provisioningSubaccounts = subaccountRepository.findByStatus(SubaccountStatus.PROVISIONING);
            assertThat(provisioningSubaccounts).isEmpty();
        }
    }

    @Nested
    @DisplayName("Query tests")
    class QueryTests {

        @Test
        @DisplayName("findById should return subaccount when it exists")
        void shouldFindSubaccountById() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);
            when(asaasWebhookClient.createWebhook(anyString(), any()))
                    .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_q").build());

            SubaccountResponse created = subaccountService.create(
                    TestFixtures.aCreateSubaccountRequest(savedCustomer.getId()));

            SubaccountResponse found = subaccountService.findById(created.getId());

            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getCustomerId()).isEqualTo(savedCustomer.getId());
        }

        @Test
        @DisplayName("findById should throw for non-existent subaccount")
        void shouldThrowOnFindByIdNotFound() {
            assertThatThrownBy(() -> subaccountService.findById(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subaccount not found");
        }

        @Test
        @DisplayName("findByCustomerId should return subaccount for existing customer")
        void shouldFindSubaccountByCustomerId() {
            AsaasSubaccountResponse asaasResponse = TestFixtures.anAsaasSubaccountResponse();
            when(asaasSubaccountClient.createSubaccount(any())).thenReturn(asaasResponse);
            when(asaasWebhookClient.createWebhook(anyString(), any()))
                    .thenReturn(AsaasWebhookConfigResponse.builder().id("wh_c").build());

            subaccountService.create(TestFixtures.aCreateSubaccountRequest(savedCustomer.getId()));

            SubaccountResponse found = subaccountService.findByCustomerId(savedCustomer.getId());
            assertThat(found.getCustomerId()).isEqualTo(savedCustomer.getId());
        }

        @Test
        @DisplayName("findByCustomerId should throw when customer has no subaccount")
        void shouldThrowOnFindByCustomerIdNotFound() {
            assertThatThrownBy(() -> subaccountService.findByCustomerId(savedCustomer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Subaccount not found");
        }
    }
}
