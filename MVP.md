# Finance Manager — MVP 1.0

## 1. Propósito

Construir uma API REST de gerenciamento financeiro pessoal, segura e publicável, para demonstrar competências de Backend Java: Spring Boot, Spring Security, JWT, PostgreSQL, Flyway, Docker, Swagger/OpenAPI, tratamento de exceções, testes automatizados e deploy.

O frontend não integra o escopo da versão 1.0. A API será suficiente para ser consumida por Swagger, Postman/Insomnia ou por um frontend futuro.

## 2. Escopo da versão 1.0

### Autenticação

- Criar conta de usuário com nome, e-mail e senha.
- Realizar login com e-mail e senha.
- Emitir JWT de acesso para endpoints protegidos.
- Proteger rotas financeiras com Spring Security.
- Garantir que cada usuário acesse exclusivamente os próprios dados.

### Contas financeiras

- Cadastrar conta financeira.
- Listar as contas do usuário autenticado.
- Atualizar nome, tipo e saldo inicial da própria conta.
- Excluir uma conta sem transações vinculadas; caso possua histórico, retornar erro de negócio explicativo.

Tipos inicialmente suportados: `CASH`, `CHECKING` e `SAVINGS`.

### Categorias

- Cadastrar categoria de receita ou despesa.
- Listar, atualizar e excluir as próprias categorias.
- Validar que uma categoria utilizada em transação tenha tipo compatível com a movimentação.

### Transações

- Registrar receita ou despesa vinculada a uma conta e categoria.
- Listar transações do usuário com paginação.
- Filtrar por período, tipo, conta e categoria.
- Atualizar e excluir a própria transação.
- Consultar resumo por período: total de receitas, total de despesas e saldo líquido.

## 3. Fora do escopo

Os itens abaixo ficam explicitamente fora da versão 1.0 para manter uma entrega enxuta e bem acabada:

- Frontend Angular.
- Refresh token, recuperação de senha, confirmação de e-mail e MFA.
- Papéis administrativos e gestão de permissões além do usuário autenticado.
- Transações recorrentes, transferências entre contas, orçamentos, metas e notificações.
- Anexos, tags, exportação de relatórios e integrações bancárias.
- Multi-moeda, compartilhamento familiar, previsão financeira e conciliação de extratos.
- Observabilidade avançada, auditoria detalhada, rate limiting e mensageria.

## 4. Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF-01 | O sistema deve permitir cadastro com e-mail único e senha protegida por hash. |
| RF-02 | O sistema deve autenticar o usuário e emitir um JWT válido. |
| RF-03 | O sistema deve exigir JWT em todas as operações financeiras. |
| RF-04 | O usuário deve administrar somente as próprias contas e categorias. |
| RF-05 | O usuário deve registrar, consultar, editar e excluir apenas as próprias transações. |
| RF-06 | Toda transação deve ter tipo, valor positivo, data, conta e categoria compatível. |
| RF-07 | A listagem de transações deve aceitar paginação e filtros definidos no escopo. |
| RF-08 | O sistema deve calcular o resumo financeiro com base nas transações do período solicitado. |
| RF-09 | A API deve publicar documentação dos endpoints e modelos por OpenAPI. |

## 5. Requisitos técnicos e decisões

| Item | Decisão para o MVP | Justificativa |
| --- | --- | --- |
| Framework | Java LTS + Spring Boot | Ecossistema maduro, produtivo e relevante para Backend Java. |
| API | REST JSON, versionada em `/api/v1` | Contrato simples, previsível e preparado para evolução. |
| Persistência | PostgreSQL + Spring Data JPA/Hibernate | Combina banco relacional robusto e produtividade com Java. |
| Banco | Flyway com migrações SQL imutáveis | Garante banco reproduzível, auditável e versionado junto ao código. |
| Segurança | Spring Security + JWT Bearer | Demonstra autenticação stateless adequada a uma API consumida separadamente. |
| Senhas | BCrypt ou Argon2id | Nunca armazenar senhas em texto puro. |
| Valores | `BigDecimal` com precisão monetária definida | Evita imprecisões de ponto flutuante. |
| Documentação | Springdoc OpenAPI / Swagger UI | Facilita avaliação e consumo sem frontend. |
| Erros | Handler global e `ProblemDetail` | Mantém respostas consistentes e evita exposição de detalhes internos. |
| Testes | JUnit 5, Mockito  | Valida regras isoladas e integração próxima da produção. |
| Empacotamento | Dockerfile + Docker Compose | Padroniza execução local e prepara a aplicação para deploy. |

