# Finance Manager — Modelagem de Domínio e Banco de Dados (MVP 1.0)

## 1. Objetivo e princípios

Este documento especifica o modelo de dados da versão 1.0 do Finance Manager. Ele é a referência para as entidades de domínio, migrações Flyway, validações de entrada e regras de negócio.

### Princípios adotados

- **UUID como identificador:** evita expor sequências previsíveis e simplifica referências externas.
- **Valores financeiros em `NUMERIC(19,2)`:** representa moeda sem imprecisão de ponto flutuante.
- **Valor sempre positivo:** receita ou despesa é determinada pelo tipo, nunca pelo sinal do valor.
- **Dados do usuário são isolados:** qualquer recurso financeiro pertence a um único usuário.
- **Integridade em camadas:** DTO valida formato, caso de uso aplica regra de negócio e PostgreSQL garante invariantes estruturais.
- **Histórico preservado:** conta ou categoria já usada em transações não pode ser excluída no MVP. Isso elimina registros financeiros órfãos e mantém os resumos reproduzíveis.
- **Saldos derivados:** não existe coluna de saldo atual. O saldo é calculado a partir de `initial_balance` e das transações; assim não há risco de saldo materializado ficar inconsistente.

## 2. Convenções gerais

| Item | Convenção |
| --- | --- |
| Banco e schema | PostgreSQL, schema padrão `public` no MVP. |
| Tabelas e colunas | `snake_case`, nomes no plural. |
| IDs | `UUID`, gerado na aplicação ou pelo banco, conforme padrão único definido no projeto. |
| Data e hora técnica | `TIMESTAMP WITH TIME ZONE` (`timestamptz`) em UTC. |
| Data financeira | `DATE`; a transação ocorre em uma data de calendário, sem horário. |
| Valores monetários | `NUMERIC(19,2)`, sem símbolo de moeda. O MVP assume BRL implicitamente. |
| Enums persistidos | Texto com `CHECK`; nunca ordinal numérico. |
| Exclusão | Física apenas quando não há histórico; `ON DELETE RESTRICT` para vínculos financeiros. |
| Auditoria mínima | `created_at` e `updated_at` em todas as entidades persistentes. |

## 3. Entidades

## 3.1 Usuário (`users`)

Representa a identidade autenticável e o proprietário dos recursos financeiros.

| Atributo | Tipo PostgreSQL | Obrigatório | Validações e significado |
| --- | --- | --- | --- |
| `id` | `uuid` | Sim | Identificador imutável e chave primária. |
| `name` | `varchar(100)` | Sim | Nome de exibição; remover espaços externos; entre 2 e 100 caracteres após normalização. |
| `email` | `varchar(254)` | Sim | E-mail válido, normalizado para minúsculas e sem espaços externos. Único sem distinguir maiúsculas/minúsculas. |
| `password_hash` | `varchar(255)` | Sim | Somente hash BCrypt ou Argon2id. Nunca aceitar hash do cliente, retornar pela API ou registrar em log. |
| `created_at` | `timestamptz` | Sim | Instante de criação em UTC. |
| `updated_at` | `timestamptz` | Sim | Instante da última atualização em UTC. |

### Validações de cadastro e atualização

- `name` é obrigatório e não pode conter apenas espaços.
- `email` é obrigatório e precisa obedecer a formato de e-mail aceito pela API.
- A senha em texto puro é exigida somente no cadastro e possui entre 8 e 72 caracteres; deve conter ao menos uma letra e um número. O limite de 72 caracteres vale quando BCrypt for adotado.
- O `password_hash` não recebe validação de entrada, pois é criado internamente após a senha ser validada.

### Regras de negócio

- Não pode existir mais de um usuário para o mesmo e-mail normalizado.
- Login compara a senha em texto puro ao hash persistido; nunca há descriptografia de senha.
- O `id` do usuário autenticado vem exclusivamente do JWT validado, não de parâmetros ou corpo da requisição.

## 3.2 Conta financeira (`financial_accounts`)

Representa o local em que o dinheiro é mantido. Não representa cartão de crédito no MVP, porque fatura, limite e ciclo de fechamento demandariam regras próprias.

| Atributo | Tipo PostgreSQL | Obrigatório | Validações e significado |
| --- | --- | --- | --- |
| `id` | `uuid` | Sim | Chave primária imutável. |
| `user_id` | `uuid` | Sim | Proprietário da conta. |
| `name` | `varchar(100)` | Sim | Nome identificador, de 2 a 100 caracteres após normalização. |
| `type` | `varchar(20)` | Sim | `CASH`, `CHECKING` ou `SAVINGS`. |
| `initial_balance` | `numeric(19,2)` | Sim | Saldo de abertura; pode ser negativo, zero ou positivo. |
| `created_at` | `timestamptz` | Sim | Instante de criação. |
| `updated_at` | `timestamptz` | Sim | Instante da última atualização. |

