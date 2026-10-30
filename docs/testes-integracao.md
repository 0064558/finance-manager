# Contrato dos testes de integração

O comportamento proposto foi aprovado pelo responsável pelo produto nesta
conversa. As decisões adicionais foram:

- **D1:** rejeitar a segunda criação de conta com os mesmos dados, com `409`.
- **D2:** reenvios da mesma operação financeira, identificados pela mesma chave
  `Idempotency-Key`, devem retornar o mesmo lançamento e produzir um único efeito
  financeiro, também sob concorrência. Não foi fixado um status específico entre
  os códigos de sucesso para a resposta do reenvio. Operações distintas continuam
  independentes; a chave deve respeitar o isolamento entre usuários.
- **D3:** edições concorrentes são aceitas; a última gravação prevalece. O
  resultado deve ser uma edição completa, sem misturar os campos das duas.

Após revisar as primeiras execuções, o responsável pelo produto também aprovou:

- Erros de validação podem identificar o campo `password`, mas nunca expor
  valores de senha, hashes ou propriedades com credenciais. O teste verifica
  propriedades recursivamente e rejeita valores de senha e hashes BCrypt.
- A rota `expenses-by-category` usa `totalExpense` em cada categoria; a cobrança
  do valor exato de `30,33` foi mantida.
- Quando a exclusão de uma conta/categoria vence a disputa com um lançamento,
  o lançamento pode retornar `404` ou `409`. Continua proibido persistir um
  lançamento órfão ou retornar sucesso para ambas as operações incompatíveis.
- Em 29/10/2026, a verificação de isolamento da listagem passou a comparar os
  campos do JSON com os sete lançamentos esperados de A. A busca textual por
  `999` causou um falso positivo ao encontrar essa sequência em um UUID legítimo.
  O usuário aprovou conferir os IDs completos, contas, categorias e valores em
  seus campos. Nenhum lançamento de B pode aparecer; paginação, ordenação,
  filtros e respostas `404` para referências de B continuam sendo exigidos.

Não existem checkout, gateway, webhooks de pagamento, estoque ou planos no app
atual. Não foi adicionado E2E de navegador, que depende de aprovação específica.

## Execução

Na raiz do projeto, com Java 21+, Docker instalado e `npm ci` já executado em
`finance-manager-web`:

```powershell
.\test.ps1
```

O comando executa `mvnw verify` e `npm test -- --watch=false`, incluindo os testes
anteriores. A falha da API não impede a execução do frontend. Qualquer falha deixa
o comando final com código 1. O Maven mantém a exigência existente de 70% de
cobertura; se testes falham, a fase `verify` não chega a produzir uma nova
verificação de cobertura aprovada.

Antes do Maven, o script verifica se o Docker responde e usa containers Linux.
No Windows, abre o Docker Desktop instalado no caminho padrão quando ele está
fechado, e aguarda até 120 segundos. Não altera o contexto nem as configurações
do Docker. O prazo pode ser ampliado com
`.\test.ps1 -DockerStartupTimeoutSeconds 240`. Se o Docker não estiver instalado,
estiver usando containers Windows ou não iniciar no prazo, a API fica com código
1 e uma orientação clara; o frontend ainda executa. Nenhum teste é marcado como
ignorado, e não há substituição por outro banco.

## Ambiente e isolamento

- JUnit 5, Spring Boot e Testcontainers já eram usados no projeto; não foi
  adicionada outra ferramenta de testes.
- Os request specs iniciam o servidor HTTP real somente em `127.0.0.1`, com porta
  aleatória, e usam `java.net.http.HttpClient`.
- Controllers, validações, JWT, BCrypt, serviços, transações JPA, consultas,
  constraints e migrations Flyway executam de verdade. As regras de propriedade
  existentes são exercitadas na aplicação e nas constraints PostgreSQL; o projeto
  não usa policies Supabase.
- O banco é `finance_manager_test` em um contêiner PostgreSQL 17 descartável.
  Tanto o datasource quanto a conexão do Flyway são definidos dinamicamente pelo
  contêiner, sobrepondo configurações externas. Um inicializador recusa perfis
  diferentes de `test` e URLs que não sejam a desse contêiner antes de subir o
  contexto. Não há leitura de `.env` nem chamadas à API publicada.
- Os dados são limpos entre testes. As classes executam em sequência porque
  compartilham o banco descartável. A concorrência dentro de cada cenário é real:
  duas tarefas esperam uma barreira comum antes de enviar suas requisições HTTP.
- Nenhum mock foi adicionado nos request specs. Não existem serviços externos
  nesses fluxos que precisem ser simulados. Os testes unitários anteriores,
  incluindo seus mocks, foram preservados.

## Cobertura por comportamento

Cada classe usa `describe` no `@DisplayName`, e cada teste usa uma frase `it
quando..., então...` em português. Casos parametrizados exercitam separadamente
os limites e formatos relevantes.

