# Bugs encontrados pelos request specs

Execução inicial em 28/10/2026, contra servidor HTTP local e PostgreSQL 17
descartável. O banco de produção não foi utilizado. Os bugs abaixo não foram
corrigidos: os testes que os detectam permanecem ativos e falhando.

## Resultado da execução do comando único

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
