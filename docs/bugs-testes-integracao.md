# Bugs encontrados pelos request specs

## Falha de infraestrutura em 30/10/2026

O log enviado pelo usuário começou com `Could not find a valid Docker
environment`. O PostgreSQL descartável não iniciou, e a falha de inicialização
se propagou como `NoClassDefFoundError` nas classes de integração. O Maven
registrou 214 execuções, zero falhas de asserção, 147 erros de execução e zero
ignorados; o frontend passou nos 80 testes. Esse resultado não valida os fluxos
da API, pois o banco necessário estava indisponível.

O comando `test.ps1` agora verifica a disponibilidade do engine Linux antes do
Maven. No Windows, inicia o Docker Desktop do caminho padrão se estiver fechado
e aguarda até 120 segundos, com mensagens de progresso. O limite é configurável
por `-DockerStartupTimeoutSeconds`. Se o Docker não estiver disponível, a API
fica com código 1 e uma mensagem de correção, e o frontend continua executando.
O contexto do Docker não é alterado; nenhum teste nem regra da aplicação foi
modificado por essa correção, e o banco continua descartável e separado.

Validação em 30/10/2026: `./test.ps1` terminou com código 0, com 226 testes da
API e 80 do frontend aprovados, nenhum ignorado e cobertura aprovada. O log
local está em `outputs/all-tests-docker-startup.log` (ignorado pelo Git).
Também foi conferido, em uma cópia isolada do comando no Windows PowerShell,
que a ausência do Docker impede o Maven, ainda executa o frontend e retorna 1.
Os caminhos de início do Desktop e expiração do prazo foram verificados
separadamente com chamadas de infraestrutura simuladas; a suíte da aplicação
continuou usando PostgreSQL real em Testcontainers.

## Situação após as correções em 29/10/2026

Os cinco bugs foram corrigidos na aplicação, com autorização do usuário.
O comando `./test.ps1` foi executado novamente contra PostgreSQL 17 descartável,
com as migrações V1 a V7. O banco de produção não foi utilizado.

| Suíte | Executados | Passaram | Falhas | Erros de execução | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| API | 226 | 226 | 0 | 0 | 0 |
| Frontend | 80 | 80 | 0 | 0 | 0 |

A verificação de cobertura da API passou: 678 de 713 linhas (95,09%), acima do
mínimo existente de 70%, que foi mantido.

| Bug | Correção |
| --- | --- |
| BUG-01 | Índice único da migração V6 para usuário, nome normalizado, tipo e saldo inicial; duplicação retorna 409, inclusive em concorrência. |
| BUG-02 | Reserva e resposta por usuário/chave na tabela V7, na mesma transação do lançamento. O frontend envia a chave e a reutiliza em reenvios. |
| BUG-03 | Bloqueios de banco compartilhados entre alteração de categoria e gravação do lançamento; validação de histórico acontece após adquirir o bloqueio. |
| BUG-04 | Exclusão atômica por ID e usuário, usando a quantidade removida para retornar 204 ou 404. |
| BUG-05 | Conversão de JSON decimal para inteiro desabilitada; a versão fracionária retorna 400. |

Os contratos de negócio aprovados dos request specs foram preservados. Foram
adicionados cinco testes HTTP de proteção da idempotência e cinco testes do
frontend sobre envio/reenvio. Durante a implementação, o teste novo de resposta
original detectou conversão do fuso horário no replay; a desserialização da
resposta armazenada foi corrigida para preservar os horários originais.
Nenhum teste foi removido, ignorado ou afrouxado.

A migração V6 não apaga contas duplicadas existentes: se houver duplicatas,
ela interrompe a atualização para permitir a revisão desses registros.
Detalhes da implementação e limites da chave no formulário estão em
`docs/testes-integracao.md`.

### Correção de falso positivo aprovada em 29/10/2026

Uma execução posterior do usuário teve uma única falha em
`ReportsAndQueriesRequestSpecTest.paginationAndFilters`: a asserção buscava
`999` no corpo JSON inteiro. A sequência apareceu no UUID de uma categoria de A
(`7a44a236-422c-4e50-9999-e426d3a7a095`), sem indicar vazamento de dados de B.
O frontend passou nos 80 testes nessa execução; a API teve 225 aprovações e
esse falso positivo.

Conforme a regra de consultar o responsável antes de corrigir um teste errado,
a alteração foi interrompida para confirmação. O usuário aprovou explicitamente
comparar os campos do JSON. O teste agora exige exatamente os sete IDs esperados
de A e confere conta, categoria, tipo, data e valor exato de cada lançamento.
Também rejeita explicitamente o ID do lançamento e as referências de B.
As asserções de paginação, ordenação, filtros e acesso indevido foram mantidas.

Após essa correção, `./test.ps1` foi executado em 29/10/2026: API com 226 testes
aprovados, frontend com 80 testes aprovados, nenhuma falha, erro ou teste
ignorado. A verificação de cobertura passou e ambas as suítes terminaram com
código 0. O log está em `outputs/all-tests-json-fields.log` (arquivo local
ignorado pelo Git).

## Histórico da execução inicial

Execução inicial em 28/10/2026, contra servidor HTTP local e PostgreSQL 17
descartável. Naquela execução, os bugs ainda não tinham sido corrigidos e os
testes correspondentes foram mantidos falhando. Os resultados abaixo registram
essa situação anterior às correções.

`./test.ps1` foi executado em 28/10/2026, com os seguintes resultados:

