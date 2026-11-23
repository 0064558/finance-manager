CREATE TABLE account_transfers (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    source_account_id uuid NOT NULL,
    destination_account_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    occurred_on date NOT NULL,
    description varchar(255),
    idempotency_key varchar(200),
    created_at timestamptz DEFAULT now() NOT NULL,

    CONSTRAINT pk_account_transfers PRIMARY KEY (id),
    CONSTRAINT uq_account_transfers_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_account_transfers_source_same_user
        FOREIGN KEY (source_account_id, user_id)
        REFERENCES financial_accounts (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_transfers_destination_same_user
        FOREIGN KEY (destination_account_id, user_id)
        REFERENCES financial_accounts (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_account_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_account_transfers_description_not_blank CHECK (
        description IS NULL OR length(trim(description)) > 0
    )
);

CREATE INDEX ix_account_transfers_user_occurred_created
    ON account_transfers (user_id, occurred_on DESC, created_at DESC, id DESC);

CREATE INDEX ix_account_transfers_source ON account_transfers (source_account_id);
CREATE INDEX ix_account_transfers_destination ON account_transfers (destination_account_id);