## 6. Modelo de dados mínimo

### User

- `id` (UUID)
- `name`
- `email` (único)
- `passwordHash`
- `createdAt`
- `updatedAt`

### FinancialAccount

- `id` (UUID)
- `userId`
- `name`
- `type`
- `initialBalance`
- `createdAt`
- `updatedAt`

### Category

- `id` (UUID)
- `userId`
- `name`
- `transactionType` (`INCOME` ou `EXPENSE`)
- `createdAt`
- `updatedAt`

### Transaction

- `id` (UUID)
- `userId`
- `accountId`
- `categoryId`
- `type` (`INCOME` ou `EXPENSE`)
- `amount`
- `occurredOn`
- `description` (opcional)
- `createdAt`
- `updatedAt`

### Relacionamentos

```text
User 1 ─── N FinancialAccount
User 1 ─── N Category
User 1 ─── N Transaction
FinancialAccount 1 ─── N Transaction
Category 1 ─── N Transaction
```

Todas as consultas de recursos financeiros serão filtradas por `userId`. Além da regra de autorização na aplicação, as chaves estrangeiras e constraints do PostgreSQL preservam a integridade dos vínculos.

## 7. Estrutura recomendada

```text
com.portfolio.financemanager
├── config               # OpenAPI e configurações gerais
├── security             # JWT, filtros e configuração do Spring Security
├── exception            # exceções de negócio e handler global
├── user
│   ├── api              # controllers e DTOs
│   ├── application      # cadastro e autenticação
│   ├── domain
│   └── infrastructure   # JPA e repositórios
└── finance
    ├── api              # contas, categorias, transações e resumo
    ├── application      # casos de uso
    ├── domain
    └── infrastructure   # JPA e repositórios
```

Organizar por módulo de negócio mantém o código coeso. Controllers trabalham com DTOs; casos de uso concentram regras e autorização; entidades não devem ser retornadas diretamente pela API.

## 8. Endpoints previstos

| Recurso | Operações |
| --- | --- |
| Autenticação | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| Contas | CRUD em `/api/v1/accounts` |
| Categorias | CRUD em `/api/v1/categories` |
| Transações | CRUD em `/api/v1/transactions` e filtros na listagem |
| Resumo | `GET /api/v1/summary?startDate=&endDate=` |
| Saúde | `GET /actuator/health` |

Os nomes e detalhes finais do contrato serão definidos na documentação OpenAPI antes da implementação dos controllers.

## 9. Banco de dados e Flyway

- Usar PostgreSQL em todos os ambientes, inclusive nos testes de integração.
- Criar migrações sequenciais e imutáveis em `db/migration`.
- Criar inicialmente tabelas de usuários, contas, categorias e transações, incluindo chaves estrangeiras, unicidade de e-mail, constraints de valor e índices dos filtros frequentes.
- Não alterar uma migração já aplicada: toda correção cria uma nova versão.
- Executar validação e aplicação de migrações ao iniciar os ambientes controlados e no pipeline de entrega.

## 10. Segurança, validação e erros

### Segurança

- Endpoints de cadastro e login são públicos; os demais exigem Bearer JWT.
- A identidade extraída do token é a fonte de `userId` nas operações; esse campo nunca é confiado ao corpo da requisição.
- Token possui expiração curta, segredo em variável de ambiente e assinatura validada.
- CORS será configurado somente para as origens necessárias no ambiente em questão.

