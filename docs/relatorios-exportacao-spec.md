# Exportação de relatórios — mini especificação

**Status:** MVP implementado na API e na interface, com validação registrada abaixo.

**Data:** 30/10/2026. **Primeira entrega:** relatório de movimentações em PDF, Excel (.xlsx) e CSV.

## 1. Objetivo e base existente

Permitir que o usuário baixe suas movimentações e um resumo do período para
conferência, organização e compartilhamento fora do Finance Manager (DinDim).

Antes desta funcionalidade, o projeto já possuía autenticação JWT, filtros por
período/tipo/conta/categoria, listagem paginada e relatórios JSON de resumo,
categorias, fluxo de caixa e saldos. A API exporta CSV, XLSX e PDF, e a tela de
Transações oferece o diálogo de exportação nos três formatos.

Referências: [ReportController](../finance-manager-api/src/main/java/com/rodrigs/finance_manager_api/report/controller/ReportController.java),
[ReportService](../finance-manager-api/src/main/java/com/rodrigs/finance_manager_api/report/service/ReportService.java),
[TransactionSpecifications](../finance-manager-api/src/main/java/com/rodrigs/finance_manager_api/transaction/repository/specification/TransactionSpecifications.java)
e [tela de transações](../finance-manager-web/src/app/pages/transactions/transactions.html).

## 2. Escopo proposto do MVP

- Entrada pela tela **Transações**, com ação **Exportar relatório**.
- Um único relatório, com o mesmo conjunto de movimentações nos três formatos.
- PDF para leitura/impressão; XLSX para análise e edição em planilhas;
  CSV para importação e intercâmbio de dados.
- Filtros de data inicial/final, tipo, conta e categoria.
- Exportação de **todos os resultados filtrados**, independentemente da página aberta.
- Geração síncrona na API e download direto, sem guardar arquivos no servidor.

Ficam para depois: gráficos, exportação pelo dashboard, relatórios
agendados, envio por e-mail, histórico de arquivos e processamento em fila.

## 3. Fluxo de uso

1. Usuário abre **Exportar relatório** e vê um diálogo com filtros e formato.
2. O diálogo copia os filtros **aplicados** à listagem, ignorando alterações
   ainda não aplicadas. Se ambas as datas estiverem vazias, sugere o mês atual;
   se apenas uma estiver preenchida, solicita a outra. Ambas são obrigatórias.
3. O usuário pode ajustar os filtros apenas para a exportação, sem mudar a listagem.
4. A ação **Baixar PDF**, **Baixar Excel** ou **Baixar CSV** mostra estado de geração e impede
   novos envios enquanto a requisição estiver em andamento.
5. Em sucesso, o navegador inicia o download. Em erro, o diálogo mantém os
   filtros e permite nova tentativa. Não anunciar que o arquivo foi salvo,
   pois o navegador controla essa etapa.

O diálogo deve funcionar em celular e por teclado, com foco inicial,
restauração de foco ao fechar e mensagens de status acessíveis. Reutilizar o
tratamento existente para API lenta e sessão expirada.

**Privacidade proposta:** a preferência de ocultar valores continua sendo
visual. O arquivo contém valores completos; enquanto essa preferência estiver
ativa, informar isso no próprio diálogo antes do download.

## 4. Conteúdo e regras

| Parte | Conteúdo |
| --- | --- |
| Identificação | DinDim / Finance Manager, título, período, filtros por nome, data/hora de geração com fuso |
| Resumo | Quantidade de transações, receitas, despesas e saldo líquido do recorte |
| Movimentações | Data, descrição, tipo (Receita/Despesa), conta, categoria e valor |

- Usar `occurredOn` como data da movimentação; intervalo inclusivo.
- Resumo e detalhes usam exatamente os mesmos filtros. Os endpoints atuais
  de resumo só recebem datas: não reutilizar esses totais sem adaptar os filtros.
- Valores das movimentações são positivos; o tipo indica receita ou despesa.
  Saldo líquido = receitas − despesas. Calcular com `BigDecimal` na API.
- O saldo líquido não inclui saldos iniciais. Saldos atuais das contas ficam
  fora deste relatório para não confundir o recorte com o patrimônio acumulado.
- Ordenar por `occurredOn ASC, id ASC`, inclusive em datas iguais.
- Sem resultados: PDF e XLSX apresentam totais zerados, cabeçalhos e indicação
  de ausência de movimentações; CSV contém apenas o cabeçalho. Descrição ausente fica vazia.
