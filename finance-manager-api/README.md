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
```

O arquivo `.env` fica fora do Git.

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
