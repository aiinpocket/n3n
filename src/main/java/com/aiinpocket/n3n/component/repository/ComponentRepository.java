package com.aiinpocket.n3n.component.repository;

import com.aiinpocket.n3n.component.entity.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComponentRepository extends JpaRepository<Component, UUID> {

    Page<Component> findByIsDeletedFalse(Pageable pageable);

    Page<Component> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);

    Optional<Component> findByIdAndIsDeletedFalse(UUID id);

    Optional<Component> findByNameAndIsDeletedFalse(String name);

    boolean existsByNameAndIsDeletedFalse(String name);
}
