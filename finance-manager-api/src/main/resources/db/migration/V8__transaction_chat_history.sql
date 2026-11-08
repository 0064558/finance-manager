CREATE TABLE transaction_chat_history (
    sequence_number bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id uuid NOT NULL UNIQUE,
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    prompt varchar(500) NOT NULL,
    response_body text NOT NULL
);

CREATE INDEX idx_transaction_chat_history_user_sequence
    ON transaction_chat_history (user_id, sequence_number);
