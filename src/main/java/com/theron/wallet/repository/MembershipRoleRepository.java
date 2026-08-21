package com.theron.wallet.repository;

import com.theron.wallet.entity.MembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRoleRepository extends JpaRepository<MembershipRole, UUID> {

    List<MembershipRole> findByMembershipId(UUID membershipId);

    void deleteByMembershipId(UUID membershipId);

    @Query(value = """
            SELECT COUNT(*)
            FROM membership_role mr
            INNER JOIN organization_membership om ON om.id = mr.membership_id
            INNER JOIN role_permission rp ON rp.role_id = mr.role_id
            INNER JOIN permission p ON p.id = rp.permission_id
            WHERE om.organization_id = :organizationId
              AND om.user_id = :userId
              AND om.status = 'ACTIVE'
              AND p.code = :permissionCode
            """, nativeQuery = true)
    long countPermission(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            @Param("permissionCode") String permissionCode);

    @Query(value = """
            SELECT DISTINCT p.code
            FROM membership_role mr
            INNER JOIN organization_membership om ON om.id = mr.membership_id
            INNER JOIN role_permission rp ON rp.role_id = mr.role_id
            INNER JOIN permission p ON p.id = rp.permission_id
            WHERE om.organization_id = :organizationId
              AND om.user_id = :userId
              AND om.status = 'ACTIVE'
            ORDER BY p.code
            """, nativeQuery = true)
    List<String> findPermissionCodesByOrganizationAndUser(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);

    @Query(value = """
            SELECT DISTINCT r.code
            FROM membership_role mr
            INNER JOIN organization_membership om ON om.id = mr.membership_id
            INNER JOIN role r ON r.id = mr.role_id
            WHERE om.organization_id = :organizationId
              AND om.user_id = :userId
              AND om.status = 'ACTIVE'
            ORDER BY r.code
            """, nativeQuery = true)
    List<String> findRoleCodesByOrganizationAndUser(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);

    @Query("""
            SELECT mr FROM MembershipRole mr
            JOIN FETCH mr.role
            WHERE mr.membership.id = :membershipId
            """)
    List<MembershipRole> findByMembershipIdWithRole(@Param("membershipId") UUID membershipId);

    @Query("""
            SELECT DISTINCT mr.role.id
            FROM MembershipRole mr
            WHERE mr.membership.organization.id = :organizationId
              AND mr.membership.user.id = :userId
              AND mr.membership.status = com.theron.wallet.enums.MembershipStatus.ACTIVE
            """)
    List<UUID> findRoleIdsByOrganizationAndUser(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);

    @Query(value = """
            SELECT DISTINCT om.user_id
            FROM membership_role mr
            INNER JOIN organization_membership om ON om.id = mr.membership_id
            INNER JOIN role_permission rp ON rp.role_id = mr.role_id
            INNER JOIN permission p ON p.id = rp.permission_id
            WHERE om.organization_id = :organizationId
              AND om.status = 'ACTIVE'
              AND p.code = :permissionCode
            """, nativeQuery = true)
    List<UUID> findUserIdsByOrganizationAndPermission(
            @Param("organizationId") UUID organizationId,
            @Param("permissionCode") String permissionCode);

    @Query(value = """
            SELECT om.user_id
            FROM membership_role mr
            INNER JOIN organization_membership om ON om.id = mr.membership_id
            INNER JOIN role r ON r.id = mr.role_id
            WHERE om.organization_id = :organizationId
              AND om.status = 'ACTIVE'
              AND r.code = 'OWNER'
            ORDER BY om.created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findOwnerUserId(@Param("organizationId") UUID organizationId);
}
