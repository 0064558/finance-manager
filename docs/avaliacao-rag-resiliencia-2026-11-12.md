# Reavaliação do RAG com política de geração compartilhada

Este relatório preserva uma rodada intermediária. O resultado mais recente está
na [validação local com tratamento de transporte](avaliacao-rag-transporte-2026-11-12.md).

## Resultado e decisão

A rodada completa de 12 de novembro de 2026 terminou às 14:21:17, horário de
São Paulo, após 12 min 12 s. Foram executados os mesmos vinte casos fictícios nas
três variantes, com os gabaritos originais. O modo de aceite **falhou**.

Catálogo e RAG tiveram, cada um, 18 respostas concluídas, 17 categorias corretas
e duas falhas de geração. Nos dezesseis pares comparáveis houve quinze acertos
mantidos e um erro mantido: zero correções e zero regressões. Quatro pares ficaram
inconclusivos. O ganho de categoria exigido pelo plano não foi demonstrado nesta
rodada; o piloto continua desligado no ambiente existente.

As pendências são a confiabilidade do transporte na geração e a seleção indevida
de uma categoria quando nenhuma se aplica. A avaliação não deve ser considerada
aprovada somente porque a busca ou os testes locais passaram. A próxima alteração
deve preservar o conjunto e os critérios desta rodada para permitir comparação.

## Configuração e reprodução

O avaliador usou o mesmo bean de cliente da aplicação, verificando previamente a
correspondência da credencial fornecida ao processo. Modelo de chat
`gemini-3.1-flash-lite`, timeout HTTP de 30 s por tentativa e até duas tentativas
no SDK para HTTP 500, 502, 503 e 504, com intervalo de 0,5 s. Não há outra camada
de retries no Spring. Erros de transporte sem resposta HTTP não receberam uma
segunda chamada nos cenários locais verificados.

A busca manteve `gemini-embedding-001`, 768 dimensões, perfil `normalized-v1`,
similaridade mínima 0,55, até três exemplos e orçamento total de 2 s. PostgreSQL
descartável com pgvector e onze transações fictícias, sem indexação de dados reais.
Não houve outra execução Maven concorrente durante esta rodada.

```powershell
# A partir de finance-manager-api, com Docker e GEMINI_API_KEY no processo:
mvn '-Dtest=TransactionChatRagEvaluationTest' '-Dtransaction.chat.rag.evaluate=true' '-Dtransaction.chat.rag.acceptance=true' test
```

Log: `finance-manager-api/target/rag-acceptance-retry-2026-11-12.log`.
Os sessenta registros foram extraídos para o JSON homônimo em `target`, após
verificar sessenta medições, vinte casos e três variantes por caso. Os arquivos
gerados não são versionados. O [diagnóstico](diagnostico-rag-resiliencia.md)
documenta a reprodução do HTTP 503, os testes HTTP locais e a correção da leitura
da configuração. A rodada interrompida durante essa correção ficou separada.

## Medições

| Indicador | Prompt anterior | Catálogo | Catálogo + RAG |
| --- | ---: | ---: | ---: |
| Tentativas | 20 | 20 | 20 |
| Respostas concluídas | 18 | 18 | 18 |
| Categorias corretas nas concluídas | 4/18 | 17/18 | 17/18 |
| Categorias corretas sobre todas as tentativas | 4/20 | 17/20 | 17/20 |
| Categorias erradas preenchidas | 0 | 1 | 1 |
| Valor e tipo corretos nas concluídas | 18/18 | 18/18 | 18/18 |
| Falhas de geração | 2 | 2 | 2 |
| Falhas de busca | 0 | 0 | 0 |
| Mediana das concluídas | 1,797 s | 2,436 s | 1,826 s |
| p95 das concluídas | 20,791 s | 27,626 s | 24,861 s |
| p95 de todas as tentativas, incluindo falhas | 19,386 s | 30,530 s | 30,516 s |

O p95 usa o posto mais próximo. O conjunto com falhas pode ter p95 menor que o
das respostas concluídas: os tamanhos e postos são diferentes. A latência inclui
as tentativas internas e a busca no RAG, sem as pausas de quatro segundos entre
medições. Valores estão arredondados a milissegundos; diferenças pequenas não
indicam vantagem de desempenho. O p95 RAG de todas as tentativas ficou cerca de
14 ms abaixo do catálogo, atendendo ao limite relativo de +2 s nesta amostra.

