package com.aiinpocket.n3n.template.repository;

import com.aiinpocket.n3n.template.entity.FlowTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowTemplateRepository extends JpaRepository<FlowTemplate, UUID> {

    Page<FlowTemplate> findAllByOrderByUsageCountDesc(Pageable pageable);

    Page<FlowTemplate> findByCategoryOrderByUsageCountDesc(String category, Pageable pageable);

    List<FlowTemplate> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    @Query("SELECT DISTINCT t.category FROM FlowTemplate t ORDER BY t.category")
    List<String> findAllCategories();

    @Query("SELECT t FROM FlowTemplate t WHERE " +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<FlowTemplate> searchTemplates(@Param("query") String query, Pageable pageable);

    @Modifying
    @Query("UPDATE FlowTemplate t SET t.usageCount = t.usageCount + 1 WHERE t.id = :id")
    void incrementUsageCount(@Param("id") UUID id);
}
