package com.actvn.enotary.repository;

import com.actvn.enotary.entity.NotaryServiceDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotaryServiceDocumentRequirementRepository extends JpaRepository<NotaryServiceDocumentRequirement, UUID> {
    @Query("""
            select r.docType
            from NotaryServiceDocumentRequirement r
            where r.serviceType.serviceCode = :serviceCode
            order by r.sortOrder asc, r.docType asc
            """)
    List<String> findDocTypesByServiceCode(@Param("serviceCode") String serviceCode);

    List<NotaryServiceDocumentRequirement> findByServiceType_IdOrderBySortOrderAscDocTypeAsc(UUID serviceTypeId);

    void deleteByServiceType_Id(UUID serviceTypeId);

    boolean existsByDocType(String docType);
}
