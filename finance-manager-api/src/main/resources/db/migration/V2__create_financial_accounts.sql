CREATE TABLE financial_accounts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    type varchar(20) NOT NULL,
    initial_balance numeric(19,2) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,

    CONSTRAINT pk_financial_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_accounts_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CASH', 'CHECKING', 'SAVINGS'))
);

CREATE INDEX ix_financial_accounts_user_id
    ON financial_accounts (user_id);
