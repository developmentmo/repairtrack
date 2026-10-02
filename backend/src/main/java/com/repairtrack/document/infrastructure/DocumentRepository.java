package com.repairtrack.document.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.document.domain.Document;

/** Append-only: documents are never updated or deleted. */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByRepairEventIdOrderByUploadedAtAsc(UUID repairEventId);

    List<Document> findByVehicleIdOrderByUploadedAtAsc(UUID vehicleId);

    Optional<Document> findFirstByVehicleIdAndSha256OrderByUploadedAtAsc(UUID vehicleId, String sha256);
}
