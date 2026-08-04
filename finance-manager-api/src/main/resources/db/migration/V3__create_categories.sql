CREATE TABLE categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    name varchar(80) NOT NULL,
    transaction_type varchar(10) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_categories_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_categories_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_categories_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE'))
);

CREATE UNIQUE INDEX uq_categories_user_type_name
    ON categories (user_id, transaction_type, lower(name));

CREATE INDEX ix_categories_user_id
    ON categories (user_id);
