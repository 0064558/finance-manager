CREATE TABLE transaction_idempotency (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    idempotency_key varchar(200) NOT NULL,
    request_hash varchar(64) NOT NULL,
    response_body text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idempotency_key)
);

-- The response is a snapshot, not a foreign key to the transaction. Deleting a
-- transaction must not allow a delayed retry to recreate its financial effect.
