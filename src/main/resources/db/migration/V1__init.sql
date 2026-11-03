CREATE TABLE comms_user (
  id VARCHAR(36) PRIMARY KEY,
  tenant VARCHAR(64) NOT NULL,
  username VARCHAR(64) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at BIGINT NOT NULL,
  CONSTRAINT uq_user_tenant_username UNIQUE (tenant, username)
);

CREATE TABLE auth_token (
  token_hash CHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at BIGINT NOT NULL,
  CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES comms_user(id) ON DELETE CASCADE,
  INDEX idx_token_expiry (expires_at)
);

CREATE TABLE conversation (
  id VARCHAR(36) PRIMARY KEY,
  tenant VARCHAR(64) NOT NULL,
  title VARCHAR(120) NOT NULL,
  last_seq BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  INDEX idx_conversation_tenant_created (tenant, created_at)
);

CREATE TABLE conversation_member (
  conversation_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  read_seq BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (conversation_id, user_id),
  CONSTRAINT fk_member_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES comms_user(id) ON DELETE CASCADE,
  INDEX idx_member_user (user_id)
);

CREATE TABLE chat_message (
  id VARCHAR(36) PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  sender_id VARCHAR(36) NOT NULL,
  client_message_id VARCHAR(64) NOT NULL,
  seq BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  payload TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  CONSTRAINT uq_message_idempotency UNIQUE (conversation_id, sender_id, client_message_id),
  CONSTRAINT uq_message_sequence UNIQUE (conversation_id, seq),
  CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES comms_user(id),
  INDEX idx_message_history (conversation_id, seq)
);

CREATE TABLE event_outbox (
  id CHAR(36) PRIMARY KEY,
  conversation_id VARCHAR(36) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  payload TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  INDEX idx_outbox_pending (published, created_at)
);
