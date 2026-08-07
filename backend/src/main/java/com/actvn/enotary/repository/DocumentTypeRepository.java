package com.actvn.enotary.repository;

import com.actvn.enotary.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, String> {
    List<DocumentType> findByIsActiveTrueOrderBySortOrderAscCodeAsc();

    List<DocumentType> findAllByOrderBySortOrderAscCodeAsc();
}
