CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE transaction_embedding_pilots (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    from_created_at timestamptz NOT NULL,
    from_transaction_id uuid NOT NULL
);

CREATE TABLE transaction_embeddings (
    transaction_id uuid PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    source_hash text NOT NULL CHECK (length(source_hash) = 64),
    embedding_version text NOT NULL,
    embedding vector(768),
    skipped boolean NOT NULL DEFAULT false,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,

    CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
    CHECK (NOT skipped OR embedding IS NULL),
    CHECK ((embedding IS NULL AND NOT skipped) OR lease_token IS NULL)
);

CREATE INDEX ix_transactions_embedding_cohort
    ON transactions (user_id, created_at DESC, id DESC) WHERE description IS NOT NULL;

CREATE INDEX ix_transaction_embeddings_pending
    ON transaction_embeddings (available_at, transaction_id) WHERE embedding IS NULL AND NOT skipped;
