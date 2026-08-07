package com.actvn.enotary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentTypeRequest {
    @NotBlank(message = "Mã loại hồ sơ không được để trống")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$", message = "Mã loại hồ sơ phải là UPPER_SNAKE_CASE")
    private String code;

    @NotBlank(message = "Tên loại hồ sơ không được để trống")
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private String source = "USER_UPLOAD";
    private String allowedFileGroup = "DOCUMENT";
    private Boolean isActive = true;
    private Integer sortOrder = 0;
}
