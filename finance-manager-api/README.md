# Finance Manager API

API para gerenciamento financeiro pessoal.

## Requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose

## Configuração local

Crie o arquivo `.env` a partir do modelo versionado:

```powershell
Copy-Item .env.example .env
```

O ambiente local usa o PostgreSQL executado pelo Docker. A porta `5433` é a porta
da máquina; dentro da rede Docker, o PostgreSQL continua ouvindo na porta `5432`.

Depois de copiar o arquivo, confira as variáveis:

```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

DB_NAME=finance_manager_dev
DB_PORT=5433
DB_URL=jdbc:postgresql://localhost:5433/finance_manager_dev
DB_USERNAME=postgres
DB_PASSWORD=defina_uma_senha_local

JWT_ISSUER=finance-manager-api
JWT_EXPIRATION_SECONDS=3600
JWT_SECRET=gere_um_secret_forte_para_o_ambiente_local
```

O arquivo `.env` fica fora do Git.

`JWT_SECRET` deve ser um valor forte e privado. Não use o valor do exemplo em
produção e não versione segredos reais.

`DB_PASSWORD` é a senha do PostgreSQL. Ela é diferente da senha usada pelos
usuários da API.

## Executar a stack completa com Docker

Valide o arquivo Compose:

```powershell
docker compose config
```

Construa a imagem da API e inicie a API junto com o PostgreSQL:

```powershell
docker compose up -d --build
```

Confira os serviços:

```powershell
docker compose ps
```

A API fica disponível em:

```text
http://localhost:8080
```

O PostgreSQL Dockerizado fica disponível para ferramentas instaladas na máquina,
como pgAdmin, em:

```text
Host: localhost
Port: 5433
Database: finance_manager_dev
Username: postgres
```

A API dentro do Docker acessa o banco pelo nome do serviço:

```text
jdbc:postgresql://postgres:5432/finance_manager_dev
```

Confira os logs:

```powershell
docker compose logs --tail=100 api
docker compose logs --tail=100 postgres
```

Teste o health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado:

```json
{
  "status": "UP"
}
```

Confirme as tabelas criadas pelas migrations:

```powershell
docker compose exec postgres psql -U postgres -d finance_manager_dev -c '\dt'
```

## Executar a API pelo IntelliJ

Para desenvolver com mais rapidez, mantenha somente o PostgreSQL no Docker e
execute a API pelo IntelliJ:

```powershell
docker compose stop api
docker compose up -d postgres
```

Nesse modo, a configuração de execução do IntelliJ deve carregar o arquivo `.env`
ou definir explicitamente:

```text
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5433/finance_manager_dev
DB_USERNAME=postgres
DB_PASSWORD=mesma_senha_do_.env
JWT_ISSUER=finance-manager-api
JWT_EXPIRATION_SECONDS=3600
JWT_SECRET=seu_segredo_local
```

A API executada pelo IntelliJ e a API executada pelo Docker usam o mesmo banco:

```text
IntelliJ: localhost:5433
Docker:   postgres:5432
```

Não execute simultaneamente a API pelo IntelliJ e pelo Docker na porta `8080`.

## Swagger e OpenAPI

Depois de iniciar a aplicacao:

```text
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON v1: http://localhost:8080/api-docs/v1
```

A documentacao OpenAPI considera endpoints sob:

```text
/api/v1/**
```

## Autenticacao JWT

A autenticacao da API usa Bearer JWT. O fluxo principal e:

1. O cliente cadastra um usuario em `POST /api/v1/auth/register`.
2. O cliente faz login em `POST /api/v1/auth/login`.
3. A API retorna um `accessToken`.
4. O cliente envia esse token nas rotas protegidas usando o header `Authorization`.

Formato do header:

```http
Authorization: Bearer seu_access_token
```

Endpoints publicos:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /swagger-ui.html
GET  /api-docs/v1
```

Endpoint protegido disponivel nesta fase:

```text
GET /api/v1/auth/me
```

Exemplo de cadastro:

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "name": "Rodrigo",
  "email": "rodrigo@email.com",
  "password": "senha1234"
}
```

