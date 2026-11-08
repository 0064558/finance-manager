ALTER TABLE transaction_chat_history
    ALTER COLUMN response_body DROP NOT NULL,
    ADD COLUMN reference_date date,
    ADD COLUMN reply_to uuid UNIQUE REFERENCES transaction_chat_history (id) ON DELETE CASCADE,
    ADD COLUMN request_id uuid,
    ADD CONSTRAINT uq_transaction_chat_request UNIQUE (user_id, request_id);

CREATE TABLE transaction_chat_drafts (
    id uuid PRIMARY KEY,
    entry_id uuid NOT NULL REFERENCES transaction_chat_history (id) ON DELETE CASCADE,
    position integer NOT NULL,
    response_body text NOT NULL,
    discarded boolean NOT NULL DEFAULT false,
    UNIQUE (entry_id, position)
);

-- Preserve the original confirmation key for every existing single suggestion.
INSERT INTO transaction_chat_drafts (id, entry_id, position, response_body)
SELECT id, id, 0, response_body
FROM transaction_chat_history
WHERE response_body::jsonb ->> 'status' = 'SUGESTAO';

UPDATE transaction_chat_history
SET response_body = NULL
WHERE response_body::jsonb ->> 'status' = 'SUGESTAO';
