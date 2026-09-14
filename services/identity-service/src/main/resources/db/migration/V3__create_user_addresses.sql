CREATE TABLE user_addresses (
                                id VARCHAR(36) PRIMARY KEY,
                                user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                recipient_name VARCHAR(120) NOT NULL,
                                phone VARCHAR(20) NOT NULL,
                                province VARCHAR(100) NOT NULL,
                                district VARCHAR(100) NOT NULL,
                                ward VARCHAR(100) NOT NULL,
                                detail_address VARCHAR(255) NOT NULL,
                                is_default BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP,
                                updated_at TIMESTAMP,
                                created_by VARCHAR(255),
                                updated_by VARCHAR(255)
);

CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);
CREATE INDEX idx_user_addresses_user_default ON user_addresses(user_id, is_default);