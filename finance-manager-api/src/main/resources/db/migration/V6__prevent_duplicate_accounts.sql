-- Do not merge or delete existing financial data. If duplicates already exist,
-- this index deliberately stops migration so they can be reviewed first.
CREATE UNIQUE INDEX uq_accounts_user_name_type_balance
    ON financial_accounts (user_id, btrim(name), type, initial_balance);