### Regras de negócio

- A conta só pode ser visualizada, alterada ou excluída por seu proprietário.
- O tipo deve pertencer ao conjunto permitido no MVP.
- Não há unicidade obrigatória de nome de conta por usuário, pois contas distintas podem ter o mesmo apelido. A interface pode alertar sobre duplicidade, mas não deve impedir o cadastro.
- Uma conta com ao menos uma transação não pode ser excluída.
- Alterar `initial_balance` é permitido apenas se a conta não possuir transações. Após o primeiro lançamento, uma correção financeira deve ocorrer por nova transação, preservando a rastreabilidade do saldo.

## 3.3 Categoria (`categories`)

Classifica uma transação como receita ou despesa. A categoria é sempre pessoal; não há catálogo global no MVP.

| Atributo | Tipo PostgreSQL | Obrigatório | Validações e significado |
| --- | --- | --- | --- |
| `id` | `uuid` | Sim | Chave primária imutável. |
| `user_id` | `uuid` | Sim | Proprietário da categoria. |
| `name` | `varchar(80)` | Sim | Nome de 2 a 80 caracteres após normalização. |
| `transaction_type` | `varchar(10)` | Sim | `INCOME` ou `EXPENSE`. |
| `created_at` | `timestamptz` | Sim | Instante de criação. |
| `updated_at` | `timestamptz` | Sim | Instante da última atualização. |

### Regras de negócio

- O usuário só administra as próprias categorias.
- O nome é único por usuário e por tipo: um usuário pode ter `Salário` como receita e `Salário` como despesa, mas não duas categorias de receita com esse nome.
- A categoria só pode ser vinculada a transação do mesmo tipo.
- Categoria já usada por uma ou mais transações não pode ser excluída no MVP.
- Alterar o tipo de uma categoria que já possui transações é proibido. Alterar o nome é permitido, pois não muda a semântica dos lançamentos existentes.

## 3.4 Transação (`transactions`)

Representa um evento financeiro já ocorrido: uma receita ou despesa. Não é transação técnica de banco e não representa transferência ou recorrência, ambos fora do escopo.

| Atributo | Tipo PostgreSQL | Obrigatório | Validações e significado |
| --- | --- | --- | --- |
| `id` | `uuid` | Sim | Chave primária imutável. |
| `user_id` | `uuid` | Sim | Dono do lançamento; reforça isolamento e é fonte dos filtros. |
| `account_id` | `uuid` | Sim | Conta que recebeu ou sofreu a movimentação. |
| `category_id` | `uuid` | Sim | Categoria de classificação. |
| `type` | `varchar(10)` | Sim | `INCOME` ou `EXPENSE`. |
| `amount` | `numeric(19,2)` | Sim | Valor estritamente maior que zero. |
| `occurred_on` | `date` | Sim | Data em que a movimentação ocorreu. |
| `description` | `varchar(255)` | Não | Observação livre; se informada, entre 1 e 255 caracteres após remover espaços externos. |
| `created_at` | `timestamptz` | Sim | Instante de criação. |
| `updated_at` | `timestamptz` | Sim | Instante da última atualização. |

### Regras de negócio

- Só o proprietário pode criar, ler, alterar ou excluir a transação.
- Conta e categoria são obrigatórias, devem existir e pertencer ao mesmo usuário da transação.
- O tipo da transação precisa ser igual ao `transaction_type` da categoria.
- `amount` deve ser maior que zero; a semântica de entrada ou saída é definida por `type`.
- `occurred_on` não pode ser futura no MVP. O sistema registra fatos financeiros realizados; previsão está fora de escopo.
- Na atualização, todos os invariantes acima são revalidados.
- Exclusão remove a transação fisicamente. Como não há auditoria ou fechamento mensal no MVP, isso é mais simples e coerente que uma exclusão lógica. A operação deve recalcular naturalmente qualquer resumo posterior.
- A listagem padrão é por `occurred_on` decrescente e, para desempate, `created_at` decrescente.

## 4. Enumerações de domínio

### Tipo de conta: `account_type`

| Valor | Significado |
| --- | --- |
| `CASH` | Dinheiro físico. |
| `CHECKING` | Conta corrente. |
| `SAVINGS` | Conta poupança. |

### Tipo de transação: `transaction_type`

| Valor | Efeito no resumo e saldo |
| --- | --- |
| `INCOME` | Soma ao total de receitas e ao saldo da conta. |
| `EXPENSE` | Soma ao total de despesas e subtrai do saldo da conta. |

## 5. Relacionamentos e cardinalidades