| Suíte | Executados | Passaram | Falhas de asserção | Erros de execução | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| API, incluindo testes anteriores | 221 | 206 | 15 | 0 | 0 |
| Frontend | 75 | 75 | 0 | 0 | 0 |

Foram adicionadas 101 execuções de request specs HTTP e uma verificação do nome
e usuário do banco descartável. Os testes anteriores foram preservados.

O comando executou o frontend mesmo após a falha da API e terminou com código 1.
As 15 falhas da API correspondem exclusivamente aos cinco bugs abaixo; os casos
de concorrência repetidos contam individualmente. As três correções de teste
aprovadas pelo usuário estão descritas ao fim deste documento e passaram.
Nenhuma expectativa dos testes que detectam bugs foi alterada. Os números de
falhas em cenários repetidos de concorrência podem variar conforme o agendamento.
O Maven não chegou à fase de verificação de cobertura porque a suíte falhou.

## BUG-01 — Contas idênticas são criadas mais de uma vez

- **Contrato aprovado (D1):** a segunda criação de conta com os mesmos dados para
  o mesmo usuário deve retornar `409`, mantendo uma única conta.
- **Reprodução:** enviar duas vezes `POST /api/v1/financial-accounts` com mesmo
  nome, tipo e saldo inicial; repetir com duas requisições simultâneas.
- **Observado:** as duas requisições retornam `201`, com identificadores distintos.
- **Impacto:** duplica contas e pode duplicar o saldo inicial consolidado.
- **Testes:** `AccountsAndCategoriesRequestSpecTest.duplicateAccount` e
  `concurrentDuplicateAccounts`.

## BUG-02 — Chave de idempotência não impede lançamento duplicado

- **Contrato aprovado (D2):** reenvios com a mesma `Idempotency-Key` e dados devem
  retornar o mesmo lançamento, produzindo um único efeito financeiro.
- **Reprodução:** enviar o mesmo `POST /api/v1/transactions` com a mesma chave,
  primeiro em sequência e depois simultaneamente.
- **Observado:** cada requisição retorna um identificador diferente. A API não
  implementa o tratamento dessa chave.
- **Impacto:** um reenvio de receita ou despesa é contabilizado novamente.
- **Testes:** `TransactionsRequestSpecTest.idempotentReplay` e
  `concurrentIdempotentReplay`.

## BUG-03 — Corrida permite categoria incompatível com o lançamento

- **Contrato aprovado:** o tipo da categoria deve coincidir com o da transação,
  inclusive quando a mudança de tipo disputa com um novo lançamento.
- **Reprodução:** criar categoria `EXPENSE`, enviar simultaneamente sua alteração
  para `INCOME` e um lançamento `EXPENSE` usando essa categoria.
- **Observado:** a alteração retorna `200` e o lançamento retorna `201`. Uma
  execução válida deve rejeitar uma das operações com `409`.
- **Impacto:** a validação de histórico e compatibilidade pode ser ultrapassada
  por requisições concorrentes.
- **Teste:** `AccountsAndCategoriesRequestSpecTest.typeChangeRace`.
- **Execução inicial:** falhou nas cinco repetições.

## BUG-04 — Exclusão simultânea do mesmo lançamento retorna erro 500

- **Contrato aprovado:** uma exclusão retorna `204`; a outra retorna `404`, sem
  contabilizar a remoção duas vezes.
- **Reprodução:** disparar dois `DELETE /api/v1/transactions/{id}` ao mesmo tempo.
- **Observado:** uma resposta `204` e outra `500`. O log registra
  `ObjectOptimisticLockingFailureException` porque a segunda exclusão tenta
  remover uma linha já excluída. O erro é tratado como inesperado.
- **Impacto:** uma operação repetida esperada aparece como falha interna.
- **Teste:** `TransactionsRequestSpecTest.concurrentDelete`.
- **Execução inicial:** falhou nas cinco repetições.

## BUG-05 — Onboarding aceita versão fracionária

- **Contrato aprovado:** a versão precisa ser inteira; valor fracionário deve
  retornar `400`.
- **Reprodução:** enviar `PATCH /api/v1/auth/me/onboarding` com
  `{"onboardingVersion":0.5}`.
- **Observado:** retorna `200` com versão zero. A desserialização converte o valor
  fracionário em inteiro antes da validação.
- **Impacto:** aceita corpo inválido em vez de comunicar o erro ao cliente.
- **Teste:** `OnboardingRequestSpecTest.invalidVersion`, caso `0.5`.

## Correções dos testes aprovadas pelo responsável pelo produto

As três questões abaixo foram consultadas antes de qualquer alteração e
aprovadas pelo usuário em 28/10/2026. Não são bugs da aplicação nem pendências.

1. **Sigilo:** passou a permitir `{"field":"password"}` para identificar o campo
   inválido. Continua proibindo propriedades com credenciais em qualquer nível
   da resposta, o valor da senha, hashes BCrypt e detalhes internos.
2. **Despesas por categoria:** passou a consultar o campo existente
   `categories[0].totalExpense`, preservando a cobrança exata de `30,33`.
3. **Exclusão disputando com lançamento:** passou a aceitar o par de respostas
   `204/409`, além de `204/404`, quando a exclusão vence. Continua exigindo
   `409/201` quando o lançamento vence. A verificação agora também confere que
   não há lançamento vinculado ao recurso excluído e, quando o lançamento é
   criado, consulta seu ID para confirmar conta, categoria e valor persistidos.

As três verificações passaram após essas correções. Os testes dos cinco bugs
acima permanecem ativos e sem alterações nas expectativas.
