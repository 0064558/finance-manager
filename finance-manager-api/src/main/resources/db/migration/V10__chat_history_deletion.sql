ALTER TABLE users ADD COLUMN chat_history_revision bigint NOT NULL DEFAULT 0;

-- Retain only identifiers and links after erasure so retries cannot restore
-- deleted messages or turn an old draft key into a new transaction.
ALTER TABLE transaction_chat_history ADD COLUMN deleted boolean NOT NULL DEFAULT false;
