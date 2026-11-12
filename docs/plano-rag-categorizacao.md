# Plano de implementação do RAG na categorização do chat

## Decisão e escopo

**Situação em 12 de novembro de 2026:** integração, índice, busca e métricas de
cobertura implementados e validação do piloto local aprovada. A
[rodada mais recente](avaliacao-rag-transporte-2026-11-12.md) teve 19 categorias
corretas no RAG contra 18 no catálogo, sem falhas de geração e com valor e tipo
corretos nos vinte casos. Houve uma correção, zero regressões e um fallback de
busca com resposta válida. O p95 ficou dentro da meta. O fluxo HTTP do piloto,
incluindo o job registrado, passou com dados fictícios e provedores simulados.
A flag padrão continua desligada; a ativação de histórico real segue as condições
operacionais e de privacidade deste plano. Os resultados de 11 de novembro abaixo
são históricos e não substituem a rodada mais recente.

O [diagnóstico de geração e resiliência](diagnostico-rag-resiliencia.md) registra
a reprodução de HTTP 503, a política de tentativas compartilhada, os testes HTTP
locais e o estado da nova avaliação com critérios de aceite automáticos.

Entregar as categorias reais como base da categorização em `POST /api/v1/transactions/chat`. Acrescentar exemplos do histórico confirmado do próprio usuário quando o RAG estiver habilitado e houver resultados relevantes. A resposta da API continua usando o contrato atual de rascunhos, esclarecimentos e sugestões de categoria.

Na avaliação local de 11 de novembro de 2026, categorias no prompt acertaram 16 dos 19 casos comparáveis e categorias com RAG acertaram 18. O histórico corrigiu `trabalho` e `uber-show`, com zero regressões nesses pares. As medianas observadas foram 2,44 s e 2,05 s, respectivamente. A amostra é fictícia e orienta um piloto. [Como reproduzir a avaliação](avaliacao-sugestoes-transacoes.md)

## Fluxo do chat

1. O `TransactionChatController` resolve o usuário autenticado, o contexto da conversa e o replay idempotente. O `TransactionSuggestionService` monta o contexto do modelo.
2. Carregar as categorias atuais do usuário via `CategoryService` e aproveitar o `ChatCategoryContext` experimentado. O catálogo informa ID, nome e tipo de cada categoria. O Gemini sugere um ID ou deixa a categoria em branco quando nenhuma se aplica.
3. No piloto, habilitar a busca para mensagens iniciais com texto útil para categorizar e sem sinais de lote reconhecidos pelas regras atuais. Respostas de esclarecimento e lotes reconhecidos usam o catálogo. Se o modelo identificar vários lançamentos numa entrada que passou pela triagem, a instrução do prompt manda categorizar cada item pelo seu trecho e pelo catálogo.
4. Quando a busca estiver habilitada e houver vetores válidos para o usuário, gerar o embedding do texto atual e recuperar até três exemplos. Se a busca vier vazia, expirar ou falhar, montar o prompt apenas com o catálogo e a conversa. A busca tem orçamento inicial de 2 s, incluindo a chamada de embedding e o SQL, sem nova tentativa no caminho da resposta.
5. Fazer uma chamada de geração ao Gemini com o catálogo e, quando disponíveis, os exemplos. Passar catálogo e histórico como dados estruturados e instruir o modelo a ignorar comandos contidos nesses campos. Os exemplos servem apenas para escolher a categoria. Descrição, valor, tipo, data e quantidade de rascunhos continuam vindo da mensagem e do contexto da conversa.
6. Validar cada categoria sugerida contra o catálogo enviado, o usuário autenticado, a categoria atual no banco e o tipo do rascunho. Um ID ausente, inexistente, de outro usuário ou de tipo incompatível resulta em rascunho sem categoria. Preservar as validações atuais e a confirmação pelo usuário.

## Índice e ciclo de vida

| Decisão | Implementação |
|---|---|
| Banco | PostgreSQL com `pgvector`, via nova migration Flyway após a V10. Usar uma tabela auxiliar com uma linha por transação, FK para `transactions.id` com `ON DELETE CASCADE`, hash da descrição de origem, versão do embedding e vetor. |
| Modelo inicial | `gemini-embedding-001` com 768 dimensões, mantendo a configuração validada no experimento. Usar `RETRIEVAL_DOCUMENT` na indexação e `RETRIEVAL_QUERY` na busca. |
| Busca | Similaridade exata por cosseno, top 3 e limiar inicial configurável de 0,55. Filtrar pelo usuário autenticado, pela versão do embedding e pela descrição de origem vigente antes do limite. Ler categoria e tipo da transação atual. |
| Fonte | Linhas confirmadas da tabela `transactions`, criadas manualmente ou pelo fluxo de confirmação do chat, com descrição elegível. O catálogo, a transação e sua categoria atual são as fontes de verdade. |
| Execução | Um job agendado reconcilia transações elegíveis e embeddings faltantes ou desatualizados em lotes pequenos. O backfill do piloto começa pelos 100 lançamentos elegíveis mais recentes de cada usuário habilitado e acompanha os novos após a ativação. |

