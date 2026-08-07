package com.actvn.enotary.service;

import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaryServiceTypeService {
    private final NotaryServiceTypeRepository repository;
    private final DocumentRequirementConfigService documentRequirementConfigService;

    public Page<NotaryServiceType> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<NotaryServiceType> getActive(Pageable pageable) {
        return repository.findByIsActiveTrue(pageable);
    }

    public NotaryServiceType create(NotaryServiceType request) {
        if (repository.findByServiceCode(request.getServiceCode()).isPresent()) {
            throw new AppException("Mã dịch vụ đã tồn tại", HttpStatus.BAD_REQUEST);
        }
        NotaryServiceType created = repository.save(request);
        documentRequirementConfigService.ensureDefaultRequirements(created);
        return created;
    }

    public NotaryServiceType update(UUID id, NotaryServiceType request) {
        NotaryServiceType existing = repository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy dịch vụ", HttpStatus.NOT_FOUND));
        
        if (!existing.getServiceCode().equals(request.getServiceCode()) && 
            repository.findByServiceCode(request.getServiceCode()).isPresent()) {
            throw new AppException("Mã dịch vụ đã tồn tại", HttpStatus.BAD_REQUEST);
        }

        existing.setServiceCode(request.getServiceCode());
        existing.setName(request.getName());
        existing.setBasePrice(request.getBasePrice());
        existing.setDescription(request.getDescription());
        existing.setIsActive(request.getIsActive());
        existing.setRequiresTemplate(request.getRequiresTemplate() != null ? request.getRequiresTemplate() : true);

        return repository.save(existing);
    }

    public void delete(UUID id) {
        NotaryServiceType existing = repository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy dịch vụ", HttpStatus.NOT_FOUND));
        // Soft delete
        existing.setIsActive(false);
        repository.save(existing);
    }
}
