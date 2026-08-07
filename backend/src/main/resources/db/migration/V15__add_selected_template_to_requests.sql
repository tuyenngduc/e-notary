ALTER TABLE notary_requests
    ADD COLUMN selected_template_id UUID;

ALTER TABLE notary_requests
    ADD CONSTRAINT fk_notary_requests_selected_template
    FOREIGN KEY (selected_template_id) REFERENCES contract_templates(id);

CREATE INDEX idx_notary_requests_selected_template
    ON notary_requests(selected_template_id);
