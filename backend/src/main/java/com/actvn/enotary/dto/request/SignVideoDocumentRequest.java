package com.actvn.enotary.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SignVideoDocumentRequest {
    @NotNull
    private UUID documentId;

    @NotBlank
    private String signatureValue;

    private Integer pageNumber;

    @JsonProperty("xPercent")
    private Double xPercent;

    @JsonProperty("yPercent")
    private Double yPercent;

    private Double widthPercent;

    private Double heightPercent;
}
