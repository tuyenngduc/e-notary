CREATE TABLE IF NOT EXISTS blockchain_transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES notary_requests(request_id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    document_hash VARCHAR(64) NOT NULL,
    transaction_hash VARCHAR(100) NOT NULL UNIQUE,
    block_number BIGINT NOT NULL,
    network_name VARCHAR(100) NOT NULL DEFAULT 'Hyperledger Besu Local',
    chain_id BIGINT NOT NULL DEFAULT 1337,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    node_name VARCHAR(100) NOT NULL DEFAULT 'besu-validator-1',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_blockchain_transactions_document
    ON blockchain_transactions(document_id);

CREATE INDEX IF NOT EXISTS idx_blockchain_transactions_request
    ON blockchain_transactions(request_id);

CREATE INDEX IF NOT EXISTS idx_blockchain_transactions_created_at
    ON blockchain_transactions(created_at DESC);