- Conta e categoria usam os nomes atuais no momento da consulta; não há
  reconstrução histórica de nomes ou de transações alteradas/excluídas.
- Garantir uma leitura consistente para que mudanças durante a geração não
  façam os totais divergirem das linhas exportadas.

**PDF:** A4 paisagem, valores em BRL e datas em `dd/MM/yyyy`, cabeçalhos de tabela
repetidos, páginas numeradas, texto com acentos e descrições longas sem cortes.
Identificar filtros por nome e geração em UTC; formatar valores diretamente
de `BigDecimal`, preservando os centavos inclusive em montantes grandes.
Textos do usuário são literais, sem interpretar HTML, links ou fórmulas.
**XLSX:** abas `Resumo` e `Transações`, cabeçalho congelado e autofiltro na lista;
datas e valores como células tipadas, com formato de data e duas casas decimais.
Campos de texto devem ser gravados como texto, inclusive quando começam com
`=`, `+`, `-` ou `@`; conteúdo do usuário nunca vira fórmula.
O resumo identifica os filtros por nome mesmo quando o recorte estiver vazio,
e registra a data/hora de geração em UTC. Totais são calculados na API e
gravados como valores, sem fórmulas na planilha.

**Precisão XLSX:** valores e totais devem preservar os centavos após a
conversão para número com 15 algarismos significativos do Excel. Se isso não
for possível, retornar `400` com `XLSX_PRECISION_EXCEEDED`, sem arquivo parcial,
orientando reduzir o recorte ou usar CSV. Os cálculos na API continuam em
`BigDecimal`; o CSV preserva os valores decimais completos.

**CSV:** tabela única de movimentações, sem abas, resumo, metadados extras ou
linhas de totais, para manter uma estrutura uniforme de importação. Os totais
calculados a partir dessas linhas devem coincidir com o resumo do PDF/XLSX.
Convenção proposta para uso em português do Brasil:

- UTF-8 com BOM, separador `;` e quebras de linha CRLF.
- Cabeçalho: `Data;Descrição;Tipo;Conta;Categoria;Valor`.
- Datas em `yyyy-MM-dd`; valores com vírgula decimal, duas casas, sem separador
  de milhar e sem símbolo de moeda (exemplo: `1250,50`, em BRL).
- Colocar campos com `;`, aspas ou quebras de linha entre aspas duplas;
  duplicar aspas internas e preservar o conteúdo ao ler o arquivo novamente.
- Neutralizar fórmulas nos campos de texto prefixando apóstrofo quando o
  conteúdo começar com `=`, `+`, `-`, `@`, tabulação ou quebra de linha,
  incluindo esses prefixos após espaços iniciais. Colocar entre aspas não basta.
  Essa proteção pode acrescentar um apóstrofo ao texto importado.

## 5. Contrato HTTP proposto

Novo endpoint autenticado: `GET /api/v1/reports/export`.
Será documentado nas anotações OpenAPI do backend durante a implementação,
seguindo a fonte de documentação já utilizada pelo projeto.

| Parâmetro | Obrigatório | Regra |
| --- | --- | --- |
| `format` | Sim | `PDF`, `XLSX` ou `CSV` |
| `startDate`, `endDate` | Sim | ISO `yyyy-MM-dd`; início ≤ fim |
| `type` | Não | `INCOME` ou `EXPENSE`; ausência inclui ambos |
| `accountId`, `categoryId` | Não | UUID pertencente ao usuário autenticado |

Exemplo: `GET /api/v1/reports/export?format=PDF&startDate=2026-10-01&endDate=2026-10-31`.
Enviar `Authorization: Bearer <token>`; nenhum `userId` é aceito para escolher dados.
Não há parâmetros de paginação ou ordenação nesta operação.

Sucesso `200`: corpo binário, `Cache-Control: no-store` e
`Content-Disposition: attachment; filename="finance-manager_2026-10-01_2026-10-31.pdf"`
(extensão `.xlsx` no Excel e `.csv` no CSV). Tipos:

- PDF: `application/pdf`.
- XLSX: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- CSV: `text/csv; charset=UTF-8`.

Erros seguem `application/problem+json` e o padrão `ProblemDetail` existente:

