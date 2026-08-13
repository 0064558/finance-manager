# Finance Manager API

API para gerenciamento financeiro pessoal.

## Requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose

## Configuracao local

Crie o arquivo `.env` a partir do modelo versionado:

```powershell
Copy-Item .env.example .env
```

O ambiente local usa PostgreSQL sem senha, com autenticacao `trust`, apenas para desenvolvimento:

```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

DB_NAME=finance_manager_dev
DB_PORT=5432
DB_URL=jdbc:postgresql://localhost:5432/finance_manager_dev
DB_USERNAME=postgres
DB_PASSWORD=
POSTGRES_AUTH_METHOD=trust

JWT_ISSUER=finance-manager-api
JWT_EXPIRATION_SECONDS=3600
JWT_SECRET=gere_um_secret_forte_para_o_ambiente_local
```

O arquivo `.env` fica fora do Git.

`JWT_SECRET` deve ser um valor forte e privado. Nao use o valor do exemplo em producao e nao versione segredos reais.

## Subir o banco

Inicie o PostgreSQL em conteiner:

```powershell
docker compose up -d
```

Confira se o conteiner esta rodando:

```powershell
docker compose ps
```

Confirme se a porta local esta disponivel:

```powershell
Test-NetConnection localhost -Port 5432
```

O resultado esperado e:

```text
TcpTestSucceeded : True
```

Tambem e possivel validar pelo PostgreSQL:

```powershell
docker compose exec postgres pg_isready -U postgres -d finance_manager_dev
```

Resultado esperado:

```text
/var/run/postgresql:5432 - accepting connections
```

## Rodar a aplicacao

Com o banco disponivel, inicie a API em perfil `dev`:

```powershell
mvn spring-boot:run
```

Ou informe o perfil explicitamente:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A aplicacao sobe em:

```text
http://localhost:8080
```

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

## Testes

Execute a suite automatizada com:

```powershell
mvn test
```

Os testes cobrem cadastro, login, hash de senha, duplicidade de e-mail, emissão/validação de JWT, token malformado, token assinado com outra chave, token expirado, acesso a rota protegida e os CRUDs de contas financeiras e categorias.

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

Pare os conteineres:

```powershell
docker compose down
```

Para remover tambem o volume nomeado do PostgreSQL:

```powershell
docker compose down -v
```