Usar o mesmo perfil de embedding na gravação e na consulta. Uma troca de modelo ou de normalização muda a versão e dispara a reconstrução em lotes. Uma troca de dimensão também exige adaptar o schema. [Embeddings do Gemini](https://ai.google.dev/gemini-api/docs/embeddings?hl=pt-br) · [Busca com pgvector](https://github.com/pgvector/pgvector#querying)

- **Criação e backfill.** A indexação começa depois de a transação existir no banco. O job reserva cada item em uma transação curta e chama o Gemini fora dela. O cadastro e a confirmação no chat seguem pelo fluxo existente.
- **Edição de descrição.** A busca ignora o vetor antigo assim que o hash deixa de corresponder à descrição atual. O job regenera o vetor em segundo plano. Se a descrição for removida ou ficar inelegível, limpar a linha auxiliar.
- **Edição de categoria, tipo, valor ou data.** O vetor pode ser reaproveitado quando o texto permanece igual. A consulta lê a categoria e o tipo vigentes da transação.
- **Exclusão.** A FK remove o vetor e o trabalho pendente junto com a transação. A exclusão do histórico de conversa atua sobre as mensagens e os rascunhos.
- **Concorrência e recuperação.** Reservar trabalho com prazo de expiração e gravar o vetor apenas se a reserva, o hash e a versão ainda corresponderem. Em erro transitório, registrar nova tentativa com espera crescente. A rotina recupera trabalho abandonado quando a API reinicia.

Alinhar o Docker Compose e o Testcontainers com PostgreSQL 17 que inclua `pgvector`, preservando os dados locais. No Neon, verificar a instalação da extensão e a permissão da conta de migração em um ambiente de teste antes de aplicar a migration de produção. [Extensão pgvector no Neon](https://neon.com/docs/extensions/pgvector)

## Dados enviados ao provedor

- **Geração de embeddings.** Enviar somente o texto necessário para a similaridade. Normalizar espaços, remover valores monetários explícitos e mascarar padrões reconhecíveis de e-mail, CPF, telefone e dados de conta. Aplicar a mesma regra ao texto de busca e às descrições indexadas.
- **Exemplos no prompt do chat.** Enviar a descrição minimizada, o ID da categoria e o tipo da transação. Manter valores, datas, saldos, contas e IDs de usuário ou de transação fora dos exemplos.
- **Persistência e telemetria.** A tabela auxiliar guarda o vetor, a referência, o hash e o estado do processamento. Os logs operacionais registram contagens, tempos e códigos de erro. Descrições livres ainda podem conter dados pessoais.

Manter os testes locais e o benchmark com dados fictícios. Para ativar a indexação com histórico real, usar uma configuração contratual do provedor adequada a esses dados e revisar o aviso de privacidade do app. Os termos do Google orientam a excluir informações pessoais, confidenciais ou sensíveis dos serviços gratuitos. [Termos da API Gemini](https://ai.google.dev/gemini-api/terms?hl=pt-br)

## Ordem de entrega

1. **Integrar o catálogo ao chat.** Usar `ChatCategoryContext` no caminho real de `suggestChat` e cobrir categoria válida, categoria ausente, tipo incompatível, mudança de categoria durante a geração, lotes, esclarecimentos e replay.
2. **Criar o índice e o job.** Aplicar a migration, integrar o cliente de embeddings usado no protótipo e validar backfill, edição, exclusão, retomada e concorrência em PostgreSQL real. Usar um provedor simulado nos testes padrão.
3. **Acoplar a busca ao chat.** Usar uma flag `TRANSACTION_CHAT_RAG_ENABLED` inicialmente desligada. Em ambiente compartilhado, começar com usuários do piloto definidos no backend. A mesma habilitação governa a busca e a indexação, incluindo o backfill. Desligar a flag devolve o fluxo ao catálogo e pausa o job.
4. **Validar e liberar.** Rodar a avaliação de 20 casos pelo fluxo de aplicação, com a mesma normalização e o mesmo orçamento da busca. Ativar em ambiente de teste e depois para o grupo piloto. A decisão de ligar por padrão vem após comparar as medições do piloto.

## Critérios de aceite

- Flag desligada, histórico vazio, baixa similaridade, erro de embeddings ou timeout seguem pelo processamento com catálogo, dentro do contrato atual.
- Testes HTTP e PostgreSQL verificam isolamento entre usuários, confirmação idempotente, exclusão em cascata, atualização da descrição e uso da categoria vigente.
- Testes de prompt exercitam descrições com instruções enganosas e verificam que os exemplos não alteram valores ou criam rascunhos adicionais.
- A avaliação pareada separa correções, regressões e falhas. A configuração candidata tem mais correções que regressões de categoria e preserva a extração de valor e tipo na suíte de avaliação. Casos sem exemplos são identificados na leitura do resultado.
- Medir latência mediana e p95 do chat, tempo de busca, taxa de fallback, cobertura de vetores e proporção de categorias sugeridas que foram mantidas na confirmação. O objetivo inicial do piloto é manter o p95 do chat com RAG a até 2 s acima da versão com catálogo. A categoria mantida na confirmação é um indicador de uso.
