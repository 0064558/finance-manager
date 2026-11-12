# Avaliação do RAG em 12 de novembro de 2026

Este é o resultado da primeira rodada completa. Consulte também a
[reavaliação posterior com a política compartilhada](avaliacao-rag-resiliencia-2026-11-12.md).

## Decisão

Manter o RAG desligado por padrão. A rodada completa encontrou uma correção de
categoria, nenhuma regressão nos dez pares comparáveis e dez pares inconclusivos.
O RAG concluiu 13 das 20 tentativas; as sete falhas de geração impedem considerar
a validação encerrada ou recomendar a ativação do piloto neste momento.

O próximo passo é diagnosticar as falhas de geração com códigos de erro e tempos,
sem registrar prompts ou credenciais, e repetir a mesma avaliação completa após
tratar a causa. A rodada teve falhas nas três variantes; ela não permite atribuir
a causa dessas falhas à recuperação de histórico.

## Execução e método

- `TransactionChatRagEvaluationTest`, com `transaction.chat.rag.evaluate=true` e
  sem seleção de caso: 20 casos, três variantes e 60 tentativas sequenciais.
- PostgreSQL 17 descartável com pgvector via Testcontainers; duas contas, categorias
  e 11 transações de histórico fictícias. Nenhum histórico real foi indexado.
- Chat `gemini-3.1-flash-lite`; embedding `gemini-embedding-001`, 768 dimensões,
  perfil `normalized-v1`, similaridade mínima 0,55 e até três exemplos.
- Busca com orçamento de 2 s, geração com limite de 60 s e pausa de 4 s entre
  tentativas. A ordem das variantes é alternada entre os casos.
- Rodada encerrada às 11:55:40, horário de São Paulo; Maven levou 17 min 55 s.

