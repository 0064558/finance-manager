# Finance Manager — Roadmap de Execução do MVP 1.0

## Como usar este roadmap

Este roteiro transforma o escopo já definido em [MVP.md](MVP.md) e a especificação de dados em [modelagem.md](modelagem.md) em uma sequência de execução. Ele não adiciona funcionalidades nem propõe arquitetura distribuída: o produto permanece uma API REST em monólito modular, com quatro entidades — `User`, `FinancialAccount`, `Category` e `Transaction`.

**Regra de avanço:** só iniciar a fase seguinte quando os critérios de conclusão da fase atual estiverem atendidos. Corrigir lacunas de qualidade antes de acumular novas entregas reduz retrabalho e facilita o diagnóstico de problemas.

---

## Fase 1 — Preparação do ambiente

### Objetivo

Criar uma base de projeto reproduzível, versionada e documentada, capaz de iniciar uma aplicação Spring Boot conectada a um PostgreSQL local em contêiner. Ao fim da fase, existe uma API mínima em execução, com documentação OpenAPI e banco versionado por Flyway, mas ainda sem regras de negócio.

### Conhecimentos necessários

- Fluxo básico de Git: repositório, branch, commits pequenos e `.gitignore`.
- Estrutura de um projeto Maven ou Gradle e ciclo de build Java.
- Conceitos de Spring Boot: inicialização, propriedades e perfis de ambiente.
- Conceitos fundamentais de PostgreSQL: banco, usuário, schema, conexão e porta.
- Docker: imagem, contêiner, volume, rede e Docker Compose.
- Papel de uma ferramenta de migration e noções de OpenAPI/Swagger.

### Tarefas

- [ ] Criar ou organizar o repositório GitHub do projeto.
- [ ] Definir um `.gitignore` adequado para Java, IDE, arquivos de ambiente e artefatos de build.
- [ ] Criar o projeto com Java 21 e Spring Boot.
- [ ] Incluir dependências estritamente necessárias: Web, Validation, Data JPA, Security, PostgreSQL, Flyway, OpenAPI e suporte de testes.
- [ ] Definir identificadores básicos do projeto: grupo, artefato, nome e pacote-raiz consistentes.
- [ ] Criar perfis de configuração para desenvolvimento, teste e produção, sem segredos no repositório.
- [ ] Criar arquivo de exemplo de variáveis de ambiente, sem valores reais.
- [ ] Configurar PostgreSQL como banco da aplicação no perfil de desenvolvimento.
- [ ] Criar `docker-compose.yml` para o PostgreSQL, com volume nomeado e credenciais lidas do ambiente.
- [ ] Subir o banco em contêiner e confirmar disponibilidade da porta de conexão.
- [ ] Configurar Flyway para localizar migrations em `db/migration` e validar o schema na inicialização.
- [ ] Criar a primeira migration técnica apenas para estabelecer o histórico do Flyway, conforme a estratégia definida no projeto.
- [ ] Iniciar a aplicação e confirmar que o Flyway registra a migration no banco.
- [ ] Configurar Swagger UI e documento OpenAPI para a API `/api/v1`.
- [ ] Configurar Spring Boot Actuator e validar o endpoint de saúde sem expor dados sensíveis.
- [ ] Registrar no README como iniciar banco e aplicação localmente.
- [ ] Fazer commits separados para fundação do projeto, ambiente Docker e documentação inicial.

### Critério de conclusão

A fase está concluída quando um novo clone do repositório consegue, seguindo o README, subir o PostgreSQL via Docker, iniciar a API com Java 21, aplicar migrations Flyway automaticamente, acessar o health check e abrir o Swagger UI. Nenhuma senha ou chave está versionada.

### Possíveis dificuldades e pontos de atenção

- **Versões incompatíveis:** confirmar que a versão do Spring Boot suporta Java 21 e que o driver PostgreSQL corresponde à versão usada.
- **Banco indisponível na inicialização:** o contêiner pode estar saudável antes de estar pronto para aceitar conexão; usar health check e conferir logs.
- **Configurações misturadas:** evitar URL ou senha de desenvolvimento fixa em arquivos que também serão usados em produção.
- **Flyway e schema existente:** não criar tabelas manualmente. O banco deve nascer das migrations.
- **Swagger exposto sem controle:** a documentação é útil localmente e para demonstração, mas configuração de produção deve ser deliberada.

---

## Fase 2 — Modelagem e banco de dados

### Objetivo

Materializar no PostgreSQL o modelo de dados definido em `modelagem.md`, com integridade referencial, constraints e índices essenciais. Ao fim da fase, o banco é criado do zero de forma determinística por migrations Flyway e protege os principais invariantes estruturais do domínio.

### Conhecimentos necessários

- Chaves primárias, estrangeiras, constraints `NOT NULL`, `UNIQUE` e `CHECK`.
- Cardinalidades 1:N e comportamento de exclusão referencial.
- Tipos PostgreSQL: `uuid`, `varchar`, `numeric(19,2)`, `date` e `timestamptz`.
- Índices compostos e relação entre índice, filtro e ordenação.
- Convenções e imutabilidade de migrations Flyway.
- Diferença entre regra garantida pelo banco e regra que exige validação na aplicação.

### Tarefas

