package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.DocumentResponse;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.DocumentTypeService;
import com.actvn.enotary.service.NotaryRequestService;
import com.actvn.enotary.repository.DocumentRepository;
import com.actvn.enotary.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.PathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentRepository documentRepository;
    private final NotaryRequestService notaryRequestService;

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> replaceDocument(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Document updated = notaryRequestService.replaceDocument(id, userDetails.getUsername(), file);
        return ResponseEntity.ok(ApiResponseUtil.success(DocumentResponse.fromEntity(updated), "Thay thế tài liệu thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> downloadDocument(
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        return serveDocument(authentication, id, false);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<?> viewDocument(
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        return serveDocument(authentication, id, true);
    }

    @GetMapping("/{id}/pages/{pageNumber}/image")
    public ResponseEntity<byte[]> renderDocumentPageImage(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @PathVariable("pageNumber") int pageNumber
    ) {
        if (pageNumber < 1) {
            throw new AppException("Trang PDF không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        enforceDocumentAccess(authentication, doc, true);

        try {
            Path file = notaryRequestService.resolveStoredFilePath(doc.getFilePath());
            if (!Files.exists(file)) {
                throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
            }

            String contentType = doc.getContentType();
            String originalName = doc.getOriginalFileName();
            boolean isPdf = "application/pdf".equalsIgnoreCase(contentType)
                    || file.getFileName().toString().toLowerCase().endsWith(".pdf")
                    || (originalName != null && originalName.toLowerCase().endsWith(".pdf"));
            if (!isPdf) {
                throw new AppException("Chỉ hỗ trợ hiển thị trang để ký với tài liệu PDF", HttpStatus.BAD_REQUEST);
            }

            try (PDDocument pdf = PDDocument.load(file.toFile())) {
                if (pageNumber > pdf.getNumberOfPages()) {
                    throw new AppException("Trang PDF không tồn tại", HttpStatus.BAD_REQUEST);
                }

                PDFRenderer renderer = new PDFRenderer(pdf);
                BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 144, ImageType.RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);

                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(output.toByteArray());
            }
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Lỗi khi hiển thị trang PDF", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<?> serveDocument(Authentication authentication, UUID id, boolean inline) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        enforceDocumentAccess(authentication, doc, inline);

        try {
            Path file = notaryRequestService.resolveStoredFilePath(doc.getFilePath());
            if (!Files.exists(file)) {
                throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
            }

            String contentType = doc.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(file);
            }
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

            PathResource resource = new PathResource(file);
            String downloadName = doc.getOriginalFileName() != null && !doc.getOriginalFileName().isBlank()
                    ? doc.getOriginalFileName()
                    : file.getFileName().toString();
            ContentDisposition.Builder dispositionBuilder = inline
                    ? ContentDisposition.inline()
                    : ContentDisposition.attachment();
            String disposition = dispositionBuilder
                    .filename(downloadName, StandardCharsets.UTF_8)
                    .build()
                    .toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Lỗi khi tải file tài liệu", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void enforceDocumentAccess(Authentication authentication, Document doc, boolean inline) {
        boolean signedDocument = DocumentTypeService.SIGNED_DOCUMENT.equals(doc.getDocType());
        if (!signedDocument && inline) {
            return;
        }

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        NotaryRequest req = doc.getRequest();
        if (req == null) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        String email = userDetails.getUsername();
        boolean isOwner = req.getClient() != null && req.getClient().getEmail().equals(email);
        boolean isAssignedNotary = req.getNotary() != null && req.getNotary().getEmail().equals(email);
        boolean isAdmin = userDetails.getRole() != null && userDetails.getRole().name().equals("ADMIN");
        boolean isNotary = userDetails.getRole() != null && userDetails.getRole().name().equals("NOTARY");
        boolean canInspectProcessingRequest = isNotary && req.getStatus() == RequestStatus.PROCESSING;

        if (!isOwner && !isAssignedNotary && !isAdmin && !canInspectProcessingRequest) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        if (signedDocument && isOwner && !isAdmin && !isAssignedNotary && req.getStatus() != RequestStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }
    }
}
