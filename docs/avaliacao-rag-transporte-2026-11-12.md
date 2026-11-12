# Validação local do RAG — transporte e categoria opcional

## Decisão

Os critérios do plano foram atendidos nesta amostra fictícia. A validação do piloto
local passou; a configuração padrão permanece desligada e nenhum ambiente
compartilhado foi ativado.

Foram preservados os vinte casos e gabaritos anteriores. A rodada com Gemini real
terminou em 12/11/2026 às 14:44:32, horário de São Paulo, após 9 min 40 s. As
[sessenta medições](avaliacoes/rag-transporte-2026-11-12.json) estão preservadas na pasta de documentação;
a cópia foi conferida por SHA-256 contra a extração do log original
`finance-manager-api/target/rag-acceptance-transport-2026-11-12.log`.

## Resultado

| Medida | Prompt anterior | Catálogo | Catálogo + RAG |
| --- | ---: | ---: | ---: |
| Respostas concluídas | 20/20 | 20/20 | 20/20 |
| Categorias corretas | 3/20 | 18/20 | 19/20 |
| Valor e tipo corretos | 20/20 | 20/20 | 20/20 |
| Falhas de geração | 0 | 0 | 0 |
| Falhas de busca | 0 | 0 | 1, com resposta válida por catálogo |
| Mediana | 1,824 s | 2,226 s | 2,158 s |
| p95, incluindo todas as tentativas | 32,214 s | 21,586 s | 9,370 s |

Comparação pareada: uma correção (`trabalho`), zero regressões, dezessete acertos
mantidos, um erro mantido (`plantao-onibus`) e um par inconclusivo (`salario`).
O caso da dentista (`sem-categoria`) retornou corretamente sem categoria nas duas
variantes. O erro compartilhado de `plantao-onibus` continua exigindo revisão
humana; o resultado não representa precisão perfeita.

Dezesseis buscas recuperaram três exemplos. Três casos não tinham histórico e um
teve busca indisponível: 80% com exemplos e 20% com catálogo apenas. A falha de
busca em `salario` preservou categoria, valor e tipo corretos; esse par não conta
como correção nem regressão. O log não distingue a causa específica dessa busca.

O p95 usa o posto mais próximo. A latência inclui geração, tentativas internas,
embedding e SQL, excluindo as pausas entre medições. A amostra é pequena e a
disponibilidade do provedor varia; estes números não isolam causalmente o efeito
das alterações nem substituem as medições de uso do piloto.

## Configuração e verificador

Chat `gemini-3.1-flash-lite`; embedding `gemini-embedding-001`, 768 dimensões,
normalização `normalized-v1`, similaridade mínima 0,55 e até três exemplos. A
busca mantém orçamento total de 2 s e uma tentativa. A geração usa uma tentativa
no SDK e um `RetryTemplate` com no máximo uma repetição, intervalo de 0,5 s e timeout
HTTP de 30 s por tentativa. São repetidos HTTP 500/502/503/504, transporte e timeout;
400/401/403/429, JSON inválido e interrupção não recebem repetição. O prompt permite
categoria nula quando nenhuma opção representa o lançamento, sem exemplos contendo
o gabarito do benchmark.

O Maven da rodada original registrou `BUILD FAILURE`: o verificador acrescentado
anteriormente exigia zero falhas de busca, apesar de o plano exigir fallback válido
nessa situação. Essa exigência indevida foi corrigida. Continuam exigidos vinte
respostas concluídas em catálogo e RAG, valor e tipo corretos no RAG, mais correções
que regressões e p95 RAG até 2 s acima do catálogo. O fallback precisa preservar
uma resposta válida e continua identificado como inconclusivo na comparação.

As mesmas sessenta medições foram reavaliadas sem novas chamadas ao provedor e
passaram. Quatro testes do verificador confirmam que fallback válido é aceito e
que geração inválida, extração alterada, ausência de ganho ou excesso de latência
continuam rejeitados. O log de falha original permanece preservado.

Para reproduzir a avaliação das medições salvas, a partir de `finance-manager-api`:

```powershell
mvn '-Dtest=TransactionChatRagAcceptanceTest,TransactionChatRagSavedReportTest' '-Dtransaction.chat.rag.report=../docs/avaliacoes/rag-transporte-2026-11-12.json' test
```

## Piloto e verificações

O teste HTTP do piloto usa PostgreSQL descartável, flag RAG ligada e um UUID
fictício na lista do backend. Executou o runnable registrado no agendador para
indexar uma transação manual, recuperou o exemplo pelo chat autenticado, confirmou
o novo rascunho e indexou a transação confirmada. Um usuário fora da lista ficou
sem vetores. O provedor desse teste HTTP é simulado; a avaliação acima usa Gemini
real pelo serviço de produção. Não foi aguardado o relógio do agendador.

A verificação final reportou 421 testes: 416 aprovados, cinco avaliações opcionais
ignoradas, nenhuma falha ou erro. Inclui seis cenários HTTP do SDK e serviço reais
contra o Postman local, o piloto HTTP e a reavaliação das medições salvas. JaCoCo:
1.824 de 1.906 linhas cobertas (95,70%), acima do limite de 70%. A simulação local
foi encerrada. Log: `finance-manager-api/target/rag-final-verification.log`.

Cobertura de vetores, tempo e resultado da busca, latência do chat e retenção de
categorias na confirmação estão instrumentados conforme o
[README da API](../finance-manager-api/README.md#piloto-de-categorização-com-histórico).
Histórico real exige a configuração do provedor e o aviso de privacidade previstos
no plano. A flag padrão permanece `false`; a decisão de ligar por padrão depende
das medições do piloto, como originalmente definido.