O comando e a preparação do ambiente estão em
[Como reproduzir](avaliacao-sugestoes-transacoes.md#experimento-local-de-categorização-no-chat).
O log local está em `finance-manager-api/target/rag-evaluation-2026-11-12.log`;
os 60 registros extraídos estão em `target/rag-evaluation-2026-11-12.json` na API.
Esses arquivos gerados não são versionados. As tabelas abaixo preservam o resultado
consolidado da rodada.

## Resultados

| Indicador | Prompt anterior | Catálogo | Catálogo + RAG |
| --- | ---: | ---: | ---: |
| Tentativas | 20 | 20 | 20 |
| Respostas concluídas | 16 | 15 | 13 |
| Categorias corretas nas concluídas | 3/16 | 14/15 | 13/13 |
| Categorias corretas sobre todas as tentativas | 3/20 | 14/20 | 13/20 |
| Categorias erradas preenchidas | 0 | 1 | 0 |
| Valor e tipo corretos nas concluídas | 16/16 | 15/15 | 13/13 |
| Falhas de geração | 4 | 5 | 7 |
| Falhas de busca | 0 | 0 | 0 |
| Mediana das concluídas | 9,325 s | 7,184 s | 10,283 s |
| p95 das concluídas | 31,854 s | 30,628 s | 28,883 s |
| p95 de todas as tentativas, incluindo falhas | 60,010 s | 30,628 s | 29,133 s |

O p95 usa o posto mais próximo. A latência do RAG inclui embedding da consulta e
SQL. As pausas entre requisições ficam fora da medição. As sete falhas do RAG
foram reportadas como falhas do provedor durante a geração; o log da aplicação
registra a classe da exceção, mas não permite identificar o código HTTP ou a causa
raiz. As falhas não são tratadas como erros de extração nem excluídas da contagem
de tentativas. Não houve valor ou tipo incorreto entre as respostas concluídas.

Nas 20 tentativas RAG, 17 recuperaram três exemplos e três seguiram com catálogo
por ausência de histórico. Isso equivale a 85% de buscas com exemplos e 15% de
fallback esperado; nenhuma busca foi marcada como timeout ou erro. Recuperar
exemplos não implica que a geração tenha terminado com sucesso.

## Comparação por caso

“Falha” indica ausência de resposta válida de geração. O par fica inconclusivo
quando uma das duas variantes falha. “Sem categoria” é o resultado esperado no
caso correspondente. Os nomes abaixo são os rótulos normalizados retornados pelo
serviço, associados aos IDs das categorias fictícias.

| Caso | Catálogo | RAG | Exemplos | Comparação |
| --- | --- | --- | ---: | --- |
| restaurante | RESTAURANTES | RESTAURANTES | 3 | Manteve acerto |
| mercado | MERCADO | Falha | 3 | Inconclusivo |
| trabalho | ROTINA | PLANTAO | 3 | Corrigiu |
| rotina | ROTINA | Falha | 3 | Inconclusivo |
| receita | RECEITAS_PONTUAIS | RECEITAS_PONTUAIS | 3 | Manteve acerto |
| sem-historico | Falha | TRANSPORTE | 0 | Inconclusivo |
| pao-e-leite | MERCADO | Falha | 3 | Inconclusivo |
| pizzaria | RESTAURANTES | RESTAURANTES | 3 | Manteve acerto |
| plantao-onibus | Falha | PLANTAO | 3 | Inconclusivo |
| rotina-aplicativo | ROTINA | ROTINA | 3 | Manteve acerto |
| uber-show | LAZER | LAZER | 3 | Manteve acerto |
| ingresso-show | Falha | Falha | 3 | Inconclusivo |
| reparo-casa | CASA | Falha | 3 | Inconclusivo |
| material-casa | Falha | Falha | 3 | Inconclusivo |
| reembolso-plantao | ACERTOS | Falha | 3 | Inconclusivo |
| freela-foto | RECEITAS_PONTUAIS | RECEITAS_PONTUAIS | 3 | Manteve acerto |
| salario | Falha | SALARIO | 3 | Inconclusivo |
| sem-historico-restaurante | RESTAURANTES | RESTAURANTES | 0 | Manteve acerto |
| sem-categoria | Sem categoria | Sem categoria | 0 | Manteve acerto |
| curso-sem-exemplo | ESTUDOS | ESTUDOS | 3 | Manteve acerto |

Total: uma correção, zero regressões, nove acertos mantidos e dez pares
inconclusivos. Casos com zero exemplos recebem o mesmo contexto de catálogo;
diferenças nesses casos não demonstram contribuição do histórico. Apesar do nome
`curso-sem-exemplo`, a busca desse caso recuperou três exemplos acima do limiar.

## Critérios e pendências

| Critério | Resultado |
| --- | --- |
| Mais correções que regressões de categoria | Atendido nos dez pares comparáveis: 1 contra 0; metade da amostra ficou inconclusiva |
| Preservar valor e tipo na suíte | Corretos nas 13 respostas RAG concluídas; não demonstrado para os sete casos sem resposta |
| p95 do RAG até 2 s acima do catálogo | Atendido nesta rodada: 1,745 s abaixo nas concluídas e 1,495 s abaixo em todas as tentativas |
| Busca tolerante a falhas | Coberta pelos testes automatizados; nenhuma falha de busca na rodada ao vivo |
| Cobertura dos vetores observável | Implementada com gauges agregados, estados e instante da última atualização; validada com PostgreSQL |
| Ativação do piloto | Pendente de diagnóstico da geração e nova validação; nenhum ambiente compartilhado foi alterado |

O teste exploratório terminou com `BUILD SUCCESS`, pois suas asserções verificam
a execução do experimento e a ocorrência de recuperação real, sem exigir todos os
critérios de liberação. Esse resultado do Maven não representa aprovação do piloto.
A comparação de latência é descritiva: a amostra é pequena, houve falhas e as
variantes não concluíram exatamente os mesmos casos.

## Verificação do código

A nova medição acompanha vetores prontos, pendentes e descrições descartadas no
recorte dos usuários piloto. Vetores com hash ou versão desatualizados contam como
pendentes; itens descartados ficam fora do denominador de cobertura. O instante da
última atualização permite identificar amostras antigas em caso de falha no banco.
Veja os nomes e as regras no [README da API](../finance-manager-api/README.md#piloto-de-categorização-com-histórico).

- Testes direcionados à cobertura, busca, indexação e HTTP: 18 passaram.
- Suíte completa da API: 391 testes reportados, 388 passaram, três avaliações
  opcionais ignoradas, nenhuma falha ou erro.
- Cobertura de linhas da suíte completa: 1.770 cobertas de 1.863, ou 95,01%;
  limite JaCoCo de 70% atendido com os dados desta execução.

As verificações usaram bancos descartáveis. A avaliação completa com Gemini foi
executada separadamente da suíte padrão. A flag padrão e a lista de pilotos do
ambiente existente não foram modificadas.
