# Diagnóstico local do Gemini

Esta simulação verifica o cliente Java e o serviço reais contra respostas
sintéticas, com chave fictícia. Não usa dados nem credenciais reais e não é uma
réplica completa da API Gemini. O mock foi construído localmente a partir das
interfaces e do serializador do SDK usado no projeto. A consulta a um mock do
proprietário não foi feita porque o CLI estava sem sessão autenticada.

Na raiz do repositório, com Postman CLI 1.76.0:

```powershell
postman simulation run postman/simulations/gemini-diagnostics.sim.yaml --port 4517 --no-history --output ndjson
```

Em outro terminal, a partir de `finance-manager-api`:

```powershell
mvn '-Dtest=TransactionGeminiSimulationTest' '-Dtransaction.suggestions.simulation.url=http://localhost:4517/gemini' test
```

A URL local substitui o host do SDK apenas nesse teste. Foi escolhido o modo de
uma porta porque o SDK usa seu próprio transporte Java/OkHttp; a interceptação
por variáveis HTTP_PROXY não foi estabelecida. Nenhuma URL de produção é alterada.

Os nomes de modelo fictícios selecionam seis cenários: `recover-*` responde 503
uma vez e depois 200; `unavailable-*` sempre responde 503; `rate-limited-*` responde
429; `invalid-*` responde 400; `slow-*` demora 1,5 s; `disconnect-*` fecha a primeira
conexão e responde normalmente na segunda chamada. O teste de lentidão reduz o
timeout HTTP para 200 ms; os demais usam 5 s para manter a verificação local curta.
Todos usam a política de geração real: uma tentativa no SDK e no máximo uma nova
tentativa pelo `RetryTemplate` do modelo, incluindo falhas de transporte e timeout.

O teste confere o resultado do serviço e as contagens no endpoint de controle
`/gemini/__requests/<modelo>`. Os eventos `request` no log do Postman confirmam o
caminho HTTP exercitado. Rotas fora desse recorte respondem 501. Interrompa a
simulação com Ctrl+C depois dos testes. Sem a propriedade de URL, os seis casos
ficam desabilitados na suíte padrão.

O CLI instalado não reconhece `postman status`; `postman whoami` confirmou a
ausência de sessão. Não foi necessário autenticar, inicializar workspace,
publicar ou implantar recursos para este diagnóstico.

Foi enviado ao Postman um feedback técnico genérico sobre essa divergência da
skill, sem dados do projeto. O CLI confirmou o recebimento.