- [ ] Revisar `modelagem.md` antes de criar qualquer migration; ele é a referência do banco do MVP.
- [ ] Definir uma única estratégia de geração de UUID e aplicá-la de modo consistente.
- [ ] Criar migration para a tabela `users` com campos, tipos, chave primária, timestamps e constraint de e-mail único case-insensitive.
- [ ] Criar migration para `financial_accounts`, incluindo `user_id`, chave estrangeira para `users` e tipos de conta permitidos.
- [ ] Criar migration para `categories`, incluindo vínculo com usuário, tipo de transação permitido e unicidade de nome por usuário e tipo.
- [ ] Criar migration para `transactions`, incluindo vínculos obrigatórios com usuário, conta e categoria.
- [ ] Implementar as chaves estrangeiras compostas que garantem que conta e categoria de uma transação pertençam ao mesmo usuário dela.
- [ ] Criar `CHECK` de valor de transação estritamente positivo.
- [ ] Criar `CHECK` para tipos de conta e de transação permitidos.
- [ ] Criar constraints para impedir textos vazios em campos obrigatórios, além de `NOT NULL`.
- [ ] Definir `ON DELETE RESTRICT` nos vínculos que preservam histórico financeiro.
- [ ] Criar os índices de `user_id` e os índices compostos de filtros/ordenação previstos em `modelagem.md`.
- [ ] Executar todas as migrations em banco vazio.
- [ ] Inspecionar tabelas, tipos, chaves, constraints e índices diretamente no PostgreSQL.
- [ ] Validar cenários negativos diretamente no banco: e-mail duplicado, valor negativo, enum inválido, chave estrangeira inexistente e vínculo entre usuários distintos.
- [ ] Confirmar que uma segunda inicialização não altera nem reaplica migrations já registradas.
- [ ] Registrar na documentação a ordem e o propósito de cada migration.

### Critério de conclusão

A fase está concluída quando um banco vazio é construído somente pelo Flyway e todas as quatro tabelas possuem campos, relações, constraints e índices previstos. Inserções estruturalmente inválidas falham no banco. As migrations aplicadas não são editadas; ajustes são uma nova versão.

### Possíveis dificuldades e pontos de atenção

- **`numeric` e ponto flutuante:** não usar `float` ou `double` para dinheiro.
- **E-mail com maiúsculas:** `UNIQUE(email)` puro pode permitir variações de caixa; garantir unicidade normalizada.
- **Foreign key simples insuficiente:** ela não garante, sozinha, que conta/categoria pertencem ao mesmo usuário da transação. Manter as chaves compostas definidas na modelagem.
- **Constraint entre tabelas:** a compatibilidade entre `transaction.type` e `category.transaction_type` deve ser validada no caso de uso; uma `CHECK` comum não consulta outra tabela.
- **Editar migration aplicada:** nunca alterar uma versão já executada em ambiente compartilhado.
- **Índices excessivos:** criar apenas os que atendem listagem e filtros do MVP; cada índice tem custo de escrita.

---

## Fase 3 — Usuários e autenticação

### Objetivo

Implementar identidade, cadastro, login e proteção por JWT. Ao fim da fase, usuários podem criar credenciais, autenticar-se e acessar apenas rotas protegidas com um token válido. Ainda não há recursos financeiros implementados.

### Conhecimentos necessários

- Separação entre DTO de entrada/saída, caso de uso, entidade e repositório.
- Hash de senha com BCrypt ou Argon2id e por que senha não é criptografada reversivelmente.
- Ciclo de autenticação Bearer JWT: emissão, assinatura, expiração, validação e contexto de segurança.
- Conceitos de Spring Security: cadeia de filtros, autenticação, autorização e `SecurityContext`.
- Códigos HTTP para cadastro, login, credenciais inválidas e conflito de e-mail.

### Tarefas

- [ ] Criar o módulo de usuário nas camadas previstas: API, aplicação, domínio e infraestrutura.
- [ ] Mapear a entidade `User` para a tabela já criada; não expor `password_hash` em DTOs de saída.
- [ ] Criar repositório de usuário com busca por e-mail normalizado.
- [ ] Definir DTOs de cadastro e login, incluindo validações de formato de e-mail e política de senha do MVP.
- [ ] Implementar caso de uso de cadastro: normalizar dados, validar duplicidade, gerar hash de senha e persistir usuário.
- [ ] Definir resposta de cadastro sem campos sensíveis.
- [ ] Configurar encoder de senha e testar a comparação de senha válida e inválida.
- [ ] Configurar propriedades JWT: segredo/chave, emissor, tempo de expiração e leitura por variáveis de ambiente.
- [ ] Implementar serviço responsável por emitir e validar JWT.
- [ ] Definir claims mínimos: identificador do usuário e dados estritamente necessários para autenticação.
- [ ] Implementar endpoint de login com resposta contendo access token e dados públicos mínimos do usuário, se previstos no contrato.
- [ ] Configurar Spring Security como stateless.
- [ ] Implementar filtro que extrai, valida o Bearer token e preenche o contexto de segurança.
- [ ] Liberar somente cadastro, login, health check e documentação que o ambiente permitir; exigir autenticação nas demais rotas.
- [ ] Padronizar respostas para login inválido, token ausente e token inválido/expirado.
- [ ] Documentar autenticação Bearer no OpenAPI e validar o fluxo pelo Swagger UI.
- [ ] Criar testes unitários para cadastro, duplicidade, hash, autenticação e token.
- [ ] Criar testes de integração para cadastro, login e acesso a uma rota protegida de teste ou rota já disponível.

### Critério de conclusão

A fase está concluída quando um usuário consegue se cadastrar e autenticar, recebe um JWT válido e este token permite acesso a uma rota protegida. Credenciais erradas, token ausente, token malformado e token expirado são rejeitados com respostas padronizadas. Senha e segredo JWT não aparecem em resposta, documentação de exemplo ou log.

### Possíveis dificuldades e pontos de atenção

- **Guardar senha em texto puro:** o único dado persistido é o hash.
- **Segredo JWT no código:** obter exclusivamente de variável de ambiente ou gerenciador de segredos no deploy.
- **Filtro em ordem errada:** o filtro JWT precisa integrar-se corretamente à cadeia do Spring Security.
- **Token longo demais:** usar expiração curta conforme configurada, pois refresh token está fora do escopo 1.0.
- **Confundir 401 e 403:** 401 trata ausência/invalidez de autenticação; 403 trata identidade válida sem permissão.
- **Confiar no `userId` do cliente:** em fases posteriores, o usuário-alvo sempre vem do contexto JWT.

