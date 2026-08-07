DELETE FROM notary_service_document_requirements
WHERE doc_type = 'REQUEST_FORM';

DELETE FROM document_types
WHERE code = 'REQUEST_FORM';
