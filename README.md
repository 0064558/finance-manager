# Finance Manager

Aplicação web para gerenciamento financeiro pessoal, construída como projeto de
portfólio. O sistema permite que cada usuário organize contas, categorias e
transações e acompanhe seus próprios saldos e resumos financeiros.

[![CI](https://github.com/0064558/finance-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/0064558/finance-manager/actions/workflows/ci.yml)

## Aplicação publicada

| Parte | Endereço |
| --- | --- |
| Frontend Angular | [finance-manager-lime.vercel.app](https://finance-manager-lime.vercel.app) |
| API Spring Boot | [finance-manager-fttd.onrender.com](https://finance-manager-fttd.onrender.com) |
| Health check | [/actuator/health](https://finance-manager-fttd.onrender.com/actuator/health) |
| Swagger UI | [/swagger-ui.html](https://finance-manager-fttd.onrender.com/swagger-ui.html) |
| OpenAPI JSON | [/api-docs/v1](https://finance-manager-fttd.onrender.com/api-docs/v1) |

O serviço gratuito do Render pode suspender a API após um período de inatividade.
O primeiro acesso depois da suspensão pode levar alguns segundos para iniciar a
aplicação.

## Escopo do MVP

- Cadastro, login e autenticação stateless com JWT.
- Contas financeiras com saldo inicial.
- Categorias de receitas e despesas.
- Registro, consulta, atualização e exclusão de transações.
- Paginação e filtros de transações por período, tipo, conta e categoria.
- Resumo financeiro por período e saldos consolidados.
- Isolamento completo dos dados por usuário autenticado.
- Guia inicial pelas principais telas, com conclusão registrada por versão para cada usuário.

O projeto não inclui, neste MVP, funcionalidades como recuperação de senha,
integrações bancárias, recorrência automática ou múltiplas moedas.

## Stack

### Backend

- Java 21 e Spring Boot 4.
- Spring Web MVC, Spring Security e JWT.
- Spring Data JPA/Hibernate.
- PostgreSQL e Flyway.
- Bean Validation e respostas de erro no formato `ProblemDetail`.
- OpenAPI/Swagger.
- JUnit, Testcontainers e JaCoCo.

### Frontend e infraestrutura

- Angular 22, TypeScript e Vitest.
- Docker e Docker Compose para desenvolvimento local.
- GitHub Actions para CI.
- Neon para PostgreSQL gerenciado.
- Render para a API containerizada.
- Vercel para o frontend estático.

## Arquitetura

O backend é um monólito modular. Cada módulo organiza controller, service,
repository, entidades e DTOs de uma capacidade do domínio. A autenticação é
stateless: o usuário é identificado pelo JWT e o cliente nunca informa um
`userId` para escolher o proprietário dos dados.

```text
Angular (Vercel)
        │ HTTPS + Bearer JWT
        ▼
Spring Boot API (Render)
        │ JPA/Hibernate + Flyway
        ▼
PostgreSQL (Neon)
```

As migrations criam o schema do banco. Constraints PostgreSQL, validações da
aplicação e filtros por usuário trabalham em conjunto para preservar integridade
e isolamento dos dados.

O frontend consulta `GET /api/v1/auth/me` para obter o nome e a versão de
onboarding do usuário. O guia é exibido quando essa versão está abaixo da
versão suportada pelo frontend; ao pular ou concluir, o progresso é salvo pela
API. Durante carregamentos, a interface mostra estados visuais e, se uma
requisição demorar mais de cinco segundos, informa que o servidor pode estar
iniciando após um período de inatividade.

## Estrutura do repositório

```text
finance-manager/
├── finance-manager-api/       # API Spring Boot, migrations e testes
├── finance-manager-web/       # Aplicação Angular
├── .github/workflows/         # Pipeline de backend e frontend
├── LICENSE
└── README.md
```

Documentação específica:

- [README da API](finance-manager-api/README.md)
- [README do frontend](finance-manager-web/README.md)
- [Modelo de variáveis da API](finance-manager-api/.env.example)
- [Licença MIT](LICENSE)

## Pré-requisitos

- Git.
- Java 21 ou superior.
- Docker Desktop com Docker Compose.
- Node.js compatível com o projeto e npm.

## Executar localmente

### 1. Clonar o projeto

```powershell
git clone https://github.com/0064558/finance-manager.git
Set-Location finance-manager
```

### 2. Configurar a API

Copie o modelo de ambiente e substitua os valores locais:

```powershell
Copy-Item finance-manager-api/.env.example finance-manager-api/.env
```

No arquivo `finance-manager-api/.env`, use pelo menos:

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
CORS_ALLOWED_ORIGINS=http://localhost:4200
```

O `.env` não deve ser versionado. Nunca reutilize segredos locais em produção.

### 3. Iniciar PostgreSQL e API

```powershell
Set-Location finance-manager-api
docker compose config
docker compose up -d --build
docker compose ps
```

A API estará disponível em `http://localhost:8080`. O PostgreSQL estará
disponível para ferramentas locais em `localhost:5433`.

Valide a aplicação:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado:

```json
{
  "status": "UP"
}
```

### 4. Iniciar o frontend

Em outro terminal:

```powershell
Set-Location finance-manager-web
npm ci
npm start
```

Abra [http://localhost:4200](http://localhost:4200). No desenvolvimento, o
proxy do Angular encaminha `/api` para a API local em `localhost:8080`.

## Testes e build

Backend, a partir de `finance-manager-api`:

```powershell
.\mvnw.cmd verify
```

Os testes de integração usam PostgreSQL real em Testcontainers e aplicam as
migrations Flyway desde um banco vazio. O Docker precisa estar disponível.

Frontend, a partir de `finance-manager-web`:

```powershell
npm ci
npm test -- --watch=false
npm run build
```

O pipeline do GitHub Actions executa backend e frontend em jobs independentes
para cada push ou pull request direcionado à `main`. O build e os testes devem
estar verdes antes de publicar uma alteração.

## Explorar a API

Localmente:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs/v1`

Em produção:

- Swagger UI: [finance-manager-fttd.onrender.com/swagger-ui.html](https://finance-manager-fttd.onrender.com/swagger-ui.html)
- OpenAPI JSON: [finance-manager-fttd.onrender.com/api-docs/v1](https://finance-manager-fttd.onrender.com/api-docs/v1)

Fluxo básico:

1. `POST /api/v1/auth/register` cadastra o usuário.
2. `POST /api/v1/auth/login` retorna um `accessToken`.
3. Envie `Authorization: Bearer <accessToken>` nas rotas protegidas.
4. Use `GET /api/v1/auth/me` para confirmar a identidade autenticada.
5. Para registrar o fim do guia, envie `PATCH /api/v1/auth/me/onboarding` com
   `{ "onboardingVersion": 1 }` e o mesmo token.

O Swagger possui o botão **Authorize** para testar as rotas protegidas. Informe
somente o token; a interface adiciona o prefixo `Bearer`.

Todos os detalhes de contas, categorias, transações, relatórios, paginação e
respostas de erro estão no [README da API](finance-manager-api/README.md).

## Deploy

O deploy atual é acionado automaticamente por commits na branch `main`:

1. O GitHub Actions executa testes e build do backend e do frontend.
2. O Render constrói a imagem Docker e publica a API.
3. Na inicialização da API, o Flyway aplica no Neon as migrations ainda
   pendentes usando o usuário migrator.
4. A Vercel compila o Angular e publica `dist/finance-manager-web/browser`.
5. O backend permite somente a origem pública configurada em
   `CORS_ALLOWED_ORIGINS`.

Segredos de banco, JWT e conexão nunca devem ser adicionados ao GitHub, ao
frontend ou a connection strings armazenadas no repositório. Use as variáveis
secretas das plataformas de hospedagem.

## Licença

Distribuído sob a [MIT License](LICENSE).