---

## Fase 4 — Contas financeiras

### Objetivo

Implementar o CRUD protegido de `FinancialAccount`, respeitando propriedade, tipos permitidos e preservação de histórico. Ao fim da fase, um usuário autenticado administra suas próprias contas e não consegue afetar dados de outro usuário.

### Conhecimentos necessários

- Padrão CRUD REST e semântica de `POST`, `GET`, `PUT`/`PATCH` e `DELETE`.
- Mapeamento JPA de UUID, enum textual, `BigDecimal`, timestamps e relacionamento N:1.
- Autorização por proprietário de recurso.
- Paginação não é necessária para contas no MVP; compreender quando uma coleção simples é suficiente.
- Exceções de conflito e regra de negócio.

### Tarefas

- [ ] Criar o módulo de finanças, com separação entre API, aplicação, domínio e infraestrutura.
- [ ] Mapear `FinancialAccount` conforme `modelagem.md`.
- [ ] Representar os três tipos permitidos: `CASH`, `CHECKING` e `SAVINGS`.
- [ ] Criar DTOs de criação e atualização com validação de nome, tipo e precisão do saldo inicial.
- [ ] Implementar criação usando exclusivamente o usuário do contexto de segurança.
- [ ] Implementar consulta de conta por ID condicionada ao proprietário.
- [ ] Implementar listagem de contas do usuário autenticado.
- [ ] Implementar atualização do nome e tipo da conta do proprietário.
- [ ] Implementar regra que impede alteração de saldo inicial depois que existe transação vinculada.
- [ ] Implementar exclusão somente quando a conta não possui transações.
- [ ] Escolher política consistente para recurso de outro usuário: responder 404 para não revelar existência ou 403; aplicar a mesma política em todos os módulos.
- [ ] Documentar endpoints, payloads, respostas e erros no OpenAPI.
- [ ] Criar testes unitários para propriedade, alteração de saldo inicial e exclusão com histórico.
- [ ] Criar testes de integração para CRUD e tentativa de acesso por usuário diferente.

### Critério de conclusão

A fase está concluída quando um usuário autenticado consegue criar, listar, consultar, atualizar e excluir suas contas sem acessar contas de terceiros. Tipos inválidos são rejeitados; alterações de saldo inicial e exclusões que violam histórico são bloqueadas por erro de negócio; operações válidas e inválidas estão documentadas e testadas.

### Possíveis dificuldades e pontos de atenção

- **Id do proprietário no request:** não aceitar nem usar `userId` enviado pelo cliente.
- **Saldo atual persistido:** não criar coluna de saldo atual; ele é derivado de saldo inicial e transações.
- **`BigDecimal` sem escala:** preservar a precisão acordada e testar valores de borda.
- **Exclusão em cascata:** nunca configurar remoção automática de transações ao excluir conta.
- **Verificação somente no controller:** a autorização deve ser aplicada no caso de uso/consulta autorizada, não depender apenas da rota.

---

## Fase 5 — Categorias

### Objetivo

Implementar o CRUD protegido de `Category`, assegurando classificação por receita ou despesa, unicidade por usuário/tipo e preservação das categorias usadas em histórico. Ao fim da fase, o usuário possui categorias confiáveis para registrar transações.

### Conhecimentos necessários

- Enums de domínio e persistência textual.
- Regras de unicidade compostas e normalização de nomes.
- Diferença entre validação de estrutura, integridade do banco e regra de negócio.
- Uso de consultas de existência para impedir exclusões inválidas.

### Tarefas

- [ ] Mapear `Category` de acordo com a tabela e constraints definidas.
- [ ] Representar `INCOME` e `EXPENSE` como os únicos tipos de categoria.
- [ ] Criar DTOs de criação e atualização, validando nome e tipo.
- [ ] Normalizar o nome antes de verificar duplicidade e persistir.
- [ ] Implementar criação para o usuário autenticado.
- [ ] Implementar listagem das categorias do usuário autenticado, com ordenação determinística por tipo e nome.
- [ ] Implementar consulta e atualização apenas para a categoria do proprietário.
- [ ] Impedir mudança de tipo quando a categoria possuir transações vinculadas.
- [ ] Permitir alteração de nome quando não colidir com outra categoria do mesmo usuário e tipo.
- [ ] Impedir exclusão de categoria com transações vinculadas.
- [ ] Traduzir violação de unicidade em resposta de conflito clara, sem revelar dados de outros usuários.
- [ ] Atualizar a documentação OpenAPI.
- [ ] Criar testes unitários para propriedade, duplicidade, mudança de tipo e exclusão com histórico.
- [ ] Criar testes de integração para CRUD e isolamento entre usuários.

### Critério de conclusão

A fase está concluída quando cada usuário pode administrar suas próprias categorias de receita e despesa, sem duplicar nomes dentro do mesmo tipo. Uma categoria usada em transação não pode ser removida nem ter tipo alterado. Casos de sucesso, conflito e acesso indevido estão testados e documentados.

### Possíveis dificuldades e pontos de atenção

- **Duplicidade por caixa/espaços:** normalizar adequadamente para que `Alimentação` e ` alimentação ` não gerem categorias distintas.
- **Tipo alterado com histórico:** a operação invalida a interpretação de transações antigas; deve ser recusada.
- **Excluir categoria usada:** não apagar nem desvincular transações existentes.
- **Misturar categoria global com pessoal:** no MVP todas as categorias pertencem a um usuário.

---

## Fase 6 — Transações

### Objetivo

Implementar o núcleo financeiro do MVP: criação, consulta, atualização, exclusão, paginação e filtros de `Transaction`. Ao fim da fase, o usuário consegue registrar receitas e despesas coerentes, sempre em conta e categoria próprias.

### Conhecimentos necessários

