package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.dto.response.UserOrganizationResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.entity.User;

import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public static OrganizationMembershipResponse toMembershipResponse(OrganizationMembership membership) {
        return toMembershipResponse(membership, null);
    }

    public static OrganizationMembershipResponse toMembershipResponse(
            OrganizationMembership membership, List<String> roleCodes) {
        User user = membership.getUser();
        return OrganizationMembershipResponse.builder()
                .id(membership.getId())
                .organizationId(membership.getOrganization().getId())
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .roleCodes(roleCodes)
                .status(membership.getStatus())
                .createdAt(membership.getCreatedAt())
                .updatedAt(membership.getUpdatedAt())
                .build();
    }

    public static UserOrganizationResponse toUserOrganizationResponse(OrganizationMembership membership) {
        Organization org = membership.getOrganization();
        return UserOrganizationResponse.builder()
                .organizationId(org.getId())
                .legalName(org.getLegalName())
                .tradeName(org.getTradeName())
                .organizationStatus(org.getStatus())
                .membershipStatus(membership.getStatus())
                .build();
    }
}
