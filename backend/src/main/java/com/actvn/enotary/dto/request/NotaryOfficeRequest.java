package com.actvn.enotary.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotaryOfficeRequest {
    @NotBlank(message = "Tên văn phòng không được để trống")
    private String name;

    @NotBlank(message = "Địa chỉ văn phòng không được để trống")
    private String address;

    private String phoneNumber;

    private String workingHours;

    private Boolean isActive = true;
}