- `BigDecimal`, precisão decimal e comparação correta de valores monetários.
- Paginação e ordenação com Spring Data.
- Filtros opcionais e composição de consultas sem SQL inseguro.
- Transações de banco e validação de múltiplos recursos dentro do mesmo caso de uso.
- Integridade por chaves estrangeiras compostas e regra de compatibilidade entre tipos.
- Semântica de intervalo de datas inclusivo.

### Tarefas

- [ ] Mapear `Transaction` conforme `modelagem.md`, usando os tipos corretos para UUID, data, valor e timestamps.
- [ ] Definir DTO de criação com `accountId`, `categoryId`, `type`, `amount`, `occurredOn` e descrição opcional.
- [ ] Validar obrigatoriedade, formato UUID, enum, valor positivo, precisão monetária e tamanho da descrição no DTO.
- [ ] Implementar criação dentro de uma transação de banco.
- [ ] Obter conta e categoria por consultas filtradas pelo usuário autenticado.
- [ ] Validar que conta e categoria existem e pertencem ao mesmo usuário da transação.
- [ ] Validar que o tipo da transação coincide com o tipo da categoria.
- [ ] Validar que a data de ocorrência não é futura.
- [ ] Persistir a transação somente após todas as validações de domínio.
- [ ] Implementar consulta por ID limitada ao proprietário.
- [ ] Implementar listagem paginada limitada ao proprietário.
- [ ] Definir ordenação padrão: `occurredOn` decrescente e `createdAt` decrescente.
- [ ] Implementar filtros opcionais por período, tipo, conta e categoria.
- [ ] Validar que data inicial é menor ou igual à data final quando ambas forem informadas.
- [ ] Validar que IDs de conta/categoria informados em filtros pertencem ao usuário autenticado, para manter política de autorização consistente.
- [ ] Implementar atualização, revalidando integralmente conta, categoria, tipo, valor e data.
- [ ] Implementar exclusão física somente da transação do proprietário.
- [ ] Garantir que não existe atualização automática de saldo persistido; saldo e resumo são calculados em consulta.
- [ ] Documentar parâmetros de filtro, formato de paginação, ordenação, exemplos de erros e todos os endpoints no OpenAPI.
- [ ] Criar testes unitários para todos os invariantes de transação.
- [ ] Criar testes de integração de CRUD, paginação, cada filtro e isolamento entre dois usuários.

### Critério de conclusão

A fase está concluída quando um usuário autenticado pode criar, listar, consultar, atualizar e excluir somente suas transações. A API impede valor não positivo, data futura, categoria incompatível, conta/categoria de outro usuário e intervalos de data inválidos. A listagem é paginada, ordenada e filtra corretamente. Esses comportamentos estão cobertos por testes automatizados.

### Possíveis dificuldades e pontos de atenção

- **Confundir sinal e tipo:** valores são sempre positivos; `INCOME`/`EXPENSE` define o efeito no cálculo.
- **Confiar somente na foreign key:** validar a compatibilidade de tipo na aplicação e preservar as constraints compostas de usuário no banco.
- **N+1 em listagem:** planejar busca de conta/categoria conforme o DTO de resposta, evitando uma consulta por item.
- **Intervalo de datas inconsistente:** documentar que as datas são inclusivas e testar limites.
- **Exclusão com requisição idempotente:** após excluir, nova busca deve retornar não encontrado; definir e documentar a resposta da repetição de `DELETE`.
- **Filtros sem ordenação estável:** sem critério de desempate, a paginação pode repetir ou pular itens.

---

## Fase 7 — Dashboard e relatórios

### Objetivo

Disponibilizar o resumo financeiro já previsto no MVP, sem criar uma nova entidade, tabela ou funcionalidade analítica. O endpoint calcula receitas, despesas e saldo líquido para o período solicitado a partir das transações persistidas.

### Conhecimentos necessários

- Funções de agregação SQL/JPA: soma, agrupamento conceitual e tratamento de resultado vazio.
- Diferença entre dado derivado por consulta e dado materializado.
- Períodos inclusivos e validação de parâmetros de data.
- Uso de `BigDecimal` em cálculos de agregação e valores zero.

### Tarefas

- [ ] Definir o contrato do endpoint de resumo com `startDate` e `endDate` obrigatórios.
- [ ] Validar formato das datas e garantir que data inicial seja menor ou igual à final.
- [ ] Criar consulta agregada limitada ao usuário autenticado.
- [ ] Calcular total de receitas a partir de transações `INCOME` no período inclusivo.
- [ ] Calcular total de despesas a partir de transações `EXPENSE` no período inclusivo.
- [ ] Calcular saldo líquido como receitas menos despesas.
- [ ] Definir retorno zero para períodos sem transações, sem retornar `null` para valores monetários.
- [ ] Confirmar que transações de outros usuários nunca entram no resultado.
- [ ] Não criar tabela de dashboard, coluna de total ou cache no MVP.
- [ ] Documentar o endpoint, parâmetros, exemplos de resposta e erros de intervalo no OpenAPI.
- [ ] Criar testes unitários de cálculo para receitas, despesas, período vazio e saldo negativo.
- [ ] Criar testes de integração para intervalo inclusivo, usuário isolado e filtro de período.

### Critério de conclusão

A fase está concluída quando o endpoint retorna receitas, despesas e saldo líquido corretos para qualquer período válido, inclusive quando não há lançamentos. Os valores decorrem exclusivamente das transações do usuário autenticado e não existe armazenamento duplicado de resumo ou saldo.

### Possíveis dificuldades e pontos de atenção

- **Usar saldo da conta no resumo:** o resumo do período usa movimentações no intervalo, não o saldo acumulado da conta.
- **Resultado nulo em agregação:** tratar ausência de registros como zero monetário.
- **Datas exclusivas por engano:** testar transações ocorridas exatamente na data inicial e final.
- **Persistir resultado derivado cedo demais:** para o volume do MVP, consultar é mais simples e confiável.

---

## Fase 8 — Tratamento de erros e validações

### Objetivo

