# Avaliação comparativa do RAG local de categorização

Em 22/11/2026, o catálogo sozinho e o catálogo com RAG local acertaram 18 dos
20 casos ao receber as mesmas decisões de categoria registradas em 12/11. Ambos
deixaram em branco apenas o caso cujo gabarito pedia categoria vazia. Em um
cenário em que a geração entregou todos os rascunhos sem categoria, o RAG local
preencheu dois corretamente. A avaliação usou apenas recursos locais e dados
fictícios.

Commit base `39ee042d29a9156b5fdc4dafe264215fb8769e6e`. Branch
`codex/avaliacao-comparativa-rag`.

## Alternativas confirmadas

O parecer de `verifique-melhorias-serem` pede repetir com vetores locais a
comparação que obteve 19/20 usando embeddings do Gemini. O parecer está na tarefa
Codex `01a20495-e169-7bd0-8648-ec22f9d3e7f0`, na linha “Reavaliar o RAG com os
vetores locais”. A referência indicada documenta três variantes na mesma rodada.
Os brancos abaixo foram contados nas 60 medições preservadas, incluindo o branco
correto de `sem-categoria` em cada variante. [Relatório de transporte](avaliacao-rag-transporte-2026-11-12.md)
· [Medições de 12/11](avaliacoes/rag-transporte-2026-11-12.json)

| Variante histórica de 12/11 | Acertos | Em branco | Mediana | p95 |
| --- | ---: | ---: | ---: | ---: |
| Prompt anterior com rótulos genéricos | 3/20 | 18/20 | 1.824 ms | 32.214 ms |
| Catálogo sem busca no histórico | 18/20 | 1/20 | 2.226 ms | 21.586 ms |
| Catálogo com RAG de embeddings Gemini | 19/20 | 1/20 | 2.158 ms | 9.370 ms |

