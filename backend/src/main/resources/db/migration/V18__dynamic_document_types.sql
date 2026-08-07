CREATE TABLE IF NOT EXISTS document_types (
    code VARCHAR(80) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    source VARCHAR(30) NOT NULL DEFAULT 'USER_UPLOAD',
    allowed_file_group VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_document_type_source CHECK (source IN ('USER_UPLOAD', 'SYSTEM_GENERATED', 'INTERNAL')),
    CONSTRAINT chk_document_type_file_group CHECK (allowed_file_group IN ('DOCUMENT', 'IMAGE', 'VIDEO', 'ANY'))
);

INSERT INTO document_types (code, name, description, source, allowed_file_group, is_system, sort_order)
VALUES
    ('REQUEST_FORM', 'Phiếu yêu cầu công chứng', 'Tự sinh từ thông tin yêu cầu công chứng trên hệ thống', 'SYSTEM_GENERATED', 'DOCUMENT', TRUE, 10),
    ('ID_CARD', 'Giấy tờ tùy thân', 'CCCD, CMND, hộ chiếu hoặc giấy tờ định danh hợp lệ', 'USER_UPLOAD', 'DOCUMENT', TRUE, 20),
    ('REPRESENTATIVE_PROOF', 'Giấy tờ chứng minh tư cách đại diện', 'Quyết định bổ nhiệm, giấy ủy quyền, giấy tờ chứng minh đại diện hợp pháp', 'USER_UPLOAD', 'DOCUMENT', TRUE, 30),
    ('DRAFT_CONTRACT', 'Dự thảo hợp đồng/giao dịch', 'Bản dự thảo hợp đồng, giao dịch hoặc văn bản cần công chứng', 'USER_UPLOAD', 'DOCUMENT', TRUE, 40),
    ('PROPERTY_PAPER', 'Giấy tờ tài sản', 'Giấy chứng nhận quyền sở hữu, quyền sử dụng hoặc giấy tờ chứng minh tài sản', 'USER_UPLOAD', 'DOCUMENT', TRUE, 50),
    ('MARITAL_STATUS_PROOF', 'Giấy tờ tình trạng hôn nhân', 'Đăng ký kết hôn, xác nhận độc thân, thỏa thuận tài sản vợ chồng', 'USER_UPLOAD', 'DOCUMENT', TRUE, 60),
    ('RESIDENCE_PROOF', 'Giấy tờ cư trú', 'Thông tin cư trú, xác nhận cư trú hoặc giấy tờ tương đương khi nghiệp vụ yêu cầu', 'USER_UPLOAD', 'DOCUMENT', TRUE, 70),
    ('BUSINESS_REGISTRATION', 'Đăng ký doanh nghiệp/tổ chức', 'Giấy chứng nhận đăng ký doanh nghiệp hoặc tài liệu pháp lý của tổ chức', 'USER_UPLOAD', 'DOCUMENT', TRUE, 80),
    ('AUTHORIZATION_DOCUMENT', 'Văn bản ủy quyền', 'Giấy ủy quyền, hợp đồng ủy quyền hoặc tài liệu ủy quyền liên quan', 'USER_UPLOAD', 'DOCUMENT', TRUE, 90),
    ('INHERITANCE_DOCUMENT', 'Giấy tờ thừa kế/nhân thân', 'Di chúc, khai sinh, khai tử, giấy tờ chứng minh quan hệ thừa kế', 'USER_UPLOAD', 'DOCUMENT', TRUE, 100),
    ('OTHER_RELATED_DOCUMENT', 'Giấy tờ khác có liên quan', 'Tài liệu bổ sung theo yêu cầu nghiệp vụ', 'USER_UPLOAD', 'DOCUMENT', TRUE, 110),
    ('SIGNED_DOCUMENT', 'Tài liệu đã ký', 'Tài liệu hoàn tất sau khi ký/xác nhận', 'INTERNAL', 'DOCUMENT', TRUE, 900),
    ('SESSION_VIDEO', 'Video phiên họp', 'Video ghi nhận phiên họp công chứng', 'INTERNAL', 'VIDEO', TRUE, 910),
    ('EVIDENCE_PHOTO', 'Ảnh bằng chứng đối chiếu', 'Ảnh bằng chứng trong phiên đối chiếu qua video', 'INTERNAL', 'IMAGE', TRUE, 920)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    source = EXCLUDED.source,
    allowed_file_group = EXCLUDED.allowed_file_group,
    is_system = TRUE,
    sort_order = EXCLUDED.sort_order;

ALTER TABLE documents DROP CONSTRAINT IF EXISTS chk_doc_type;
ALTER TABLE notary_service_document_requirements DROP CONSTRAINT IF EXISTS chk_service_requirement_doc_type;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'REQUEST_FORM', 5
FROM notary_service_types
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'MARITAL_STATUS_PROOF', 25
FROM notary_service_types
WHERE service_code IN ('TRANSFER_OF_PROPERTY', 'WILL')
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'AUTHORIZATION_DOCUMENT', 30
FROM notary_service_types
WHERE service_code = 'POWER_OF_ATTORNEY'
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'BUSINESS_REGISTRATION', 30
FROM notary_service_types
WHERE service_code = 'BUSINESS_CONTRACT'
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'REPRESENTATIVE_PROOF', 40
FROM notary_service_types
WHERE service_code = 'BUSINESS_CONTRACT'
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_documents_doc_type'
    ) THEN
        ALTER TABLE documents
            ADD CONSTRAINT fk_documents_doc_type FOREIGN KEY (doc_type) REFERENCES document_types(code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_service_requirements_doc_type'
    ) THEN
        ALTER TABLE notary_service_document_requirements
            ADD CONSTRAINT fk_service_requirements_doc_type FOREIGN KEY (doc_type) REFERENCES document_types(code);
    END IF;
END $$;