Exemplo de login:

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "rodrigo@email.com",
  "password": "senha1234"
}
```

Resposta esperada do login:

```json
{
  "accessToken": "jwt_gerado_pela_api",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "uuid-do-usuario",
    "name": "Rodrigo",
    "email": "rodrigo@email.com",
    "createdAt": "2026-08-05T17:00:00-03:00"
  }
}
```

Para usar pelo Swagger UI, acesse `http://localhost:8080/swagger-ui.html`, faca login pelo endpoint `/auth/login`, copie o `accessToken`, clique em `Authorize` e informe somente o token. O Swagger ja aplica o prefixo Bearer no header.

Respostas importantes:

| Situacao | Status | Codigo |
| --- | --- | --- |
| Dados invalidos no cadastro ou login | `400` | `VALIDATION_FAILED` |
| E-mail ja cadastrado | `409` | `EMAIL_ALREADY_REGISTERED` |
| Login com credenciais invalidas | `401` | `INVALID_CREDENTIALS` |
| Token ausente, invalido ou expirado | `401` | `AUTHENTICATION_REQUIRED` |

Erros da API usam `application/problem+json`. Além dos campos padrão de
`ProblemDetail` (`type`, `title`, `status`, `detail` e `instance`), a resposta
inclui `code`, `timestamp` e `traceId`. Erros de validação também incluem
`fields`, com o nome e a mensagem de cada campo inválido:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/auth/register",
  "code": "VALIDATION_FAILED",
  "timestamp": "2026-08-16T18:00:00Z",
  "traceId": "4f8f8f91-0d4b-4f56-9a34-6adf4e4b2db0",
  "fields": [
    { "field": "password", "message": "A senha deve conter ao menos uma letra e um número" }
  ]
}
```

Falhas inesperadas retornam `500` com uma mensagem genérica. O `traceId` pode
ser usado para localizar o erro nos logs do servidor; detalhes internos, SQL,
stack trace, senhas e tokens nunca são enviados ao cliente.

Senhas nunca sao retornadas pela API. O banco armazena apenas o hash gerado com BCrypt.

## Fase 4 - Contas financeiras

A fase 4 implementa o CRUD protegido de contas financeiras. Cada conta pertence a um
usuario, e o usuario autenticado e obtido exclusivamente do JWT. O cliente nao envia
`userId` no corpo ou na URL para definir o proprietario da conta.

### Modelo da conta

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | UUID | Identificador gerado pela API. |
| `name` | String | Obrigatorio, entre 2 e 100 caracteres. Espacos externos sao removidos. |
| `type` | Enum | `CASH`, `CHECKING` ou `SAVINGS`. |
| `initialBalance` | BigDecimal | Obrigatorio, no maximo 17 digitos inteiros e 2 casas decimais; pode ser negativo. |
| `createdAt` | OffsetDateTime | Preenchido pela API. |
| `updatedAt` | OffsetDateTime | Atualizado pela API. |

O saldo atual nao e armazenado nesta tabela. Ele sera calculado futuramente a partir do
saldo inicial e das transacoes vinculadas a conta.

### Endpoints

Todos os endpoints abaixo exigem:

```http
Authorization: Bearer seu_access_token
```

| Metodo | Endpoint | Status de sucesso | Finalidade |
| --- | --- | --- | --- |
| `POST` | `/api/v1/financial-accounts` | `201 Created` | Cria uma conta para o usuario autenticado. |
| `GET` | `/api/v1/financial-accounts` | `200 OK` | Lista as contas do usuario autenticado, por data de criacao crescente. |
| `GET` | `/api/v1/financial-accounts/{accountId}` | `200 OK` | Consulta uma conta propria. |
| `PUT` | `/api/v1/financial-accounts/{accountId}` | `200 OK` | Atualiza nome, tipo e saldo inicial quando permitido. |
| `DELETE` | `/api/v1/financial-accounts/{accountId}` | `204 No Content` | Exclui uma conta propria sem transacoes. |

### Criar uma conta

```http
POST /api/v1/financial-accounts
Content-Type: application/json
Authorization: Bearer seu_access_token
```

```json
{
  "name": "Conta corrente",
  "type": "CHECKING",
  "initialBalance": 1500.00
}
```

### Atualizar uma conta

O payload do `PUT` possui os mesmos campos do cadastro:

```json
{
  "name": "Conta corrente principal",
  "type": "CHECKING",
  "initialBalance": 1700.00
}
```

O saldo inicial pode ser alterado enquanto a conta nao possui transacoes. Depois do
primeiro lancamento, essa alteracao e recusada para preservar o historico financeiro.

### Regras de propriedade e historico

- Consultas, atualizacoes e exclusoes usam `accountId` junto do id extraido do JWT.
- Uma conta de outro usuario responde `404 Not Found`, sem revelar se o recurso existe.
- Uma conta com transacoes nao pode ser excluida.
- Uma conta com transacoes nao pode ter o `initialBalance` alterado.
- O tipo da conta deve ser `CASH`, `CHECKING` ou `SAVINGS`.
- Nao existe exclusao em cascata de transacoes.

### Respostas de erro

| Situacao | Status | Codigo |
| --- | --- | --- |
| Corpo com nome, tipo ou saldo invalido | `400` | `VALIDATION_FAILED` |
| Token ausente, invalido ou expirado | `401` | `AUTHENTICATION_REQUIRED` |
| Conta inexistente ou pertencente a outro usuario | `404` | `FINANCIAL_ACCOUNT_NOT_FOUND` |
| Conta possui transacoes e a operacao e proibida | `409` | `FINANCIAL_ACCOUNT_HAS_TRANSACTIONS` |

### Testes da fase

Os testes unitarios do service cobrem criacao, listagem, propriedade, atualizacao,
alteracao de saldo inicial com historico e exclusao. Os testes de integracao cobrem o
CRUD protegido, validacao de payload e tentativa de acesso por outro usuario usando
tokens JWT reais.

### Categorias

Todos os endpoints de categoria exigem autenticação:

```http
Authorization: Bearer seu_access_token
```

| Metodo | Endpoint | Status de sucesso | Finalidade |
| --- | --- | --- | --- |
| `POST` | `/api/v1/categories` | `201 Created` | Cria uma categoria de receita ou despesa. |
| `GET` | `/api/v1/categories` | `200 OK` | Lista as categorias do usuário autenticado. |
| `GET` | `/api/v1/categories/{categoryId}` | `200 OK` | Consulta uma categoria própria. |
| `PUT` | `/api/v1/categories/{categoryId}` | `200 OK` | Atualiza nome e tipo da categoria. |
| `DELETE` | `/api/v1/categories/{categoryId}` | `204 No Content` | Exclui uma categoria sem transações. |

#### Criar uma categoria

```http
POST /api/v1/categories
Content-Type: application/json
Authorization: Bearer seu_access_token
```

```json
{
  "name": "Alimentação",
  "transactionType": "EXPENSE"
}
```

Os únicos valores aceitos para `transactionType` são `INCOME` e `EXPENSE`.

#### Atualizar uma categoria

```http
PUT /api/v1/categories/{categoryId}
Content-Type: application/json
Authorization: Bearer seu_access_token
```

```json
{
  "name": "Alimentação atualizada",
  "transactionType": "EXPENSE"
}
```

O nome deve possuir entre 2 e 80 caracteres. Espaços externos são removidos antes
da persistência. O nome é único por usuário e tipo, portanto `Salário` pode existir
como `INCOME` e `EXPENSE`, mas não duas vezes no mesmo tipo.

#### Regras de propriedade e histórico

- Categorias são pessoais e só podem ser acessadas pelo proprietário extraído do JWT.
- Uma categoria de outro usuário responde `404 Not Found`, sem revelar sua existência.
- Uma categoria vinculada a transações não pode ser excluída.
- O tipo de uma categoria vinculada a transações não pode ser alterado.
- O nome pode ser alterado, desde que não gere duplicidade.

#### Respostas de erro

| Situação | Status | Código |
| --- | --- | --- |
| Nome ou tipo inválido | `400` | `VALIDATION_FAILED` |
| Token ausente, inválido ou expirado | `401` | `AUTHENTICATION_REQUIRED` |
| Categoria inexistente ou pertencente a outro usuário | `404` | `CATEGORY_NOT_FOUND` |
| Nome duplicado no mesmo tipo | `409` | `CATEGORY_ALREADY_EXISTS` |
| Categoria possui transações e a operação é proibida | `409` | `CATEGORY_HAS_TRANSACTIONS` |

## Transações

As transações representam receitas e despesas já realizadas. Todos os endpoints
exigem autenticação e usam exclusivamente o usuário extraído do JWT. O cliente
não envia `userId`.

### Modelo da transação

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | UUID | Identificador gerado pela API. |
| `accountId` | UUID | Conta pertencente ao usuário autenticado. |
| `categoryId` | UUID | Categoria pertencente ao usuário autenticado. |
| `type` | Enum | `INCOME` ou `EXPENSE`; deve coincidir com o tipo da categoria. |
| `amount` | BigDecimal | Obrigatório, maior que zero, com no máximo 17 dígitos inteiros e 2 casas decimais. |
| `occurredOn` | LocalDate | Obrigatório e não pode ser uma data futura. |
| `description` | String | Opcional, com no máximo 255 caracteres; espaços externos são removidos. |
| `createdAt` | OffsetDateTime | Preenchido pela API. |
| `updatedAt` | OffsetDateTime | Atualizado pela API. |

Os valores enviados em `amount` são sempre positivos. O campo `type` define se o
lançamento é uma receita ou uma despesa. A criação ou alteração de uma transação
não modifica `initialBalance`; saldos são derivados das transações em consultas.

### Endpoints

| Método | Endpoint | Status de sucesso | Finalidade |
| --- | --- | --- | --- |
| `POST` | `/api/v1/transactions` | `201 Created` | Cria uma receita ou despesa. |
| `GET` | `/api/v1/transactions` | `200 OK` | Lista transações com paginação e filtros. |
| `GET` | `/api/v1/transactions/{transactionId}` | `200 OK` | Consulta uma transação própria. |
| `PUT` | `/api/v1/transactions/{transactionId}` | `200 OK` | Atualiza integralmente uma transação própria. |
| `DELETE` | `/api/v1/transactions/{transactionId}` | `204 No Content` | Exclui fisicamente uma transação própria. |

### Criar uma transação

```http
POST /api/v1/transactions
Content-Type: application/json
Authorization: Bearer seu_access_token
```

```json
{
  "accountId": "8eb438af-1c4e-4395-8385-15ed32a80a60",
  "categoryId": "6e95f378-4b1e-4bb4-be15-5cf2bf619812",
  "type": "EXPENSE",
  "amount": 125.50,
  "occurredOn": "2026-08-13",
  "description": "Mercado"
}
```

Conta e categoria devem pertencer ao usuário autenticado. Uma categoria
`EXPENSE` não pode ser usada com uma transação `INCOME`, e vice-versa.

### Listar transações

A listagem é paginada. As páginas são numeradas a partir de zero, o tamanho
padrão é 20 e a ordenação padrão é `occurredOn` decrescente, seguida de
`createdAt` decrescente.

```http
GET /api/v1/transactions?page=0&size=10
Authorization: Bearer seu_access_token
```

Filtros opcionais:

| Parâmetro | Tipo | Regra |
| --- | --- | --- |
| `startDate` | `yyyy-MM-dd` | Data inicial inclusiva. |
| `endDate` | `yyyy-MM-dd` | Data final inclusiva. |
| `type` | Enum | `INCOME` ou `EXPENSE`. |
| `accountId` | UUID | Conta própria usada no lançamento. |
| `categoryId` | UUID | Categoria própria usada no lançamento. |
| `page` | Inteiro | Página zero-based. |
| `size` | Inteiro | Quantidade de itens por página. |
| `sort` | Texto | Ordenação do Spring Data, quando necessário. |

Exemplo combinando filtros:

```http
GET /api/v1/transactions?startDate=2026-08-01&endDate=2026-08-31&type=EXPENSE&accountId=8eb438af-1c4e-4395-8385-15ed32a80a60&page=0&size=10
```

O intervalo de datas é inclusivo. A data inicial deve ser menor ou igual à data
final. O retorno possui os dados em `content` e os metadados de paginação, como
`totalElements`, `totalPages`, `number` e `size`.

### Consultar, atualizar e excluir

Consulta por ID:

```http
GET /api/v1/transactions/d511327d-4e93-460d-a3b0-1be08037fc3d
Authorization: Bearer seu_access_token
```

O `PUT` recebe o mesmo formato do cadastro e revalida todos os campos:

```http
PUT /api/v1/transactions/d511327d-4e93-460d-a3b0-1be08037fc3d
Content-Type: application/json
Authorization: Bearer seu_access_token
```

```json
{
  "accountId": "8eb438af-1c4e-4395-8385-15ed32a80a60",
  "categoryId": "6e95f378-4b1e-4bb4-be15-5cf2bf619812",
  "type": "EXPENSE",
  "amount": 150.75,
  "occurredOn": "2026-08-13",
  "description": "Mercado atualizado"
}
```

Para excluir:

```http
DELETE /api/v1/transactions/d511327d-4e93-460d-a3b0-1be08037fc3d
Authorization: Bearer seu_access_token
```

A exclusão é física. Depois da exclusão, uma nova consulta ou tentativa de
exclusão do mesmo ID responde `404 Not Found`.

### Regras de propriedade e respostas de erro

- Uma transação de outro usuário responde `404 Not Found`, sem revelar sua existência.
- Conta ou categoria de outro usuário não pode ser usada em uma transação.
- O tipo da transação deve coincidir com o tipo da categoria.
- O valor deve ser positivo e ter no máximo duas casas decimais.
- A data de ocorrência não pode ser futura.
- O intervalo informado deve possuir data inicial menor ou igual à data final.

| Situação | Status | Código |
| --- | --- | --- |
| Corpo ou parâmetro inválido | `400` | `VALIDATION_FAILED` |
| Intervalo de datas inválido | `400` | `INVALID_TRANSACTION_DATE_RANGE` |
| Token ausente, inválido ou expirado | `401` | `AUTHENTICATION_REQUIRED` |
| Transação inexistente ou de outro usuário | `404` | `TRANSACTION_NOT_FOUND` |
| Conta inexistente, de outro usuário ou filtro não autorizado | `404` | `FINANCIAL_ACCOUNT_NOT_FOUND` |
| Categoria inexistente, de outro usuário ou filtro não autorizado | `404` | `CATEGORY_NOT_FOUND` |
| Tipo da transação diferente do tipo da categoria | `409` | `TRANSACTION_TYPE_MISMATCH` |

### Testes da fase

Os testes unitários cobrem as regras do `TransactionService`, incluindo
propriedade, valor, tipo, datas, filtros, atualização e exclusão. Os testes de
integração cobrem o CRUD protegido, paginação, filtros, validações e isolamento
entre dois usuários usando tokens JWT reais.

## Dashboard e relatórios

Os endpoints de relatório exigem autenticação JWT:

```http
Authorization: Bearer seu_access_token
```

| Método | Endpoint | Status de sucesso | Finalidade |
| --- | --- | --- | --- |
| `GET` | `/api/v1/reports/summary` | `200 OK` | Retorna receitas, despesas e saldo líquido de um período. |
| `GET` | `/api/v1/reports/balances` | `200 OK` | Retorna o saldo atual de cada conta e o total consolidado. |

### Resumo financeiro por período

As datas inicial e final são obrigatórias, devem usar o formato `yyyy-MM-dd` e o
intervalo é inclusivo:

```http
GET /api/v1/reports/summary?startDate=2026-08-01&endDate=2026-08-31
Authorization: Bearer seu_access_token
```

Exemplo de resposta:

```json
{
  "startDate": "2026-08-01",
  "endDate": "2026-08-31",
  "totalIncome": 1600.00,
  "totalExpense": 100.00,
  "netBalance": 1500.00
}
```

O `netBalance` representa somente a movimentação líquida do período:

```text
totalIncome - totalExpense
```

O saldo inicial das contas não participa desse cálculo. Se `startDate` for posterior
a `endDate`, ou se alguma data estiver ausente ou inválida, a API responde `400 Bad
Request`.

### Saldo atual das contas

O saldo atual não recebe intervalo de datas. Ele considera todas as movimentações
acumuladas e o saldo inicial de cada conta:

```text
saldo da conta = initialBalance + receitas - despesas
```

Exemplo de requisição:

```http
GET /api/v1/reports/balances
Authorization: Bearer seu_access_token
```

Exemplo de resposta:

```json
{
  "totalBalance": 2777.77,
  "accounts": [
    {
      "accountId": "8eb438af-1c4e-4395-8385-15ed32a80a60",
      "accountName": "Bradesco",
      "balance": 2000.00
    },
    {
      "accountId": "53e8cae9-ddf6-4e6a-a328-8a1fedcbfb5c",
      "accountName": "Nubank",
      "balance": 777.77
    }
  ]
}
```

Contas sem transações também são retornadas, considerando somente o saldo inicial.
Os relatórios sempre filtram pelo usuário autenticado e nunca incluem dados de
outros usuários.

## Testes

Os testes unitários e de integração são executados com:

```powershell
mvn verify
```

Os testes de integração usam PostgreSQL real em Testcontainers, aplicam as migrations Flyway desde o zero e limpam os dados entre os métodos. Portanto, o Docker precisa estar disponível durante a execução.

O relatório de cobertura é gerado em `target/site/jacoco/index.html`. O build falha quando a cobertura de linhas da aplicação fica abaixo de 70%.

Os testes cobrem cadastro, login, hash de senha, duplicidade de e-mail, emissão/validação de JWT, token malformado, token assinado com outra chave, token expirado, acesso a rota protegida, isolamento entre usuários, regras de histórico, filtros e paginação de transações, relatórios, migrations PostgreSQL e respostas de erro padronizadas.

## Migrations Flyway

O schema do banco e criado exclusivamente por migrations em `src/main/resources/db/migration`.
As migrations ja aplicadas nao devem ser editadas; qualquer ajuste deve entrar em uma nova versao.

| Versao | Arquivo | Proposito |
| --- | --- | --- |
| `V1` | `V1__create_users.sql` | Habilita UUID com `pgcrypto`, cria `users`, checks obrigatorios e indice unico case-insensitive de e-mail. |
| `V2` | `V2__create_financial_accounts.sql` | Cria `financial_accounts`, vinculo com usuario, tipos permitidos, constraint composta para propriedade e indice por usuario. |
| `V3` | `V3__create_categories.sql` | Cria `categories`, tipos de transacao permitidos, unicidade por usuario/tipo/nome normalizado e indice por usuario. |
| `V4` | `V4__create_transactions.sql` | Cria `transactions`, FKs simples e compostas de isolamento por usuario, checks de tipo/valor/descricao e indices de filtros. |

## Parar o ambiente

Para parar somente a API e continuar usando o PostgreSQL pelo IntelliJ:

```powershell
docker compose stop api
```

Para parar os contêineres sem remover o volume de dados:

```powershell
docker compose down
```

Para remover também o volume nomeado do PostgreSQL e apagar os dados locais:

```powershell
docker compose down -v
```

Use `docker compose down -v` somente quando quiser recriar o banco do zero.