| Status | Situação | Ação na interface |
| --- | --- | --- |
| `400` | Parâmetro ausente/inválido, intervalo invertido, limite excedido ou perda de precisão XLSX | Mostrar motivo e orientar ajuste dos filtros ou uso de CSV |
| `401` | Token ausente, inválido ou expirado | Seguir fluxo existente de sessão expirada |
| `404` | Conta/categoria inexistente ou de outro usuário | Pedir revisão do filtro sem revelar dados de terceiros |
| `500` | Falha inesperada na geração | Informar falha e permitir nova tentativa |

Exemplo funcional de erro: período invertido → `400`, sem arquivo, com detalhe
indicando que a data inicial deve ser anterior ou igual à final.
Novos códigos de erro, se necessários, seguem a convenção atual da API.

O frontend deve usar o cliente HTTP autenticado, ler a resposta como `Blob`,
interpretar também erros JSON recebidos como `Blob` e liberar a URL temporária
após iniciar o download. Expor `Content-Disposition` no CORS para permitir
leitura do nome do arquivo quando API e frontend estiverem em origens diferentes.

## 6. Implementação e limites propostos

- Manter a funcionalidade no módulo `report`: consulta/validação comum,
  modelo de dados da exportação e renderizadores separados para PDF, XLSX e CSV.
- Reutilizar os filtros e a validação de propriedade de conta/categoria.
  Buscar os nomes associados sem uma consulta adicional por linha.
- Calcular resumo a partir do mesmo conjunto de linhas usado no arquivo.
  Não montar o relatório percorrendo páginas HTTP pelo frontend.
- Carregar dados dentro da transação de leitura e renderizar após encerrá-la,
  evitando manter a conexão com o banco ocupada durante a criação do arquivo.
- Proposta inicial: até **366 dias inclusivos** e **5.000 transações** por
  arquivo, nos três formatos. Consultar no máximo limite + 1 e rejeitar excesso
  antes de renderizar; nunca truncar o relatório silenciosamente.
- Validar datas e propriedade dos filtros na API antes de gerar; não registrar
  conteúdo financeiro ou tokens em logs e não persistir os arquivos.
- Nenhuma alteração de schema prevista para esta primeira entrega.

