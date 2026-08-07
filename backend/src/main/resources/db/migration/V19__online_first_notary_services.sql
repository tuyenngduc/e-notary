INSERT INTO document_types (code, name, description, source, allowed_file_group, is_system, sort_order)
VALUES
    ('PERSONAL_COMMITMENT_DOCUMENT', 'Văn bản cam kết cá nhân', 'Bản dự thảo cam kết hoặc nội dung cam kết cá nhân cần công chứng', 'USER_UPLOAD', 'DOCUMENT', TRUE, 120),
    ('SOURCE_DOCUMENT', 'Tài liệu gốc cần chứng thực', 'Bản chụp hoặc bản scan giấy tờ gốc để công chứng viên đối chiếu qua phiên trực tuyến', 'USER_UPLOAD', 'DOCUMENT', TRUE, 130),
    ('SIGNATURE_DOCUMENT', 'Văn bản cần xác nhận chữ ký', 'Đơn từ hoặc văn bản người dân cần ký và xác nhận chữ ký', 'USER_UPLOAD', 'DOCUMENT', TRUE, 140),
    ('CIVIL_AGREEMENT_DOCUMENT', 'Văn bản thỏa thuận dân sự', 'Bản dự thảo thỏa thuận hợp tác, hợp đồng dịch vụ hoặc cam kết dân sự', 'USER_UPLOAD', 'DOCUMENT', TRUE, 150)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    source = EXCLUDED.source,
    allowed_file_group = EXCLUDED.allowed_file_group,
    is_system = TRUE,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

WITH removed_templates AS (
    SELECT t.id
    FROM contract_templates t
    JOIN notary_service_types s ON s.id = t.service_type_id
    WHERE s.service_code IN ('TRANSFER_OF_PROPERTY', 'MARRIAGE_CONTRACT', 'BUSINESS_CONTRACT', 'OTHER')
)
UPDATE notary_requests
SET selected_template_id = NULL
WHERE selected_template_id IN (SELECT id FROM removed_templates);

DELETE FROM contract_templates
WHERE service_type_id IN (
    SELECT id
    FROM notary_service_types
    WHERE service_code IN ('TRANSFER_OF_PROPERTY', 'MARRIAGE_CONTRACT', 'BUSINESS_CONTRACT', 'OTHER')
);

DELETE FROM notary_service_types
WHERE service_code IN ('TRANSFER_OF_PROPERTY', 'MARRIAGE_CONTRACT', 'BUSINESS_CONTRACT', 'OTHER');

INSERT INTO notary_service_types (id, service_code, name, base_price, description, is_active)
VALUES
    (gen_random_uuid(), 'POWER_OF_ATTORNEY', 'Giấy ủy quyền điện tử', 200000, 'Ủy quyền nộp hồ sơ, nhận giấy tờ hoặc thực hiện thủ tục hành chính; phù hợp xác thực từ xa một bên với công chứng viên.', TRUE),
    (gen_random_uuid(), 'PERSONAL_COMMITMENT', 'Văn bản cam kết cá nhân', 150000, 'Cam kết độc thân, cam kết trách nhiệm, cam kết tài sản hoặc bảo lãnh cá nhân; quy trình online gọn, dễ lưu vết.', TRUE),
    (gen_random_uuid(), 'SIGNATURE_CERTIFICATION', 'Xác nhận chữ ký điện tử', 120000, 'Xác thực người ký qua video call, ghi nhận ý chí tự nguyện và công chứng viên ký số xác nhận.', TRUE),
    (gen_random_uuid(), 'E_COPY_CERTIFICATION', 'Chứng thực bản sao điện tử', 100000, 'Đối chiếu CCCD, bằng cấp, giấy tờ cá nhân qua phiên trực tuyến và ký số bản chứng thực.', TRUE),
    (gen_random_uuid(), 'WILL', 'Di chúc điện tử', 600000, 'Lập và xác thực di chúc qua video call, phù hợp lưu trữ lâu dài và kiểm tra toàn vẹn bằng Blockchain.', TRUE),
    (gen_random_uuid(), 'LOAN_AGREEMENT', 'Hợp đồng vay mượn điện tử', 300000, 'Hợp đồng vay tiền hoặc mượn tài sản có xác nhận ý chí, bằng chứng video và ký số nhiều bên khi cần.', TRUE),
    (gen_random_uuid(), 'CIVIL_AGREEMENT', 'Thỏa thuận dân sự điện tử', 250000, 'Hợp đồng dịch vụ, thỏa thuận hợp tác hoặc cam kết dân sự có khả năng số hóa cao.', TRUE)