Consolidar uma experiência de API previsível: entradas são validadas antes do caso de uso, regras de domínio retornam erros claros e exceções inesperadas não vazam detalhes internos. A fase reforça verticalmente os módulos anteriores, sem adicionar endpoints ou dados.

### Conhecimentos necessários

- Bean Validation e validação de DTOs.
- `@RestControllerAdvice`, exceções customizadas e `ProblemDetail`.
- Diferenças entre erros de validação, autenticação, autorização, ausência, conflito e regra de negócio.
- Princípios de segurança de mensagens de erro e logs.

### Tarefas

- [ ] Definir o formato padrão de erro baseado em `ProblemDetail`.
- [ ] Incluir nos erros, quando apropriado, status, título, detalhe seguro, código interno, timestamp e identificador de rastreio.
- [ ] Criar tratamento global para falhas de validação de DTO, com lista de campos e mensagens.
- [ ] Criar exceção específica para recurso não encontrado ou acesso ocultado por política de autorização.
- [ ] Criar exceção específica para conflitos: e-mail duplicado, categoria duplicada e exclusões bloqueadas por histórico.
- [ ] Criar exceção específica para regra de negócio: tipo incompatível, data futura e intervalo inválido.
- [ ] Padronizar a tradução de exceções do Spring Security para 401 e 403.
- [ ] Definir regra única para acesso a recurso de outro usuário e aplicá-la em conta, categoria e transação.
- [ ] Garantir que exceções inesperadas retornem 500 genérico e sejam registradas apenas no servidor com contexto suficiente para diagnóstico.
- [ ] Revisar todos os DTOs para obrigatoriedade, limites de tamanho, enums, valores positivos e datas.
- [ ] Garantir que validações de DTO não substituam validações de domínio nem constraints de banco.
- [ ] Adicionar respostas de erro relevantes à documentação OpenAPI.
- [ ] Criar testes para cada família de erro e para a estrutura de resposta padronizada.

### Critério de conclusão

A fase está concluída quando todos os endpoints do MVP respondem erros conhecidos no mesmo formato e com status HTTP coerente. Entradas inválidas informam os campos necessários; falhas internas não expõem stack trace, SQL, senha, token ou detalhes de infraestrutura. Os principais cenários de erro estão cobertos por testes.

### Possíveis dificuldades e pontos de atenção

- **Erros diferentes por controller:** centralizar tratamento, evitando respostas ad hoc.
- **Mensagens técnicas ao cliente:** a mensagem pública deve explicar o problema sem revelar a implementação.
- **Usar 500 para regra de negócio:** conflitos e violações conhecidas devem ter status específico.
- **Duplicar validação sem motivo:** manter a responsabilidade correta: formato no DTO, regra no caso de uso, integridade no banco.
- **Detalhes de autenticação excessivos:** login deve informar credenciais inválidas sem revelar se o e-mail existe.

---

## Fase 9 — Testes automatizados

### Objetivo

Transformar regras e fluxos críticos do MVP em uma rede de segurança automatizada. Os testes demonstram qualidade do backend e permitem refatorar com confiança antes do deploy.

### Conhecimentos necessários

- Pirâmide de testes e diferença entre teste unitário e integração.
- JUnit 5, Mockito e padrões Arrange–Act–Assert.
- Testes de Spring Boot: contexto, camada web e persistência.
- Testcontainers com PostgreSQL e isolamento de dados entre testes.
- Cobertura de código como indicador, não objetivo isolado.

### Tarefas

#### Organização e execução

- [ ] Definir convenção de nomes e diretórios de teste que reflita os módulos de produção.
- [ ] Configurar execução de testes unitários no build padrão.
- [ ] Configurar perfil de teste isolado e PostgreSQL em Testcontainers para integrações.
- [ ] Garantir que Flyway cria o schema do zero no banco de teste.
- [ ] Garantir limpeza/isolamento de dados entre testes e ausência de dependência de ordem.

#### Testes unitários mínimos

- [ ] Cadastro: normalização de e-mail, duplicidade e geração de hash.
- [ ] Login: credenciais corretas e incorretas.
- [ ] JWT: token válido, token expirado e token inválido.
- [ ] Contas: proprietário, tipo permitido, saldo inicial e bloqueio de alteração/exclusão com histórico.
- [ ] Categorias: proprietário, unicidade, bloqueio de mudança de tipo e exclusão com histórico.
- [ ] Transações: valor positivo, data não futura, tipo compatível, propriedade de conta/categoria, atualização e exclusão.
- [ ] Resumo: receitas, despesas, saldo líquido, período vazio e limites do período.
- [ ] Erros: mapeamento de exceções de domínio para status e estrutura esperada.

#### Testes de integração mínimos

- [ ] Aplicar migrations Flyway contra PostgreSQL real em contêiner.
- [ ] Cadastro com sucesso e tentativa de e-mail duplicado.
- [ ] Login com sucesso e tentativa com credencial inválida.
- [ ] Acesso sem token, token inválido e token válido em rota protegida.
- [ ] CRUD de conta com isolamento entre dois usuários.
- [ ] CRUD de categoria com isolamento entre dois usuários.
- [ ] Criação de transação válida e rejeição de categoria incompatível.
- [ ] Rejeição de conta ou categoria pertencente a outro usuário.
- [ ] Listagem paginada, ordenada e filtrada por cada parâmetro suportado.
- [ ] Resumo correto para período, inclusive nas bordas e para usuário sem transações.
- [ ] Padronização de erros de validação, conflito, não encontrado, regra de negócio e autenticação.

#### Cobertura e pipeline

- [ ] Gerar relatório de cobertura no build local e no CI.
- [ ] Adotar meta inicial de **70% de cobertura de linhas no código de aplicação**, sem usar o número para ignorar cenários críticos não cobertos.
- [ ] Buscar cobertura próxima de 100% nas regras de negócio mais sensíveis: isolamento por usuário, valor, compatibilidade de tipo, exclusão com histórico e resumo.
- [ ] Configurar o pipeline para falhar em build ou testes falhos.
- [ ] Revisar testes frágeis, duplicados ou dependentes de detalhes internos antes do deploy.

