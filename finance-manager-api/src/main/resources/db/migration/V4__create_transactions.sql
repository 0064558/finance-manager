CREATE TABLE transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    account_id uuid NOT NULL,
    category_id uuid NOT NULL,
    type varchar(10) NOT NULL,
    amount numeric(19,2) NOT NULL,
    occurred_on date NOT NULL,
    description varchar(255),
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_account_same_user
        FOREIGN KEY (account_id, user_id)
        REFERENCES financial_accounts (id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category_same_user
        FOREIGN KEY (category_id, user_id)
        REFERENCES categories (id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_description_not_blank CHECK (
        description IS NULL OR length(trim(description)) > 0
    )
);

CREATE INDEX ix_transactions_user_occurred_created
    ON transactions (user_id, occurred_on DESC, created_at DESC);

CREATE INDEX ix_transactions_user_account_occurred
    ON transactions (user_id, account_id, occurred_on DESC);

CREATE INDEX ix_transactions_user_category_occurred
    ON transactions (user_id, category_id, occurred_on DESC);

CREATE INDEX ix_transactions_user_type_occurred
    ON transactions (user_id, type, occurred_on DESC);
