CREATE TABLE wallet_account (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE wallet_ledger_entry (
    id VARCHAR(255) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL REFERENCES wallet_account(id),
    user_id VARCHAR(255) NOT NULL,
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    operation_id VARCHAR(160) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wallet_ledger_user_created ON wallet_ledger_entry(user_id, created_at DESC, id);
