UPDATE notary_service_types
SET name = CASE service_code
    WHEN 'POWER_OF_ATTORNEY' THEN 'Giấy ủy quyền'
    WHEN 'SIGNATURE_CERTIFICATION' THEN 'Xác nhận chữ ký'
    WHEN 'E_COPY_CERTIFICATION' THEN 'Chứng thực bản sao'
    WHEN 'WILL' THEN 'Di chúc'
    WHEN 'LOAN_AGREEMENT' THEN 'Hợp đồng vay mượn'
    WHEN 'CIVIL_AGREEMENT' THEN 'Thỏa thuận dân sự'
    ELSE name
END,
updated_at = CURRENT_TIMESTAMP
WHERE service_code IN (
    'POWER_OF_ATTORNEY',
    'SIGNATURE_CERTIFICATION',
    'E_COPY_CERTIFICATION',
    'WILL',
    'LOAN_AGREEMENT',
    'CIVIL_AGREEMENT'
);
