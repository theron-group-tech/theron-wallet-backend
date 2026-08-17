package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateBeneficiaryRequest;
import com.theron.wallet.dto.request.UpdateBeneficiaryRequest;
import com.theron.wallet.dto.response.BeneficiaryResponse;
import com.theron.wallet.entity.Beneficiary;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.BankAccountType;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.BeneficiaryMapper;
import com.theron.wallet.repository.BeneficiaryRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.BeneficiaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public BeneficiaryResponse create(UUID actorUserId, CreateBeneficiaryRequest request) {
        Organization organization = requireActiveOrganization(request.getOrganizationId());
        authorizationService.requirePermission(organization.getId(), actorUserId, PermissionCodes.BENEFICIARIES_CREATE);
        User actor = requireActor(actorUserId);

        String name = requireTrimmed(request.getName(), "name");
        String pixKey = trimToNull(request.getPixKey());
        String bankCode = trimToNull(request.getBankCode());
        String branch = trimToNull(request.getBranch());
        String accountNumber = trimToNull(request.getAccount());
        assertCompleteDestination(pixKey, request.getPixKeyType(), bankCode, branch, accountNumber, request.getAccountType());
        assertUnique(organization.getId(), pixKey, bankCode, branch, accountNumber, null);

        Beneficiary beneficiary = Beneficiary.builder()
                .organization(organization)
                .createdBy(actor)
                .name(name)
                .document(trimToNull(request.getDocument()))
                .pixKey(pixKey)
                .pixKeyType(request.getPixKeyType())
                .bankCode(bankCode)
                .branch(branch)
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .status(BeneficiaryStatus.ACTIVE)
                .build();

        try {
            beneficiary = beneficiaryRepository.saveAndFlush(beneficiary);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Beneficiary already exists for this destination in the organization");
        }

        log.info("Beneficiary created: beneficiaryId={}, organizationId={}", beneficiary.getId(), organization.getId());
        auditLogService.record(
                AuditAction.BENEFICIARY_CREATED,
                organization.getId(),
                actorUserId,
                "Beneficiary",
                beneficiary.getId(),
                Map.of("name", name));
        return BeneficiaryMapper.toResponse(beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> listByOrganization(UUID actorUserId, UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        authorizationService.requirePermission(organizationId, actorUserId, PermissionCodes.BENEFICIARIES_READ);
        return beneficiaryRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(BeneficiaryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse findById(UUID actorUserId, UUID id) {
        Beneficiary beneficiary = getOrThrow(id);
        authorizationService.requirePermission(
                beneficiary.getOrganization().getId(), actorUserId, PermissionCodes.BENEFICIARIES_READ);
        return BeneficiaryMapper.toResponse(beneficiary);
    }

    @Override
    @Transactional
    public BeneficiaryResponse update(UUID actorUserId, UUID id, UpdateBeneficiaryRequest request) {
        Beneficiary beneficiary = getOrThrow(id);
        authorizationService.requirePermission(
                beneficiary.getOrganization().getId(), actorUserId, PermissionCodes.BENEFICIARIES_UPDATE);

        if (request.getName() != null) {
            beneficiary.setName(requireTrimmed(request.getName(), "name"));
        }
        if (request.getDocument() != null) {
            beneficiary.setDocument(trimToNull(request.getDocument()));
        }
        if (request.getPixKey() != null) {
            beneficiary.setPixKey(trimToNull(request.getPixKey()));
        }
        if (request.getPixKeyType() != null) {
            beneficiary.setPixKeyType(request.getPixKeyType());
        }
        if (request.getBankCode() != null) {
            beneficiary.setBankCode(trimToNull(request.getBankCode()));
        }
        if (request.getBranch() != null) {
            beneficiary.setBranch(trimToNull(request.getBranch()));
        }
        if (request.getAccount() != null) {
            beneficiary.setAccountNumber(trimToNull(request.getAccount()));
        }
        if (request.getAccountType() != null) {
            beneficiary.setAccountType(request.getAccountType());
        }
        if (request.getStatus() != null) {
            beneficiary.setStatus(request.getStatus());
        }

        assertCompleteDestination(
                beneficiary.getPixKey(),
                beneficiary.getPixKeyType(),
                beneficiary.getBankCode(),
                beneficiary.getBranch(),
                beneficiary.getAccountNumber(),
                beneficiary.getAccountType());
        assertUnique(
                beneficiary.getOrganization().getId(),
                beneficiary.getPixKey(),
                beneficiary.getBankCode(),
                beneficiary.getBranch(),
                beneficiary.getAccountNumber(),
                beneficiary.getId());

        try {
            beneficiary = beneficiaryRepository.saveAndFlush(beneficiary);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Beneficiary already exists for this destination in the organization");
        }

        log.info("Beneficiary updated: beneficiaryId={}", beneficiary.getId());
        auditLogService.record(
                AuditAction.BENEFICIARY_UPDATED,
                beneficiary.getOrganization().getId(),
                actorUserId,
                "Beneficiary",
                beneficiary.getId(),
                null);
        return BeneficiaryMapper.toResponse(beneficiary);
    }

    @Override
    @Transactional
    public void delete(UUID actorUserId, UUID id) {
        Beneficiary beneficiary = getOrThrow(id);
        authorizationService.requirePermission(
                beneficiary.getOrganization().getId(), actorUserId, PermissionCodes.BENEFICIARIES_DELETE);
        beneficiary.setStatus(BeneficiaryStatus.INACTIVE);
        beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary logically deleted: beneficiaryId={}", id);
        auditLogService.record(
                AuditAction.BENEFICIARY_DELETED,
                beneficiary.getOrganization().getId(),
                actorUserId,
                "Beneficiary",
                beneficiary.getId(),
                null);
    }

    private Beneficiary getOrThrow(UUID id) {
        return beneficiaryRepository.findByIdWithOwner(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", id));
    }

    private Organization requireActiveOrganization(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization is not ACTIVE");
        }
        return organization;
    }

    private User requireActor(UUID actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
    }

    private void assertUnique(
            UUID organizationId,
            String pixKey,
            String bankCode,
            String branch,
            String accountNumber,
            UUID excludeId) {
        if (pixKey != null) {
            boolean duplicate = excludeId == null
                    ? beneficiaryRepository.existsByOrganization_IdAndPixKey(organizationId, pixKey)
                    : beneficiaryRepository.existsByOrganization_IdAndPixKeyAndIdNot(organizationId, pixKey, excludeId);
            if (duplicate) {
                throw new DuplicateResourceException("Beneficiary", "pixKey", pixKey);
            }
        }
        if (bankCode != null) {
            boolean duplicate = excludeId == null
                    ? beneficiaryRepository.existsByOrganization_IdAndBankCodeAndBranchAndAccountNumber(
                            organizationId, bankCode, branch, accountNumber)
                    : beneficiaryRepository.existsByOrganization_IdAndBankCodeAndBranchAndAccountNumberAndIdNot(
                            organizationId, bankCode, branch, accountNumber, excludeId);
            if (duplicate) {
                throw new DuplicateResourceException("Beneficiary", "bankAccount", bankCode + "/" + branch + "/" + accountNumber);
            }
        }
    }

    private static void assertCompleteDestination(
            String pixKey,
            PixKeyType pixKeyType,
            String bankCode,
            String branch,
            String accountNumber,
            BankAccountType accountType) {
        boolean pixComplete = pixKey != null && pixKeyType != null;
        boolean bankComplete = bankCode != null && branch != null && accountNumber != null && accountType != null;
        if (!pixComplete && !bankComplete) {
            throw new InvalidRequestException(
                    "Provide a complete PIX destination (pixKey + pixKeyType) or a complete bank destination (bankCode, branch, account, accountType)");
        }
    }

    private static String requireTrimmed(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
