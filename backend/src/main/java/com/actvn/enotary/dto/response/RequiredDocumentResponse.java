package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequiredDocumentResponse {
    private String code;
    private String name;
    private String source;
    private String allowedFileGroup;
    private boolean uploaded;
    private boolean missing;
}