Os limites ainda precisam ser medidos no ambiente de hospedagem. Antes de
adicionar dependências, escolher bibliotecas PDF/XLSX verificando licença,
compatibilidade com Java 21/Spring Boot 4, consumo de memória e suporte a fontes.
A meta de tempo de geração ainda não está definida.
Para PDF, usar OpenPDF 3.0.5 (Java 21+, licença dual LGPL/MPL), com fontes
Liberation incorporadas de `openpdf-fonts-extra`, distribuídas com a licença
própria no pacote. Usar tabelas com repetição de cabeçalho e divisão de linhas
longas entre páginas. Geração e numeração em memória, sem arquivos persistidos.
Referências: [biblioteca, requisitos e licença](https://github.com/LibrePDF/OpenPDF)
e [fontes Liberation](https://github.com/LibrePDF/OpenPDF/tree/master/openpdf-fonts-extra).
Para XLSX, usar Apache POI 6.0.0 (`poi-ooxml`), licença Apache 2.0 e Java 17+,
compatível com o Java 21 do projeto. Usar `XSSFWorkbook` em memória para o
limite atual de 5.000 linhas, sem arquivos temporários e com estilos reutilizados;
o consumo no ambiente publicado ainda precisa ser medido.
Referências: [versão e requisito Java](https://poi.apache.org/),
[guia XSSF](https://poi.apache.org/components/spreadsheet/quick-guide.html) e
[licença](https://poi.apache.org/legal.html).
O CSV segue a decisão sem dependência adicional registrada na seção 8.

## 7. Critérios de aceite e validação

- [x] PDF, XLSX e CSV abrem corretamente e apresentam as mesmas movimentações;
  totais calculados do CSV coincidem com os resumos do PDF e XLSX.
- [x] Exportar uma lista com várias páginas inclui todos os resultados do recorte.
- [x] Datas de borda, filtros isolados/combinados e recorte vazio são respeitados.
- [x] Com receita de R$ 1.000,00 e despesa de R$ 250,50, saldo líquido é
  R$ 749,50; ao filtrar despesas, receita é zero e saldo líquido é −R$ 250,50.
- [x] Requisições sem autenticação e filtros de outro usuário não expõem dados.
- [x] Exatamente 366 dias e 5.000 linhas são aceitos; exceder qualquer limite
  retorna erro e não baixa arquivo parcial.
- [x] XLSX preserva células numéricas/datas e textos parecidos com fórmulas.
- [x] CSV preserva acentos, delimitadores, aspas e descrições com várias linhas,
  neutraliza fórmulas e, sem resultados, contém apenas o cabeçalho.
- [x] PDF com várias páginas, acentos e descrições longas passa por revisão visual.
- [x] Interface cobre geração, sucesso, falha JSON em resposta `Blob`, sessão
  expirada, teclado, celular e aviso de valores completos.

Testes previstos: regras de consulta/cálculo e limites; integração HTTP com JWT
e PostgreSQL via Testcontainers; leitura dos arquivos gerados para conferir
conteúdo/tipos; testes Angular dos estados e download; conferência visual do PDF.

## 8. Decisões da revisão e tarefas de implementação

Na revisão de 30/10/2026, foram mantidos como base de implementação: entrada
por Transações, PDF/XLSX/CSV, filtros comuns, valores completos no arquivo e
limites de 366 dias/5.000 transações. Os limites seguem sujeitos a medição na
hospedagem. Para permitir validação incremental, a primeira etapa é consulta
comum + CSV pela API; o restante do MVP vem nas tarefas seguintes.

O CSV usa um renderizador pequeno sem dependência adicional, com tratamento
explícito de escape e fórmulas e testes de conteúdo. PDF usa OpenPDF e XLSX usa
Apache POI. Os dados exportados vêm de uma única consulta com joins
de conta/categoria, limite no banco e cópia para um modelo imutável antes da
renderização. Os totais usam essa mesma cópia.

- [x] Revisar escopo e registrar decisões e sequência incremental.
- [x] Entregar consulta comum: filtros, propriedade, ordenação, limites e totais.
- [x] Entregar API CSV: arquivo, erros padronizados, download e CORS.
- [x] Validar CSV com testes unitários e HTTP real em PostgreSQL descartável.
- [x] Implementar XLSX com células tipadas e validar conteúdo.
- [x] Implementar PDF e validar conteúdo e paginação visualmente.
- [x] Implementar diálogo Angular, download e estados de erro/privacidade.
- [x] Executar verificações do MVP e conferir os critérios de aceite.

**Evidência da etapa CSV (30/10/2026):** `mvnw.cmd -B verify` passou com 246 testes,
sem falhas ou testes ignorados, e cobertura mínima exigida atendida. Os novos
cenários estão em
[CsvReportRendererTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/report/service/CsvReportRendererTest.java),
[ReportExportRequestSpecTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/requestspec/ReportExportRequestSpecTest.java)
e [CorsIntegrationTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/config/CorsIntegrationTest.java).
Cobrem conteúdo/escape, fórmulas, filtros, isolamento, datas inclusivas, ordem,
totais, recorte vazio, limites de 366 dias/5.000 linhas, cabeçalhos e erros sem
arquivo parcial. Naquela etapa, PDF, XLSX e interface ainda estavam pendentes.

**Evidência da etapa XLSX (30/10/2026):** `mvnw.cmd -B verify` passou com 264
testes, sem falhas ou testes ignorados, e cobertura mínima exigida atendida.
[XlsxReportRendererTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/report/service/XlsxReportRendererTest.java)
reabre os bytes gerados para conferir abas, células tipadas, totais, textos,
data de geração, congelamento, autofiltro, recorte vazio, precisão e reutilização
de estilos com 5.000 linhas. Os request specs também baixam o XLSX por HTTP real
e conferem filtros por nome, isolamento, limites, cabeçalhos e erros JSON sem
arquivo parcial. O CSV continua coberto e disponível para valores que o Excel
não consegue representar sem perda de centavos. A validação do XLSX foi feita
por leitura automatizada com Apache POI; não houve revisão visual no Excel.

**Evidência da etapa PDF (30/10/2026):** `mvnw.cmd -B verify` passou com 274
testes, sem falhas ou testes ignorados, e cobertura mínima exigida atendida.
[PdfReportRendererTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/report/service/PdfReportRendererTest.java)
confere orientação A4 paisagem, acentos, valores e totais exatos, filtros,
recorte vazio, textos literais, divisão de descrições longas, repetição dos
cabeçalhos, numeração de todas as páginas e presença das 5.000 linhas.
Os request specs conferem download, filtros, isolamento, autenticação, limites
e erro sem arquivo parcial nos três formatos; montantes grandes continuam
disponíveis em CSV e PDF quando o XLSX não consegue preservar os centavos.

Foram renderizadas com Poppler e inspecionadas três amostras fictícias: recorte
vazio (1 página), 100 movimentações (8 páginas) e descrição com muitas quebras
de linha e palavra longa (3 páginas). A revisão confirmou acentos, margens,
cabeçalhos, rodapés e texto sem cortes ou sobreposições. A incorporação das
fontes também foi verificada. As amostras ficam apenas em `target/pdf-qa` quando
os testes são executados com `-Dpdf.preview=true`; não contêm dados reais.

**Evidência da interface (30/10/2026):** 109 testes passaram em 24 arquivos do
frontend e `npm run build` foi aprovado. O build mantém avisos de tamanho do
bundle inicial e de estilos de algumas telas, sem exceder os limites de erro.
No ambiente Windows desta execução, o runner usou uma configuração temporária
com `maxWorkers: 2` e TEMP/TMP dentro de `.angular/test-temp`; nenhuma dependência
nova ou configuração permanente de teste foi adicionada ao frontend.

[ReportExportApi](../finance-manager-web/src/app/core/report-export.spec.ts) cobre
autenticação, parâmetros sem paginação, resposta binária, erros JSON em Blob,
nome do arquivo, início do download e liberação da URL temporária.
[ReportExportDialog](../finance-manager-web/src/app/pages/transactions/report-export-dialog/report-export-dialog.spec.ts)
cobre período padrão, datas incompletas/invertidas, limite de 366 dias, tipos e
categorias, aviso de privacidade, envio duplicado, demora, erros, sessão expirada,
foco e cancelamento da assinatura ao sair da tela. O
[teste de Transações](../finance-manager-web/src/app/pages/transactions/transactions.spec.ts)
confirma a cópia dos filtros aplicados, ignorando alterações ainda não aplicadas
e sem alterar a listagem durante os ajustes da exportação.

Uma prévia local com dados fictícios e serviços substituídos foi inspecionada
no navegador em desktop e celular de 390 px, nos temas claro e escuro. Foram
conferidos foco inicial, correção de datas, troca de formato, geração, falha,
preservação dos filtros, Escape, restauração do foco e rolagem interna sem
rolagem horizontal ou movimentação do fundo. Os testes HTTP da API são os
registrados na etapa PDF (274 testes); o fluxo completo navegador + API real
não foi repetido nesta etapa, que alterou apenas frontend e documentação.

**Disponibilidade:** `format=CSV`, `format=XLSX` e `format=PDF` estão disponíveis
na API. Na tela de Transações, usar **Exportar relatório**, ajustar o período e
os filtros e escolher **Baixar PDF**, **Baixar Excel** ou **Baixar CSV**.
Uma futura entrada no dashboard poderá preencher o período do mês selecionado,
reutilizando o mesmo fluxo de exportação.

### Tela de Relatórios

- Entrada ativa no menu lateral, inclusive no menu móvel, na rota protegida `/reports`.
- Tela com opções PDF, Excel e CSV e explicação do conteúdo de cada formato.
- Cada opção abre o diálogo existente com o formato selecionado e o mês atual sugerido.
- Período, tipo, conta e categoria continuam ajustáveis antes do download.
- Carregar contas e categorias antes de habilitar a exportação; permitir nova tentativa em caso de falha.
- Contas e categorias vazias não impedem gerar um relatório sem movimentações.
- Manter validações, limites, privacidade e tratamento de erros do fluxo de Transações.
- Layout responsivo com cores dos temas claro e escuro.

Validação: 117 testes do frontend passaram em 25 arquivos e o build de produção
foi concluído, mantendo os avisos existentes de tamanho. Os novos testes cobrem
seleção dos três formatos, envio dos filtros, carregamento, falha e nova tentativa,
opções vazias, sessão expirada, cancelamento de requisições e navegação pelo menu
móvel. Uma prévia local com serviços substituídos foi revisada nos temas claro e
escuro e em largura de celular, incluindo abertura do Excel e retorno do foco ao
cancelar. Não houve nova execução do fluxo com a API real nesta etapa.
