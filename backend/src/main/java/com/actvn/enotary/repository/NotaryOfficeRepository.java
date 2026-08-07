package com.actvn.enotary.repository;

import com.actvn.enotary.entity.NotaryOffice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotaryOfficeRepository extends JpaRepository<NotaryOffice, UUID> {
    Optional<NotaryOffice> findByNameIgnoreCase(String name);

    Page<NotaryOffice> findByIsActiveTrue(Pageable pageable);
}