| Origem | Relacionamento | Destino | Regra |
| --- | --- | --- | --- |
| Usuário | 1 para N | Conta financeira | Toda conta tem exatamente um proprietário. |
| Usuário | 1 para N | Categoria | Toda categoria tem exatamente um proprietário. |
| Usuário | 1 para N | Transação | Toda transação é atribuída a um proprietário. |
| Conta financeira | 1 para N | Transação | Uma transação referencia exatamente uma conta. |
| Categoria | 1 para N | Transação | Uma transação referencia exatamente uma categoria. |

Não há relacionamento direto entre conta e categoria. Elas se encontram somente por meio da transação.

## 6. Constraints de banco de dados

As constraints abaixo são requisitos da migração Flyway inicial. Elas complementam, mas não substituem, a validação de domínio.

### `users`

| Nome sugerido | Tipo | Regra |
| --- | --- | --- |
| `pk_users` | Primary key | `id`. |
| `uq_users_email_normalized` | Unique | E-mail único sem diferença entre maiúsculas e minúsculas. Implementar com índice único em `lower(email)` ou `citext`. |
| `ck_users_name_not_blank` | Check | Nome contém ao menos um caractere não branco. |
| `ck_users_email_not_blank` | Check | E-mail não vazio após `trim`. |
| `ck_users_password_hash_not_blank` | Check | Hash não vazio. |

### `financial_accounts`

| Nome sugerido | Tipo | Regra |
| --- | --- | --- |
| `pk_financial_accounts` | Primary key | `id`. |
| `fk_accounts_user` | Foreign key | `user_id` referencia `users(id)` com `ON DELETE RESTRICT`. |
| `uq_accounts_id_user` | Unique | Par `(id, user_id)`; habilita chave estrangeira composta em transações. |
| `ck_accounts_name_not_blank` | Check | Nome não vazio após `trim`. |
| `ck_accounts_type` | Check | Tipo em `CASH`, `CHECKING`, `SAVINGS`. |
| `ck_accounts_initial_balance_scale` | Tipo/escala | Garantida por `numeric(19,2)`; saldo negativo é permitido. |

### `categories`

| Nome sugerido | Tipo | Regra |
| --- | --- | --- |
| `pk_categories` | Primary key | `id`. |
| `fk_categories_user` | Foreign key | `user_id` referencia `users(id)` com `ON DELETE RESTRICT`. |
| `uq_categories_id_user` | Unique | Par `(id, user_id)`; habilita chave estrangeira composta em transações. |
| `uq_categories_user_type_name` | Unique | `(user_id, transaction_type, lower(name))`; evita duplicação lógica dentro do tipo. |
| `ck_categories_name_not_blank` | Check | Nome não vazio após `trim`. |
| `ck_categories_transaction_type` | Check | Tipo em `INCOME`, `EXPENSE`. |

### `transactions`

| Nome sugerido | Tipo | Regra |
| --- | --- | --- |
| `pk_transactions` | Primary key | `id`. |
| `fk_transactions_user` | Foreign key | `user_id` referencia `users(id)` com `ON DELETE RESTRICT`. |
| `fk_transactions_account_same_user` | Foreign key composta | `(account_id, user_id)` referencia `(id, user_id)` de `financial_accounts`, com `ON DELETE RESTRICT`. |
| `fk_transactions_category_same_user` | Foreign key composta | `(category_id, user_id)` referencia `(id, user_id)` de `categories`, com `ON DELETE RESTRICT`. |
| `ck_transactions_type` | Check | Tipo em `INCOME`, `EXPENSE`. |
| `ck_transactions_amount_positive` | Check | `amount > 0`. |
| `ck_transactions_description_not_blank` | Check | Descrição nula ou não vazia após `trim`. |

### Invariante que requer aplicação

O PostgreSQL não consegue, com uma `CHECK` convencional, comparar o tipo da transação com o tipo da categoria em outra tabela. Portanto, a regra `transaction.type = category.transaction_type` deve ser obrigatoriamente verificada no caso de uso, dentro da mesma transação de banco.

Um trigger poderia reforçar essa regra, mas não é recomendado para o MVP: aumentaria complexidade e duplicaria uma regra de domínio que precisa retornar erro de negócio claro. A consistência de propriedade, por outro lado, é garantida pelas chaves estrangeiras compostas e também validada pela aplicação.

## 7. Índices recomendados

| Tabela | Índice | Finalidade |
| --- | --- | --- |
| `users` | único em `lower(email)` | Cadastro e login por e-mail normalizado. |
| `financial_accounts` | `user_id` | Listar contas do usuário. |
| `categories` | `user_id` | Listar categorias do usuário. |
| `transactions` | `(user_id, occurred_on desc, created_at desc)` | Listagem padrão paginada e filtro por período. |
| `transactions` | `(user_id, account_id, occurred_on desc)` | Filtro por conta e período. |
| `transactions` | `(user_id, category_id, occurred_on desc)` | Filtro por categoria e período. |
| `transactions` | `(user_id, type, occurred_on desc)` | Filtro por tipo e cálculo de resumo. |

