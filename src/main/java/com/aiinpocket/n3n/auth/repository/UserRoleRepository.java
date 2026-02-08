package com.aiinpocket.n3n.auth.repository;

import com.aiinpocket.n3n.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByRole(String role);

    void deleteByUserId(UUID userId);
}
