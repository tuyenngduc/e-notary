package com.actvn.enotary.service;

import com.actvn.enotary.dto.request.NotaryOfficeRequest;
import com.actvn.enotary.entity.NotaryOffice;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.NotaryOfficeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaryOfficeService {
    private final NotaryOfficeRepository repository;

    public Page<NotaryOffice> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<NotaryOffice> getActive(Pageable pageable) {
        return repository.findByIsActiveTrue(pageable);
    }

    public NotaryOffice create(NotaryOfficeRequest request) {
        String name = normalizeRequired(request.getName());
        repository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new AppException("Tên văn phòng đã tồn tại", HttpStatus.BAD_REQUEST);
        });

        NotaryOffice office = new NotaryOffice();
        applyRequest(office, request);
        return repository.save(office);
    }

    public NotaryOffice update(UUID id, NotaryOfficeRequest request) {
        NotaryOffice existing = repository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy văn phòng công chứng", HttpStatus.NOT_FOUND));

        String name = normalizeRequired(request.getName());
        repository.findByNameIgnoreCase(name)
                .filter(office -> !office.getId().equals(id))
                .ifPresent(office -> {
                    throw new AppException("Tên văn phòng đã tồn tại", HttpStatus.BAD_REQUEST);
                });

        applyRequest(existing, request);
        return repository.save(existing);
    }

    public void delete(UUID id) {
        NotaryOffice existing = repository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy văn phòng công chứng", HttpStatus.NOT_FOUND));
        existing.setIsActive(false);
        repository.save(existing);
    }

    private void applyRequest(NotaryOffice office, NotaryOfficeRequest request) {
        office.setName(normalizeRequired(request.getName()));
        office.setAddress(normalizeRequired(request.getAddress()));
        office.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
        office.setWorkingHours(normalizeOptional(request.getWorkingHours()));
        office.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
