ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS payments_request_id_fkey;

ALTER TABLE payments
    ADD CONSTRAINT payments_request_id_fkey
    FOREIGN KEY (request_id)
    REFERENCES notary_requests(request_id)
    ON DELETE CASCADE;
