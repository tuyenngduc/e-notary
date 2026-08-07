package com.actvn.enotary.dto.response;

import com.actvn.enotary.enums.RequestStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignVideoDocumentResponse {
    private DocumentResponse signedDocument;
    private boolean clientSigned;
    private boolean notarySigned;
    private boolean completed;
    private RequestStatus requestStatus;
}
