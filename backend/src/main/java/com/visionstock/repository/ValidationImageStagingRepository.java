package com.visionstock.repository;

import com.visionstock.model.inventory.ValidationImageStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationImageStagingRepository extends JpaRepository<ValidationImageStaging, UUID> {

    List<ValidationImageStaging> findByValidationRequestIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID validationRequestId);
}
