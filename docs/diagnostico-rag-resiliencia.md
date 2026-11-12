# Diagnóstico da geração e validação do RAG

**Resultado final:** a [validação local foi aprovada](avaliacao-rag-transporte-2026-11-12.md).
A rodada teve 19/20 categorias corretas no RAG contra 18/20 no catálogo, sem falhas
de geração, uma correção, zero regressões e um fallback válido. O verificador foi
alinhado ao fallback exigido pelo plano e reavaliou as mesmas sessenta medições.
A verificação completa final passou com 416 testes aprovados e 95,70% de cobertura.
As etapas abaixo preservam o histórico do diagnóstico.

## Revisão para falhas de transporte

Após a rodada descrita ao final deste documento, a política passou a usar um
`RetryTemplate` explícito no modelo: no máximo duas tentativas, com intervalo de
0,5 s, para HTTP 500/502/503/504, timeout ou falha de transporte. O SDK passou a
uma tentativa. JSON inválido, HTTP 400/401/403/429 e interrupção não recebem nova
tentativa. Isso fecha a lacuna da política anterior, que só repetia códigos HTTP.

O prompt agora declara que o catálogo não obriga a preencher categoria e pede
`categoriaSugerida null` quando não há correspondência semântica ou evidência
suficiente. Essa alteração geral não inclui a resposta de nenhum caso do benchmark.
O contrato do rascunho e a validação de pertencimento e tipo continuam os mesmos.

A verificação completa desta revisão terminou sem falhas: 415 testes reportados,
410 aprovados e cinco avaliações opcionais ignoradas; 1.821 de 1.906 linhas
cobertas (95,54%). Os seis cenários HTTP locais passaram, incluindo desconexão
recuperada e timeout persistente limitado a duas chamadas.

A nova avaliação dos mesmos vinte casos foi concluída em
`finance-manager-api/target/rag-acceptance-transport-2026-11-12.log`; seu resultado
e a correção do verificador estão detalhados no relatório final citado acima.

## Evidências já verificadas em 12 de novembro de 2026

Uma reavaliação curta do caso fictício `mercado`, com a configuração anterior,
reproduziu uma falha de geração HTTP 503 após recuperar três exemplos válidos.
O novo diagnóstico registra classificação, código HTTP, tipo da causa e duração,
sem copiar mensagens de exceção, prompts, respostas ou credenciais para os logs
operacionais. A resposta de erro da API continua sem detalhes internos.

Na primeira revisão foi introduzida uma configuração explícita para a geração: timeout HTTP de 30 s,
no máximo duas tentativas para HTTP 500, 502, 503 e 504 e intervalo de 0,5 s. O SDK
era a única camada de retry; o Spring não acrescentava tentativas. A recuperação de
embeddings permanece com uma tentativa e seu orçamento total de busca de 2 s.

Durante essa alteração, uma rodada foi interrompida após rejeições HTTP 400. Um
diagnóstico com entrada pública fixa comprovou que o bean recebia o placeholder
literal da propriedade em vez da credencial. A leitura passou a usar diretamente
o `Environment`, com validação de configuração e teste local da resolução do
placeholder. Essa rodada interrompida não é evidência de disponibilidade nem de
qualidade do RAG. O log foi preservado em
`finance-manager-api/target/rag-acceptance-rejected-credential.log`.

O avaliador agora usa o mesmo cliente de geração da aplicação e confere a
correspondência da credencial fornecida ao processo antes de indexar ou chamar o
modelo. O modelo e a chave reais não são substituídos por fixtures nesse caminho.

## Verificação local

- 95 testes direcionados de configuração, diagnóstico, serviço, recuperação e
  HTTP passaram.
- Os cinco cenários do [Postman local](../postman/simulations/README.md) usaram o
  SDK e o serviço reais. As contagens e os eventos HTTP confirmaram recuperação
  de 503 seguido de 200, parada após dois 503, uma chamada para 400 e 429 e
  encerramento de uma resposta lenta. Esses testes verificam o cliente contra
  respostas sintéticas; não estabelecem disponibilidade ou precisão do Gemini.
- A suíte completa isolada reportou 409 testes: 404 passaram, cinco avaliações
  opcionais foram ignoradas, sem falhas ou erros. JaCoCo verificou dados novos:
  1.807 linhas cobertas de 1.900 (95,11%), acima do mínimo de 70%.
- Dois testes adicionais passaram separadamente após a suíte: reserva concorrente
  de uma mesma transação no PostgreSQL e rejeição de rascunho inventado induzido
  por instruções no histórico. A mensagem atual foi preservada como um campo JSON
  independente e o rascunho extra resultou em esclarecimento, sem valor aceito.
- Após esses testes e a rodada ao vivo, o relatório foi atualizado e o limite
  JaCoCo novamente verificado: 1.814 de 1.900 linhas cobertas (95,47%), sem
  divergência entre os dados de execução e as classes analisadas.

Uma execução anterior da suíte, concorrente com outra execução Maven, falhou no
teste de cancelamento da busca. O mesmo teste passou isoladamente e na suíte
completa isolada. O comportamento de produção não foi relaxado para contornar o
teste; a dependência de agendamento sob carga fica registrada como limitação.

## Aceite e resultado da nova rodada

O modo `transaction.chat.rag.acceptance=true` exige os vinte casos: geração
concluída no catálogo e no RAG para comparação pareada, valor e tipo corretos no
RAG inclusive em fallback de busca, mais correções que regressões e p95 de todas as tentativas
RAG até 2 s acima do catálogo. Ele recusa uma seleção parcial de casos. O modo
exploratório continua disponível, mas seu `BUILD SUCCESS` não comprova liberação.

A [nova rodada completa](avaliacao-rag-resiliencia-2026-11-12.md) terminou com
sessenta medições, credencial verificada e gabaritos preservados. Catálogo e RAG
tiveram cada um 18 respostas concluídas, 17 categorias corretas e duas falhas de
geração. Foram quatro pares inconclusivos, zero correções, zero regressões e um
erro de categoria mantido. O p95 de todas as tentativas RAG ficou em 30,516 s,
contra 30,530 s do catálogo. A verificação de latência passou, mas o aceite falhou
por geração incompleta e ausência de ganho de categoria.

As pendências são a confiabilidade do transporte e a categoria indevida quando
nenhuma opção se aplica. A simulação local foi encerrada ao final dos testes.
O piloto permanece desligado no ambiente existente. A
[rodada anterior completa](avaliacao-rag-2026-11-12.md) continua preservada como
evidência histórica e não é substituída pelos testes locais.
