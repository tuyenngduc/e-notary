package com.actvn.enotary.repository;

import com.actvn.enotary.entity.Document;
import com.actvn.enotary.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
	@Query("select distinct d.docType from Document d where d.request.requestId = :requestId")
	List<String> findDocTypesByRequestId(@Param("requestId") UUID requestId);

	@Query("select d from Document d where d.request.requestId = :requestId order by d.createdAt desc")
	List<Document> findByRequest_RequestId(@Param("requestId") UUID requestId);

	@Query("select d from Document d where d.request.requestId = :requestId and d.docType = :docType order by d.createdAt desc")
	List<Document> findByRequestIdAndDocTypeOrderByCreatedAtDesc(@Param("requestId") UUID requestId, @Param("docType") String docType);

	@Query("select d from Document d where lower(d.fileHash) = lower(:fileHash) and d.docType = :docType order by d.createdAt desc")
	List<Document> findTopByFileHashIgnoreCaseAndDocTypeOrderByCreatedAtDesc(
			@Param("fileHash") String fileHash,
			@Param("docType") String docType
	);

	@Query("""
			select d from Document d
			where d.docType = :docType
			  and d.fileHash is not null
			  and d.request.status in :statuses
			  and not exists (
			      select 1 from BlockchainTransaction t
			      where t.document.documentId = d.documentId
			  )
			order by d.createdAt desc
			""")
	List<Document> findSignedDocumentsMissingBlockchainTransaction(
			@Param("docType") String docType,
			@Param("statuses") List<RequestStatus> statuses
	);
}

