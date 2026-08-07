package com.actvn.enotary.repository;

import com.actvn.enotary.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SignatureRepository extends JpaRepository<Signature, UUID> {
    Optional<Signature> findByDocument_DocumentIdAndUser_UserId(UUID documentId, UUID userId);

    boolean existsByDocument_DocumentIdAndUser_UserId(UUID documentId, UUID userId);

    long countByDocument_DocumentIdAndIsValidTrue(UUID documentId);
}
