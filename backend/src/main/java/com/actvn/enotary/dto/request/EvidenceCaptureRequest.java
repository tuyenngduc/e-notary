package com.actvn.enotary.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EvidenceCaptureRequest {
    @NotBlank(message = "Ảnh bằng chứng không được để trống")
    private String imageData;
}
