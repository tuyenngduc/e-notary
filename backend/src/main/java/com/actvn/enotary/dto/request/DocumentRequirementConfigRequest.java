package com.actvn.enotary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DocumentRequirementConfigRequest {
    @NotBlank(message = "Mã dịch vụ không được để trống")
    private String serviceCode;

    @NotBlank(message = "Tên dịch vụ không được để trống")
    private String serviceName;

    @NotNull(message = "Giá cơ bản không được để trống")
    @PositiveOrZero(message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    private String description;

    private Boolean isActive = true;

    private Boolean requiresTemplate = true;

    @NotEmpty(message = "Danh sách giấy tờ bắt buộc không được để trống")
    private List<String> requiredDocTypes;
}