### Critério de conclusão

A fase está concluída quando os testes unitários e de integração listados executam de forma determinística em máquina limpa e no CI. As migrations são verificadas contra PostgreSQL real. O relatório indica ao menos 70% nas camadas de aplicação e todas as regras financeiras e de isolamento possuem cobertura direta.

### Possíveis dificuldades e pontos de atenção

- **Testar apenas repositórios mockados:** isso não verifica SQL, constraints, migrations ou comportamento do PostgreSQL.
- **Usar H2 como equivalente de PostgreSQL:** diferenças de dialeto e constraints podem ocultar defeitos; preferir Testcontainers.
- **Testes com relógio real:** datas podem tornar cenários instáveis; controlar o relógio onde necessário.
- **Cobertura artificial:** não escrever testes sem asserções relevantes apenas para elevar percentual.
- **Tempo excessivo de suíte:** separar unitários e integração, mas manter ambos obrigatórios no pipeline principal.

---

## Fase 10 — Dockerização

### Objetivo

Empacotar a API e suas dependências de desenvolvimento de maneira reproduzível. Ao fim da fase, todo o MVP funciona localmente por meio de contêineres, sem instalação local de PostgreSQL e sem alteração de código para configurar o ambiente.

### Conhecimentos necessários

- Dockerfile multi-stage, imagem de build e imagem de execução.
- Variáveis de ambiente, `.env` e diferenças entre build-time e runtime.
- Docker Compose, redes internas, volumes e health checks.
- Princípio de imagem mínima e usuário não privilegiado em contêiner.

### Tarefas

- [ ] Criar Dockerfile multi-stage compatível com Java 21.
- [ ] Separar etapa de compilação da imagem final de execução.
- [ ] Garantir que a imagem final contém apenas o necessário para executar o artefato.
- [ ] Executar a aplicação no contêiner com usuário não privilegiado, se compatível com a imagem base escolhida.
- [ ] Criar ou ajustar `docker-compose.yml` para iniciar API e PostgreSQL juntos.
- [ ] Configurar a API para acessar o banco pelo nome do serviço Docker, não por `localhost`.
- [ ] Configurar volume nomeado do PostgreSQL para persistência local.
- [ ] Expor somente as portas necessárias para API e banco no ambiente local.
- [ ] Definir variáveis de ambiente para conexão, perfil Spring e segredo JWT.
- [ ] Manter `.env` real fora do Git e atualizar `.env.example`.
- [ ] Configurar health checks do banco e da API.
- [ ] Garantir que migrations Flyway executam quando a stack é iniciada do zero.
- [ ] Testar cadastro, login, CRUDs, transações, resumo e Swagger com toda a stack em contêiner.
- [ ] Testar reinicialização preservando dados no volume e documentar como reiniciar ambiente limpo de forma segura.
- [ ] Atualizar README com comandos de build, inicialização, parada e diagnóstico básico.

### Critério de conclusão

A fase está concluída quando uma pessoa consegue clonar o repositório, preencher variáveis de exemplo, iniciar uma única stack Docker e usar toda a API do MVP. O banco é persistente entre reinicializações locais, Flyway monta um banco novo corretamente e nenhuma configuração sensível está embutida na imagem.

### Possíveis dificuldades e pontos de atenção

- **`localhost` dentro de contêiner:** ele aponta para o próprio contêiner, não para o banco; usar o nome do serviço.
- **JAR desatualizado na imagem:** garantir que o Dockerfile compila o estado atual do projeto.
- **Segredo JWT padrão publicado:** exigir variável de ambiente e usar valor apenas de desenvolvimento local.
- **Volume com schema antigo:** lembrar que Flyway avança o schema; não tentar corrigir deletando dados sem decisão consciente.
- **Aplicação iniciando antes do banco:** diagnosticar readiness e estratégia de retry sem mascarar erros reais.

---

## Fase 11 — Deploy

### Objetivo

Publicar a API do MVP em uma plataforma de contêiner gerenciada, com PostgreSQL gerenciado, configurações seguras e validação de funcionamento externo. O deploy é parte da demonstração profissional do projeto, não uma mudança de escopo funcional.

### Conhecimentos necessários

- Variáveis de ambiente e gerenciamento de segredos na plataforma.
- Diferenças entre serviços de banco locais e gerenciados.
- Logs de aplicação, health check e diagnóstico de falhas de inicialização.
- Build de imagem, registry e pipeline básico de integração contínua.
- CORS e URLs públicas.

### Tarefas

- [ ] Escolher uma plataforma de contêiner gerenciada compatível com Docker e com plano adequado ao projeto de portfólio.
- [ ] Criar instância PostgreSQL gerenciada na mesma região do backend, quando possível.
- [ ] Criar base de dados, usuário de aplicação e credenciais com privilégio mínimo necessário.
- [ ] Configurar URL de conexão, usuário, senha, perfil de produção, segredo JWT, emissor e tempo de expiração como variáveis secretas.
- [ ] Configurar a aplicação para não expor detalhes de erro, documentação sensível ou dados de diagnóstico em produção além do necessário.
- [ ] Publicar imagem da API ou conectar a plataforma ao repositório para build controlado.
- [ ] Definir comando de inicialização e porta conforme requisito da plataforma.
- [ ] Configurar health check apontando para o endpoint de saúde.
- [ ] Executar migrations Flyway no banco de produção de forma controlada no primeiro deploy.
- [ ] Verificar logs de inicialização, conexão do banco e aplicação das migrations.
- [ ] Realizar smoke test externo: health check, Swagger, cadastro, login, rota protegida, conta, categoria, transação e resumo.
- [ ] Confirmar que dados de dois usuários permanecem isolados no ambiente publicado.
- [ ] Configurar CORS apenas se houver origem consumidora definida; não liberar todas as origens sem necessidade.
- [ ] Configurar pipeline de entrega para executar build e testes antes de publicar alterações na branch principal.
- [ ] Registrar URL pública, plataforma, data do deploy e procedimento de atualização no README.