O controle disponível localmente é o catálogo sem histórico, executado no mesmo
serviço atual que o RAG local. O RAG histórico usava `gemini-embedding-001` e
incluía até três exemplos no prompt antes da geração. A geração das três
variantes históricas usou `gemini-3.1-flash-lite`. Os registros preservam acertos,
brancos inferíveis, quantidade de exemplos e duração, sem vetores do Gemini ou
respostas brutas do modelo. Reexecutar o prompt anterior ou o RAG histórico com
o comportamento original exigiria acesso ao provedor com cota gratuita
confirmada. A comparação entre implementações sob a mesma geração e
cronometragem fica pendente. Os tempos históricos incluem geração remota, rede
e eventuais tentativas internas.
[Plano do piloto](plano-rag-categorizacao.md) · [Configuração da rodada histórica](avaliacao-rag-transporte-2026-11-12.md#configuração-e-verificador)

## Recorte do RAG atual

A busca ocorre depois que o serviço valida um único rascunho, gerado a partir
de uma única mensagem. Ela exige ausência de categoria válida e catálogo do
usuário disponível. O recuperador aplica o filtro de piloto e busca apenas no
histórico confirmado do mesmo usuário e tipo. A categoria só é preenchida se
houver correspondências, todas indicarem a mesma categoria e ela continuar
válida no catálogo. Uma categoria válida já escolhida pelo modelo é mantida
mesmo quando diverge do gabarito. Lotes, conversas com mais de uma mensagem e
pedidos de esclarecimento seguem sem essa busca.
[TransactionSuggestionService.java](../finance-manager-api/src/main/java/com/rodrigs/finance_manager_api/transaction/service/TransactionSuggestionService.java)
· [TransactionRagRetriever.java](../finance-manager-api/src/main/java/com/rodrigs/finance_manager_api/transaction/rag/TransactionRagRetriever.java)

## Medição local

O teste reaproveita os 20 enunciados, os IDs esperados das categorias recriadas
no banco, as duas contas fictícias e as 11 transações de histórico de 12/11.
Cada enunciado usa a data de referência de 11/11/2026. Uma conta tem todo o
histórico e a outra tem apenas catálogo. O PostgreSQL
17.11 com pgvector rodou em contêiner descartável. Os embeddings de histórico
e de consulta foram calculados pelo cliente local de produção
`local-ptbr-hash-v1:768:normalized-v1`, com similaridade mínima de 0,55,
até três correspondências e orçamento de busca de 2 s.
[Casos e histórico](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/transaction/service/TransactionChatRagEvaluationTest.java)

Em cada par o serviço recebeu o mesmo rascunho de um `ChatModel` simulado, com
valor e tipo fixados pelos dados de teste. No cenário **Catálogo de 12/11**,
o teste reconstruiu as escolhas de categoria dos registros salvos. As duas
escolhas erradas foram `ROTINA`, em `trabalho` e `plantao-onibus`, conforme as
linhas `/COMPARACAO` do log original
`finance-manager-api/target/rag-acceptance-transport-2026-11-12.log`. No cenário
**Categoria nula**, o rascunho de entrada de cada caso recebeu categoria
nula para exercitar a busca condicional. O teste verificou um rascunho válido
com valor e tipo corretos em todas as tentativas. O gabarito de categoria
é comparado pelo nome único na conta fictícia, após o serviço validar o ID.
[Executor da medição](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/transaction/service/TransactionChatLocalRagReplay.java)

| Cenário | Variante | Acertos | Em branco | Brancos corretos | Preenchidas erradas | Mediana | p95 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Catálogo de 12/11 | Catálogo | 18/20 | 1/20 | 1/20 | 2/20 | 13,42 ms | 28,22 ms |
| Catálogo de 12/11 | Catálogo + RAG local | 18/20 | 1/20 | 1/20 | 2/20 | 13,84 ms | 33,57 ms |
| Categoria nula | Catálogo | 1/20 | 20/20 | 1/20 | 0/20 | 13,75 ms | 27,72 ms |
| Categoria nula | Catálogo + RAG local | 3/20 | 18/20 | 1/20 | 0/20 | 25,81 ms | 61,60 ms |

Cada variante teve 200 amostras de latência por cenário, com dez repetições
de cada caso após duas rodadas de aquecimento. As duas variantes foram executadas
em sequência para cada caso, com alternância de qual era executada primeiro.
`System.nanoTime()` cercou a chamada ao serviço, incluindo catálogo,
serialização do prompt, leitura da resposta simulada, validação e, quando acionada,
vetorização e SQL. Preparação do rascunho, carga do histórico, aquecimento e
coleta do resultado ficaram fora da janela. Foi medida a latência do serviço
em processo, com o acesso ao banco local. A mediana usa a média das duas
observações centrais e o p95 é a 190ª observação em ordem crescente. As categorias
e os resultados de busca de cada caso foram iguais nas dez repetições.
[Medições por caso e repetição](avaliacoes/rag-local-2026-11-22.json)

Com as escolhas de 12/11, houve 19 casos sem chamada ao recuperador e uma
consulta que detectou histórico vazio, em `sem-categoria`. Com categoria nula,
o RAG recuperou e aplicou a categoria correta em `rotina` e `uber-show`.
Quinze casos ficaram abaixo do limiar de similaridade e três tinham histórico
vazio. A busca registrou zero falhas. O ganho de dois casos mede o preenchimento
de brancos nesse cenário induzido. Seu tamanho na geração atual depende de
quais categorias o modelo deixa em branco.

## Reprodução

Execute a partir de `finance-manager-api`, com Maven e dependências em cache,
Docker ativo e as imagens locais `pgvector/pgvector:pg17` e
`testcontainers/ryuk:0.12.0`. A rodada usou Windows 11, Java 25.0.2 e Maven
3.9.16. Os comandos foram executados sem carregar o perfil do PowerShell.
O modo de reprodução desliga a geração remota e usa o PostgreSQL descartável.

```powershell
mvn -o -B '-Dtest=TransactionChatRagEvaluationTest,TransactionChatRagAcceptanceTest,TransactionChatRagSavedReportTest,TransactionSuggestionServiceTest,TransactionChatRagRequestSpecTest' '-Dtransaction.chat.rag.local-replay=true' '-Dtransaction.chat.rag.report=../docs/avaliacoes/rag-transporte-2026-11-12.json' test
```

`BUILD SUCCESS`. Dos 88 testes reportados, 85 passaram e 3 opcionais foram
ignorados, sem erros ou falhas. O verificador reavaliou as 60 linhas históricas
salvas, e os três testes HTTP do piloto passaram. As medições da tabela local
vieram desta execução.

```powershell
mvn -o -B '-Dtest=TransactionChatRagEvaluationTest' '-Dtransaction.chat.rag.local-replay=true' test
```

`BUILD SUCCESS`. Dos quatro testes reportados, a reprodução passou e três
opcionais foram ignorados, sem erros ou falhas. As categorias e os resultados
de busca coincidiram em todas as repetições das duas rodadas. Na segunda,
o p95 do catálogo e do RAG local foi de 54,82 e 63,16 ms no cenário de 12/11
e de 17,93 e 38,37 ms com categoria nula.

O comando gera `target/rag-local-replay.json`. Em cada cenário, `expected`
traz o gabarito e `outcomes` contém uma categoria e um resultado de busca por
repetição. `serviceMicros` contém as durações em microssegundos, na mesma
ordem. Para consolidar, conte por caso as categorias que igualam `expected`
(incluindo `null`), os resultados com `category: null` e, para a latência,
agrupe as 200 durações de cada cenário e variante.
