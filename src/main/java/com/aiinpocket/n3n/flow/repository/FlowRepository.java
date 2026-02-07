package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.Flow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowRepository extends JpaRepository<Flow, UUID> {

    Page<Flow> findByIsDeletedFalse(Pageable pageable);

    Page<Flow> findByCreatedByAndIsDeletedFalse(UUID createdBy, Pageable pageable);

    Optional<Flow> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByNameAndIsDeletedFalse(String name);

    @Query("SELECT f FROM Flow f WHERE f.isDeleted = false AND " +
           "(LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(f.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Flow> searchFlows(@Param("query") String query, Pageable pageable);

    @Query("SELECT f FROM Flow f WHERE f.createdBy = :userId AND f.isDeleted = false AND " +
           "(LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(f.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Flow> searchByCreatedByAndQuery(@Param("userId") UUID userId, @Param("query") String query, Pageable pageable);

    long countByCreatedByAndIsDeletedFalse(UUID createdBy);
}