### Critério de conclusão

A fase está concluída quando a API está acessível por URL pública, conectada a PostgreSQL gerenciado, com migrations aplicadas, JWT configurado por segredo externo e todos os fluxos do MVP validados por smoke test. O pipeline bloqueia publicação quando build ou testes falham.

### Possíveis dificuldades e pontos de atenção

- **URL de banco incorreta:** plataformas podem fornecer formato específico; conferir SSL, porta e parâmetros exigidos.
- **Migração falhando em produção:** revisar em staging/local com banco vazio antes de publicar; nunca alterar migration já aplicada.
- **Segredos em logs:** não imprimir propriedades de configuração nem connection string completa.
- **Porta fixa:** a plataforma pode fornecer a porta por variável; respeitar a configuração do ambiente.
- **Plano gratuito com suspensão:** documentar essa limitação de disponibilidade, caso se aplique, sem comprometer a demonstração.
- **CORS permissivo:** configurar apenas depois de saber qual cliente realmente consome a API.

---

## Fase 12 — Documentação final

### Objetivo

Preparar o projeto para avaliação técnica: uma pessoa externa deve entender o problema, a arquitetura, como executar, como testar e como explorar a API sem depender de explicações adicionais.

### Conhecimentos necessários

- Escrita técnica objetiva em Markdown.
- Leitura de OpenAPI/Swagger e exemplos HTTP.
- Documentação de arquitetura de monólito modular.
- Captura de tela sem exposição de dados, tokens ou segredos.

### Tarefas

#### README

- [ ] Escrever uma visão geral curta do Finance Manager e seu objetivo de portfólio.
- [ ] Listar o escopo exato do MVP: usuários, contas, categorias, transações e resumo.
- [ ] Listar a stack: Java 21, Spring Boot, Security, JWT, PostgreSQL, Flyway, JPA/Hibernate, Docker, OpenAPI e testes.
- [ ] Explicar a arquitetura de monólito modular e a separação de responsabilidades por módulo/camada.
- [ ] Inserir diagrama textual ou visual simples do fluxo cliente → API → PostgreSQL.
- [ ] Referenciar os documentos `MVP.md`, `modelagem.md` e este roadmap.
- [ ] Documentar pré-requisitos, variáveis de ambiente e execução local por Docker.
- [ ] Documentar como rodar build e testes.
- [ ] Informar URL publicada e endereço da Swagger UI quando o deploy estiver ativo.
- [ ] Informar limitações intencionais do MVP, sem listar funcionalidades futuras.

#### Swagger e contrato da API

- [ ] Revisar títulos, descrição, versão e licença/contato, se aplicável.
- [ ] Garantir que endpoints públicos e protegidos estão corretamente identificados.
- [ ] Documentar o esquema Bearer JWT e como autorizá-lo no Swagger UI.
- [ ] Descrever parâmetros de paginação, ordenação e filtros de transação.
- [ ] Adicionar exemplos seguros para operações principais.
- [ ] Documentar respostas de sucesso e de erro relevantes, incluindo `ProblemDetail`.
- [ ] Conferir se DTOs exibidos no Swagger não expõem campos internos como `passwordHash` ou `userId` controlado pelo servidor.

#### Arquitetura e qualidade

- [ ] Documentar as decisões: monólito modular, JWT stateless, PostgreSQL, Flyway, dados derivados por consulta e isolamento por usuário.
- [ ] Explicar como as constraints de banco e regras de aplicação se complementam.
- [ ] Registrar a estratégia de testes unitários, integração e PostgreSQL em contêiner.
- [ ] Registrar a estratégia de Docker e deploy, incluindo configuração externa de segredos.

#### Screenshots e validação final

- [ ] Capturar Swagger UI da API publicada ou local, sem token visível.
- [ ] Capturar health check ou tela de execução da stack, sem dados sensíveis.
- [ ] Se usar screenshots de requisições, ocultar e-mails reais, UUIDs de produção, tokens e senhas.
- [ ] Conferir todos os links, comandos e nomes de endpoint do README.
- [ ] Executar a seção de instalação do README em ambiente limpo ou por outra pessoa, se possível.
- [ ] Revisar gramática, consistência de nomenclatura e ausência de instruções obsoletas.

### Critério de conclusão

A fase está concluída quando um avaliador consegue clonar, configurar, iniciar, testar e explorar a API usando apenas a documentação. O README, Swagger e documentos de arquitetura estão alinhados ao comportamento real da versão publicada, e as capturas não expõem dados sensíveis.

### Possíveis dificuldades e pontos de atenção

- **Documentação desatualizada:** revisar depois do deploy final, não apenas no início do projeto.
- **Swagger como única documentação:** ele descreve contrato, mas não substitui instalação, arquitetura e decisões.
- **Screenshots com segredo:** tokens de JWT podem ser reutilizáveis até expirar; nunca publicá-los.
- **Prometer o que não existe:** documentar somente recursos implementados no MVP.

---

## Cronograma sugerido para estudo individual

O cronograma abaixo pressupõe dedicação média de **8 a 12 horas por semana**. O prazo é uma referência; a conclusão de cada fase deve depender de seus critérios, não de uma data fixa.

