package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FlowImportRepository extends JpaRepository<FlowImport, UUID> {
}
