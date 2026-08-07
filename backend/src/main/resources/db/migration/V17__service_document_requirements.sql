CREATE TABLE IF NOT EXISTS notary_service_document_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_type_id UUID NOT NULL REFERENCES notary_service_types(id) ON DELETE CASCADE,
    doc_type VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_document_requirement UNIQUE (service_type_id, doc_type),
    CONSTRAINT chk_service_requirement_doc_type CHECK (doc_type IN ('ID_CARD', 'PROPERTY_PAPER', 'DRAFT_CONTRACT'))
);

CREATE INDEX IF NOT EXISTS idx_service_document_requirements_service
    ON notary_service_document_requirements(service_type_id);

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'ID_CARD', 10
FROM notary_service_types
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'DRAFT_CONTRACT', 20
FROM notary_service_types
ON CONFLICT (service_type_id, doc_type) DO NOTHING;

INSERT INTO notary_service_document_requirements (id, service_type_id, doc_type, sort_order)
SELECT gen_random_uuid(), id, 'PROPERTY_PAPER', 15
FROM notary_service_types
WHERE service_code = 'TRANSFER_OF_PROPERTY'
ON CONFLICT (service_type_id, doc_type) DO NOTHING;
