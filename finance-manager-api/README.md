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
GET  /actuator/health
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

## Testes

Execute a suite automatizada com:

```powershell
mvn test
```

Os testes cobrem cadastro, login, hash de senha, duplicidade de e-mail, emissao/validacao de JWT, token malformado, token assinado com outra chave, token expirado e acesso a rota protegida.

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