Dezessete buscas RAG recuperaram três exemplos e três seguiram sem histórico:
85% com exemplos, 15% de fallback esperado e nenhuma falha de busca. Uma dessas
buscas com exemplos foi seguida por falha de geração; não confundir recuperação
bem-sucedida com resposta concluída.

## Comparação pareada

| Caso | Catálogo | RAG | Exemplos | Resultado |
| --- | --- | --- | ---: | --- |
| restaurante | RESTAURANTES | RESTAURANTES | 3 | Manteve acerto |
| mercado | MERCADO | MERCADO | 3 | Manteve acerto |
| trabalho | Falha de geração | PLANTAO | 3 | Inconclusivo |
| rotina | ROTINA | ROTINA | 3 | Manteve acerto |
| receita | RECEITAS_PONTUAIS | RECEITAS_PONTUAIS | 3 | Manteve acerto |
| sem-historico | TRANSPORTE | Falha de geração | 0 | Inconclusivo |
| pao-e-leite | MERCADO | MERCADO | 3 | Manteve acerto |
| pizzaria | RESTAURANTES | RESTAURANTES | 3 | Manteve acerto |
| plantao-onibus | PLANTAO | PLANTAO | 3 | Manteve acerto |
| rotina-aplicativo | ROTINA | ROTINA | 3 | Manteve acerto |
| uber-show | LAZER | LAZER | 3 | Manteve acerto |
| ingresso-show | LAZER | Falha de geração | 3 | Inconclusivo |
| reparo-casa | CASA | CASA | 3 | Manteve acerto |
| material-casa | CASA | CASA | 3 | Manteve acerto |
| reembolso-plantao | ACERTOS | ACERTOS | 3 | Manteve acerto |
| freela-foto | RECEITAS_PONTUAIS | RECEITAS_PONTUAIS | 3 | Manteve acerto |
| salario | SALARIO | SALARIO | 3 | Manteve acerto |
| sem-historico-restaurante | Falha de geração | RESTAURANTES | 0 | Inconclusivo |
| sem-categoria | RESTAURANTES | RESTAURANTES | 0 | Manteve erro |
| curso-sem-exemplo | ESTUDOS | ESTUDOS | 3 | Manteve acerto |

No caso `sem-categoria`, a mensagem descreve uma consulta com a dentista, e a conta
fictícia possui somente categorias de restaurantes e transporte. Ambas as variantes
preencheram Restaurantes indevidamente. Não houve exemplos nesse caso, portanto o
erro não demonstra influência de histórico recuperado. A validação atual verifica
pertencimento e tipo da categoria, mas não garante sua adequação semântica.

## Falhas e critérios de aceite

| Variante | Caso | Falha observada | Duração total aproximada |
| --- | --- | --- | ---: |
| Catálogo | trabalho | Transporte, sem código HTTP identificado | 30,530 s |
| Catálogo | sem-historico-restaurante | Transporte, sem código HTTP identificado | 30,542 s |
| RAG | sem-historico | Transporte, sem código HTTP identificado | 30,585 s |
| RAG | ingresso-show | Transporte, sem código HTTP identificado | 30,516 s |
| Prompt anterior | receita | HTTP 503 após o limite de tentativas | 17,233 s |
| Prompt anterior | rotina-aplicativo | HTTP 503 após o limite de tentativas | 19,386 s |

As falhas de transporte foram classificadas pela causa `IOException`. Sua duração
é próxima ao timeout configurado, mas o log operacional não estabelece a causa
raiz de cada uma; não atribuir todas a um evento remoto específico.

O teste de aceite falhou em três verificações: catálogo não concluiu vinte casos;
RAG não concluiu vinte casos com extração validada; zero correções não supera zero
regressões. O limite relativo de latência foi atendido. O erro semântico da dentista
fica registrado separadamente; ele não foi omitido por estar em um par sem exemplos.

Na [rodada anterior](avaliacao-rag-2026-11-12.md), o RAG teve sete falhas de geração
e 13 categorias corretas sobre vinte tentativas. Nesta rodada houve duas falhas e
17 categorias corretas. A comparação é descritiva: a disponibilidade do provedor
e as respostas do modelo variam, e estas execuções não isolam causalmente o efeito
da política de tentativas. Não foram alterados os gabaritos para melhorar o resultado.

Os testes locais e a cobertura passaram, conforme o diagnóstico. Isso comprova os
cenários exercitados no código, sem substituir a validação de disponibilidade,
qualidade e benefício antes da liberação do piloto.