ON CONFLICT (service_code) DO UPDATE SET
    name = EXCLUDED.name,
    base_price = EXCLUDED.base_price,
    description = EXCLUDED.description,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM notary_service_document_requirements
WHERE service_type_id IN (
    SELECT id
    FROM notary_service_types
    WHERE service_code IN (
        'POWER_OF_ATTORNEY',
        'PERSONAL_COMMITMENT',
        'SIGNATURE_CERTIFICATION',
        'E_COPY_CERTIFICATION',
        'WILL',
        'LOAN_AGREEMENT',
        'CIVIL_AGREEMENT'
    )
);

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), s.id, r.doc_type, r.sort_order
FROM notary_service_types s
JOIN (
    VALUES
        ('POWER_OF_ATTORNEY', 'REQUEST_FORM', 5),
        ('POWER_OF_ATTORNEY', 'ID_CARD', 10),
        ('POWER_OF_ATTORNEY', 'AUTHORIZATION_DOCUMENT', 20),
        ('POWER_OF_ATTORNEY', 'DRAFT_CONTRACT', 30),

        ('PERSONAL_COMMITMENT', 'REQUEST_FORM', 5),
        ('PERSONAL_COMMITMENT', 'ID_CARD', 10),
        ('PERSONAL_COMMITMENT', 'PERSONAL_COMMITMENT_DOCUMENT', 20),
        ('PERSONAL_COMMITMENT', 'OTHER_RELATED_DOCUMENT', 40),

        ('SIGNATURE_CERTIFICATION', 'REQUEST_FORM', 5),
        ('SIGNATURE_CERTIFICATION', 'ID_CARD', 10),
        ('SIGNATURE_CERTIFICATION', 'SIGNATURE_DOCUMENT', 20),

        ('E_COPY_CERTIFICATION', 'REQUEST_FORM', 5),
        ('E_COPY_CERTIFICATION', 'ID_CARD', 10),
        ('E_COPY_CERTIFICATION', 'SOURCE_DOCUMENT', 20),

        ('WILL', 'REQUEST_FORM', 5),
        ('WILL', 'ID_CARD', 10),
        ('WILL', 'DRAFT_CONTRACT', 20),
        ('WILL', 'INHERITANCE_DOCUMENT', 30),
        ('WILL', 'MARITAL_STATUS_PROOF', 40),

        ('LOAN_AGREEMENT', 'REQUEST_FORM', 5),
        ('LOAN_AGREEMENT', 'ID_CARD', 10),
        ('LOAN_AGREEMENT', 'DRAFT_CONTRACT', 20),
        ('LOAN_AGREEMENT', 'OTHER_RELATED_DOCUMENT', 40),

        ('CIVIL_AGREEMENT', 'REQUEST_FORM', 5),
        ('CIVIL_AGREEMENT', 'ID_CARD', 10),
        ('CIVIL_AGREEMENT', 'CIVIL_AGREEMENT_DOCUMENT', 20),
        ('CIVIL_AGREEMENT', 'OTHER_RELATED_DOCUMENT', 40)
) AS r(service_code, doc_type, sort_order)
    ON r.service_code = s.service_code
ON CONFLICT (service_type_id, doc_type) DO UPDATE SET
    sort_order = EXCLUDED.sort_order;