Os índices compostos foram escolhidos para iniciar com os filtros explicitamente previstos. Devem ser revisados com planos de execução quando houver dados reais; não adicionar índices apenas por precaução.

## 8. Regras de negócio consolidadas

| ID | Regra |
| --- | --- |
| RN-01 | E-mail é único de forma case-insensitive. |
| RN-02 | Senha nunca é persistida, retornada ou registrada em texto puro. |
| RN-03 | Operações financeiras usam o usuário extraído do JWT; `user_id` não é um campo controlado pelo cliente. |
| RN-04 | Conta, categoria e transação só podem ser acessadas pelo proprietário. |
| RN-05 | Transação possui conta e categoria existentes do mesmo usuário. |
| RN-06 | Tipo de transação deve coincidir com o tipo da categoria. |
| RN-07 | Valor da transação é positivo e possui no máximo duas casas decimais. |
| RN-08 | Data da transação não pode ser futura. |
| RN-09 | Conta com transações não pode ser excluída nem ter saldo inicial alterado. |
| RN-10 | Categoria com transações não pode ser excluída nem ter o tipo alterado. |
| RN-11 | A exclusão de transação é física e atualiza o resultado de consultas futuras. |
| RN-12 | O saldo de uma conta é calculado: saldo inicial + receitas − despesas. |
| RN-13 | O resumo do período considera transações cuja `occurred_on` está entre as datas inicial e final, inclusive. |
| RN-14 | A data inicial de filtro deve ser menor ou igual à data final. |

## 9. DER textual

```text
┌──────────────────────────────┐
│ users                        │
├──────────────────────────────┤
│ PK id : UUID                 │
│    name : VARCHAR(100)       │
│ UQ email : VARCHAR(254)      │
│    password_hash : VARCHAR   │
│    created_at : TIMESTAMPTZ  │
│    updated_at : TIMESTAMPTZ  │
└──────────────┬───────────────┘
               │ 1
       ┌───────┼───────────────────┐
       │       │                   │
       │ N     │ N                 │ N
┌──────▼──────────────┐  ┌─────────▼────────────┐
│ financial_accounts  │  │ categories           │
├─────────────────────┤  ├──────────────────────┤
│ PK id               │  │ PK id                │
│ FK user_id ─────────┼──┼─> users.id           │
│    name             │  │    name              │
│    type             │  │    transaction_type  │
│    initial_balance  │  │    created_at        │
│    created_at       │  │    updated_at        │
│    updated_at       │  └─────────┬────────────┘
└──────────┬──────────┘            │ 1
           │ 1                     │
           │                       │ N
           │ N                     │
           │        ┌──────────────▼────────────┐
           └───────>│ transactions               │
                    ├───────────────────────────┤
                    │ PK id : UUID              │
                    │ FK user_id ──────────────>│ users.id
                    │ FK account_id ───────────>│ financial_accounts.id
                    │ FK category_id ──────────>│ categories.id
                    │    type                    │
                    │    amount                  │
                    │    occurred_on             │
                    │    description (nullable)  │
                    │    created_at              │
                    │    updated_at              │
                    └───────────────────────────┘

Integridade adicional: (transactions.account_id, transactions.user_id)
referencia (financial_accounts.id, financial_accounts.user_id), e
(transactions.category_id, transactions.user_id) referencia
(categories.id, categories.user_id).
```

## 10. Derivações de consulta — não persistidas

| Informação | Fórmula |
| --- | --- |
| Saldo de uma conta | `initial_balance + Σ INCOME − Σ EXPENSE` das transações da conta. |
| Total de receitas no período | `Σ amount` onde `type = INCOME` e `occurred_on` está no intervalo. |
| Total de despesas no período | `Σ amount` onde `type = EXPENSE` e `occurred_on` está no intervalo. |
| Saldo líquido no período | total de receitas − total de despesas. |

Esses dados serão obtidos por consulta agregada. Para o porte do MVP, isso é mais correto e simples do que manter tabelas de saldo ou resumo materializado.

## 11. Ordem de migrações Flyway sugerida

| Versão | Conteúdo |
| --- | --- |
| `V1` | Extensão necessária para UUID, tabela `users`, constraints e índice de e-mail. |
| `V2` | Tabela `financial_accounts`, chave estrangeira para usuários e índice de proprietário. |
| `V3` | Tabela `categories`, constraints de tipo/unicidade e índice de proprietário. |
| `V4` | Tabela `transactions`, foreign keys compostas, checks e índices de filtros. |

As migrações são imutáveis depois de aplicadas. Mudanças de modelo são feitas por uma nova versão, inclusive em correções de constraint ou índice.