| Semana | Fase | Resultado esperado |
| --- | --- | --- |
| 1 | Fase 1 | Projeto, PostgreSQL em Docker, Flyway, Swagger e health check prontos. |
| 2 | Fase 2 | Modelo inteiro criado por migrations e validado no PostgreSQL. |
| 3–4 | Fase 3 | Cadastro, login, JWT e segurança testados. |
| 5 | Fase 4 | CRUD de contas com regras e testes. |
| 6 | Fase 5 | CRUD de categorias com regras e testes. |
| 7–8 | Fase 6 | CRUD de transações, filtros, paginação e testes. |
| 9 | Fase 7 | Resumo por período correto e testado. |
| 10 | Fase 8 | Validações e tratamento de erros consolidados. |
| 11 | Fase 9 | Suíte de testes completa, estável e integrada ao CI. |
| 12 | Fase 10 | Execução integral por Docker. |
| 13 | Fase 11 | Aplicação publicada e validada externamente. |
| 14 | Fase 12 | README, Swagger, arquitetura e evidências finalizados. |

Se a disponibilidade for menor, estender o cronograma preservando a sequência. Não antecipar deploy antes de ter segurança e testes dos fluxos críticos.

## Ordem ideal de aprendizado

1. Java 21 aplicado a classes, enums, `BigDecimal`, exceções e coleções.
2. Git/GitHub e organização de repositório.
3. Spring Boot: projeto, configuração, injeção de dependência, REST e perfis.
4. PostgreSQL: modelagem relacional, SQL, constraints e índices.
5. Docker e Docker Compose para dependências locais.
6. Flyway e migrations imutáveis.
7. JPA/Hibernate: entidades, relacionamentos, repositórios, paginação e consultas.
8. Bean Validation, DTOs e padronização de respostas HTTP.
9. Spring Security, hash de senha, JWT e autorização por proprietário.
10. JUnit 5, Mockito, testes de integração e Testcontainers.
11. Swagger/OpenAPI como contrato executável.
12. Dockerfile, variáveis de ambiente, CI e deploy de contêiner.

Essa ordem permite aprender cada fundamento pouco antes de utilizá-lo na fase correspondente e evita implementar segurança ou persistência sem base suficiente.

## Checklist geral do projeto

### Base técnica

- [ ] Java 21 e Spring Boot configurados.
- [ ] Repositório GitHub organizado e sem segredos.
- [ ] PostgreSQL local em Docker.
- [ ] Flyway como única fonte de criação/evolução do schema.
- [ ] OpenAPI/Swagger e health check disponíveis.

### Banco e domínio

- [ ] Tabelas `users`, `financial_accounts`, `categories` e `transactions` criadas por migrations.
- [ ] UUIDs, timestamps, valores monetários, enums e relacionamentos seguem `modelagem.md`.
- [ ] Constraints, foreign keys compostas e índices essenciais estão presentes.
- [ ] Dados de usuários distintos não podem ser relacionados indevidamente.

### Segurança

- [ ] Cadastro e login funcionam.
- [ ] Senhas são somente hashes seguros.
- [ ] JWT é assinado, expira e é lido de configuração externa.
- [ ] Rotas financeiras exigem autenticação.
- [ ] Propriedade é validada em contas, categorias, transações, filtros e resumo.

### Funcionalidades do MVP

- [ ] CRUD de contas, com preservação de histórico.
- [ ] CRUD de categorias, com tipo e unicidade corretos.
- [ ] CRUD de transações, com validações de valor, data, conta e categoria.
- [ ] Paginação, ordenação e filtros de transação.
- [ ] Resumo por período: receitas, despesas e saldo líquido.

### Qualidade e entrega

- [ ] Validações e respostas de erro padronizadas por `ProblemDetail`.
- [ ] Testes unitários cobrem regras críticas.
- [ ] Testes de integração usam PostgreSQL em Testcontainers.
- [ ] Cobertura de aplicação de pelo menos 70%, com regras críticas diretamente testadas.
- [ ] Dockerfile e Docker Compose executam a stack completa.
- [ ] API está publicada com PostgreSQL gerenciado e segredos externos.
- [ ] README, Swagger, documentos e screenshots representam o estado real do projeto.

## Definição de MVP concluído

O MVP 1.0 está concluído somente quando todos os itens abaixo forem verdadeiros:

1. A API está publicada e pode ser explorada por Swagger, com endpoint de saúde disponível.
2. Um usuário pode se cadastrar, autenticar-se com JWT e acessar somente os seus recursos.
3. Contas e categorias podem ser administradas, respeitando suas regras de histórico.
4. Transações de receita e despesa podem ser criadas, filtradas, paginadas, atualizadas e excluídas com todas as validações previstas.
5. O resumo por período retorna receitas, despesas e saldo líquido corretos.
6. O PostgreSQL é criado e evoluído exclusivamente por migrations Flyway, com constraints e índices definidos na modelagem.
7. A aplicação roda localmente com Docker e em produção com configurações externas e banco gerenciado.
8. Testes unitários e de integração cobrem fluxos críticos, passam no CI e dão suporte à manutenção do projeto.
9. Falhas conhecidas seguem formato uniforme, sem vazamento de detalhes internos ou dados sensíveis.
10. A documentação permite a outra pessoa executar e avaliar o projeto sem orientação adicional.

## Critérios para decidir quando iniciar a versão 1.1

Iniciar uma versão 1.1 somente se **todos** os critérios de conclusão do MVP acima estiverem atendidos e a estabilidade for comprovada. Antes de iniciar novo planejamento, confirmar:

- [ ] Não há falhas conhecidas nos fluxos de cadastro, login, autorização, CRUD de transações e resumo.
- [ ] Todas as migrations do MVP foram aplicadas e validadas no ambiente publicado.
- [ ] A suíte de testes está verde no CI e cobre regras críticas.
- [ ] O deploy está documentado e pode ser repetido sem alteração manual de dados.
- [ ] O README e Swagger correspondem à API realmente publicada.
- [ ] O escopo da versão 1.0 foi revisado e não há pendências essenciais disfarçadas de melhoria futura.

Atender a esses critérios significa que a versão 1.0 é uma entrega profissional fechada. A próxima versão deve começar por uma decisão de produto separada, sem misturar novas funcionalidades com correções pendentes do MVP.
