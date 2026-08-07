package com.actvn.enotary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class NotaryServiceTypeRequest {
    @NotBlank(message = "Mã dịch vụ không được để trống")
    private String serviceCode;

    @NotBlank(message = "Tên dịch vụ không được để trống")
    private String name;

    @NotNull(message = "Giá cơ bản không được để trống")
    @PositiveOrZero(message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    private String description;
    
    private Boolean isActive = true;

    private Boolean requiresTemplate = true;
}