| Classe em `requestspec/` | Comportamentos |
| --- | --- |
| `AuthRequestSpecTest` | Cadastro, limites, BCrypt, e-mail único em concorrência, credenciais, todas as rotas protegidas com tokens ausentes/inválidos/expirados/de outra chave/de usuário inexistente, logins simultâneos e isolamento da identidade. |
| `OnboardingRequestSpecTest` | Persistência, repetição sequencial e simultânea, regressão, versão negativa/futura/fracionária, corrida entre versões zero e um e propriedade pelo JWT. |
| `AccountsAndCategoriesRequestSpecTest` | Limites de valores e nomes, campos obrigatórios, CRUD, isolamento, contas duplicadas, categorias únicas por usuário/tipo, proteção de histórico e disputas entre alterações/exclusões e novos lançamentos. |
| `TransactionsRequestSpecTest` | Ciclo financeiro, centavos e limites, erros com preservação do estado, referências e acesso entre usuários, movimentação entre contas, exclusão repetida/concorrente, PUT repetido, idempotência e última gravação prevalece. |
| `ReportsAndQueriesRequestSpecTest` | Filtros combinados, datas inclusivas, paginação, ordenação, páginas vazias, totais precisos/negativos, dias sem movimentos, contas sem lançamentos, categorias, isolamento em todos os relatórios e leituras simultâneas sem escrita. |
| `ErrorContractRequestSpecTest` | JSON malformado, UUID inválido, recurso inexistente e erros públicos padronizados sem informações internas. |
| `IdempotencyRequestSpecTest` | Chave com dados diferentes, rollback da reserva em falhas, reenvio após exclusão sem recriação, limites da chave e preflight CORS do navegador. |

Os cenários de corrida envolvendo histórico e exclusões são repetidos cinco
vezes, sem `sleep`. O agendamento de threads e commits pertence ao servidor e ao
PostgreSQL: essas execuções exercitam concorrência, mas não provam todos os
interleavings possíveis. Na disputa entre primeiro lançamento e mudança de saldo
inicial, o teste valida os estados finais compatíveis com uma ordem válida; não
observa o instante interno de cada leitura/gravação.

## Correções da aplicação em 29/10/2026

- A migração V6 impede contas com os mesmos usuário, nome após `trim`, tipo e
  saldo inicial. Não exige nome único entre contas com outros tipos ou saldos.
  A restrição também protege criações simultâneas e alterações. Se já houver
  contas duplicadas em um banco existente, a migração para sem apagar nem unir
  registros: esses dados precisam ser revisados antes de aplicar a restrição.
- A migração V7 adiciona `transaction_idempotency`. A reserva da chave, o
  lançamento e a resposta original são gravados na mesma transação de banco.
  Uma chave concorrente espera a primeira transação e reutiliza sua resposta.
  Uma falha desfaz tanto a reserva quanto o lançamento. A chave pertence ao
  usuário autenticado e não pode ser reutilizada com outros dados (`409`).
  Chaves vazias ou com mais de 200 caracteres retornam `400`.
- O app envia `Idempotency-Key` e mantém a chave quando o usuário tenta enviar
  novamente os mesmos dados no formulário após falha. Dados alterados ou uma
  nova operação recebem outra chave. O formulário também bloqueia um segundo
  envio enquanto aguarda a resposta. A chave do formulário fica em memória;
  fechar/reabrir o formulário ou recarregar a página inicia outra operação.
  Clientes antigos da API ainda podem omitir o cabeçalho, caso em que cada
  criação é uma nova operação. CORS permite o cabeçalho.
- A resposta de idempotência é um retrato da criação, inclusive seus horários.
  Um reenvio após editar ou excluir o lançamento retorna esse retrato, sem
  desfazer a edição nem recriar o lançamento excluído. Não há expiração
  automática das chaves nesta implementação.
- Gravações de lançamentos bloqueiam a conta e depois a categoria até o commit.
  Alterações/exclusões desses recursos usam os mesmos bloqueios antes de
  consultar o histórico. As consultas comuns continuam sem bloqueio de escrita.
  Edições de lançamentos mantêm a regra de última gravação prevalecer (D3).
- A exclusão de lançamento usa uma única operação no banco filtrada por ID e
  usuário: uma linha removida retorna `204`; nenhuma linha retorna `404`.
- A desserialização JSON rejeita números decimais em campos inteiros, antes da
  validação de onboarding.

Os request specs existentes mantiveram suas expectativas. Nos testes unitários
anteriores, os arranjos dos repositórios foram adaptados aos métodos com
bloqueio e à exclusão atômica, mantendo as verificações de permissão e validação.
O teste de esquema passou a exigir as sete migrações e a quinta tabela.
Os testes do frontend também verificam reenvio, novas operações e duplo envio.

## Regras de manutenção

Não apagar, desabilitar, marcar como `skip` nem enfraquecer teste para deixar a
suíte verde. Se a expectativa parecer incorreta, consultar o responsável pelo
produto antes de alterá-la. Falhas confirmadas por comportamento do app ficam
registradas em `docs/bugs-testes-integracao.md`, com o teste ainda falhando.
