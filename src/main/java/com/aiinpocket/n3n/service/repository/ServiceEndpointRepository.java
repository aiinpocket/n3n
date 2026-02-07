package com.aiinpocket.n3n.service.repository;

import com.aiinpocket.n3n.service.entity.ServiceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceEndpointRepository extends JpaRepository<ServiceEndpoint, UUID> {

    List<ServiceEndpoint> findByServiceIdOrderByPathAsc(UUID serviceId);

    List<ServiceEndpoint> findByServiceIdAndIsEnabledTrue(UUID serviceId);

    Optional<ServiceEndpoint> findByServiceIdAndMethodAndPath(UUID serviceId, String method, String path);

    int countByServiceId(UUID serviceId);

    @Modifying
    @Query("DELETE FROM ServiceEndpoint e WHERE e.serviceId = :serviceId")
    void deleteAllByServiceId(UUID serviceId);

    @Query(value = "SELECT * FROM service_endpoints e WHERE e.service_id = :serviceId AND CAST(:tag AS TEXT) = ANY(e.tags)", nativeQuery = true)
    List<ServiceEndpoint> findByServiceIdAndTag(UUID serviceId, String tag);
}
