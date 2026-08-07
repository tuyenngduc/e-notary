CREATE TABLE IF NOT EXISTS notary_offices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    phone_number VARCHAR(30),
    working_hours VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notary_offices_name_lower ON notary_offices (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_notary_offices_active ON notary_offices(is_active);

CREATE TRIGGER update_notary_office_modtime
    BEFORE UPDATE ON notary_offices
    FOR EACH ROW
    EXECUTE PROCEDURE update_modified_column();

INSERT INTO notary_offices (name, address, phone_number, working_hours, is_active)
VALUES
    ('Văn phòng công chứng số 1', 'Số 1 Trần Phú, Ba Đình, Hà Nội', '024 3823 4567', 'Thứ 2 - Thứ 6, 08:00 - 17:00', TRUE)
ON CONFLICT (LOWER(name)) DO NOTHING;
