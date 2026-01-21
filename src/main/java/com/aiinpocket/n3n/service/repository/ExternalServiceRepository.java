package com.aiinpocket.n3n.service.repository;

import com.aiinpocket.n3n.service.entity.ExternalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalServiceRepository extends JpaRepository<ExternalService, UUID> {

    Page<ExternalService> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Optional<ExternalService> findByIdAndIsDeletedFalse(UUID id);

    Optional<ExternalService> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);

    Page<ExternalService> findByStatusAndIsDeletedFalse(String status, Pageable pageable);
}