### Validação

- Bean Validation nos DTOs para campos obrigatórios, e-mail, tamanho, enum, data e valor positivo.
- Regras de negócio no caso de uso: propriedade da conta/categoria, compatibilidade do tipo e impedimento de exclusão de conta com histórico.
- Constraints no banco como camada final de proteção.

### Tratamento de exceções

Retornar erros uniformes no formato `ProblemDetail`, com identificador de rastreio quando disponível.

| Cenário | Status |
| --- | --- |
| Dados inválidos | 400 |
| Credenciais ou token inválidos | 401 |
| Recurso de outro usuário | 403 ou 404, conforme política adotada |
| Recurso não encontrado | 404 |
| E-mail duplicado ou exclusão inválida | 409 |
| Regra de negócio violada | 422 |
| Erro inesperado | 500 sem detalhes internos |

## 11. Testes mínimos obrigatórios

### Unitários

- Autenticação: senha válida/inválida e geração de token.
- Regras de transação: valor positivo, tipo compatível e propriedade dos recursos relacionados.
- Resumo financeiro: receitas, despesas, período vazio e saldo líquido.
- Regras de exclusão de conta e categoria.

### Integração

- Cadastro e login retornam os status e contratos esperados.
- Endpoint protegido rejeita ausência ou invalidez de JWT.
- Usuário A não consegue consultar nem alterar dados do usuário B.
- Fluxo completo de criação e consulta de transação funciona com filtros e paginação.
- Resumo retorna totais corretos para período informado.

O foco é cobrir comportamentos críticos e não atingir uma porcentagem de cobertura artificial.

## 12. Docker e deploy

### Docker

- `Dockerfile` multi-stage para gerar imagem enxuta da API.
- `docker-compose.yml` com API e PostgreSQL.
- Variáveis de ambiente para conexão do banco, perfil Spring e segredo JWT.
- Volume nomeado para persistência local do PostgreSQL.
- Health check da aplicação e do banco.

### Deploy

- Publicar imagem Docker em registry e executar em uma plataforma de contêiner gerenciada.
- Usar PostgreSQL gerenciado no ambiente publicado.
- Configurar segredos exclusivamente na plataforma de deploy.
- Executar build, testes e migrações no pipeline antes da publicação.
- Expor URL da Swagger UI, endpoint de saúde e instruções de teste no README.

## 13. Ordem de implementação

1. **Fundação:** projeto Spring Boot, Docker Compose, PostgreSQL, Flyway inicial, OpenAPI, convenções de erro e pipeline básico.
2. **Identidade:** `User`, cadastro, login, Spring Security, JWT e testes de segurança.
3. **Cadastro financeiro:** contas e categorias, com validação e isolamento por usuário.
4. **Transações e resumo:** CRUD, filtros, paginação, agregações e testes de integração.
5. **Entrega:** revisão de documentação, Dockerfile, variáveis de ambiente, deploy e smoke test público.

## 14. Critério de pronto da versão 1.0

- [ ] API inicia por Docker com PostgreSQL sem configuração manual de banco.
- [ ] Todas as estruturas do banco são criadas exclusivamente por Flyway.
- [ ] Cadastro, login e proteção JWT funcionam e são testados.
- [ ] Contas, categorias e transações têm CRUD completo e isolamento entre usuários.
- [ ] Filtros, paginação e resumo financeiro funcionam corretamente.
- [ ] Validações e respostas de erro são consistentes e documentadas.
- [ ] Swagger/OpenAPI permite explorar toda a API.
- [ ] Testes unitários e integração cobrem fluxos e regras essenciais.
- [ ] Aplicação está publicada, com variáveis secretas configuradas fora do código.
- [ ] README descreve arquitetura, execução local, endpoints e URL pública.

---

O MVP estará concluído quando esses critérios forem atendidos. Funcionalidades adicionais somente entram após a versão 1.0 estar estável, testada e publicada.
