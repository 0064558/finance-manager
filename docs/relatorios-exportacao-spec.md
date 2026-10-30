# Exportação de relatórios — mini especificação

**Status:** escopo inicial revisado; implementação em andamento (primeira etapa: API CSV).  
**Data:** 30/10/2026. **Primeira entrega:** relatório de movimentações em PDF, Excel (.xlsx) e CSV.

## 1. Objetivo e base existente

Permitir que o usuário baixe suas movimentações e um resumo do período para
conferência, organização e compartilhamento fora do Finance Manager (DinDim).

O projeto já possui autenticação JWT, filtros por período/tipo/conta/categoria,
listagem paginada e relatórios JSON de resumo, categorias, fluxo de caixa e
saldos. Ainda não há geração de PDF ou planilha.

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
**XLSX:** abas `Resumo` e `Transações`, cabeçalho congelado e autofiltro na lista;
datas e valores como células tipadas, com formato de data e duas casas decimais.
Campos de texto devem ser gravados como texto, inclusive quando começam com
`=`, `+`, `-` ou `@`; conteúdo do usuário nunca vira fórmula.

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
| `400` | Parâmetro ausente/inválido, intervalo invertido ou limite excedido | Mostrar motivo e orientar ajuste dos filtros |
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
As bibliotecas PDF/XLSX e a meta de tempo de geração ainda não estão definidas.
O CSV segue a decisão sem dependência adicional registrada na seção 8.

## 7. Critérios de aceite e validação

- [ ] PDF, XLSX e CSV abrem corretamente e apresentam as mesmas movimentações;
  totais calculados do CSV coincidem com os resumos do PDF e XLSX.
- [ ] Exportar uma lista com várias páginas inclui todos os resultados do recorte.
- [ ] Datas de borda, filtros isolados/combinados e recorte vazio são respeitados.
- [ ] Com receita de R$ 1.000,00 e despesa de R$ 250,50, saldo líquido é
  R$ 749,50; ao filtrar despesas, receita é zero e saldo líquido é −R$ 250,50.
- [ ] Requisições sem autenticação e filtros de outro usuário não expõem dados.
- [ ] Exatamente 366 dias e 5.000 linhas são aceitos; exceder qualquer limite
  retorna erro e não baixa arquivo parcial.
- [ ] XLSX preserva células numéricas/datas e textos parecidos com fórmulas.
- [ ] CSV preserva acentos, delimitadores, aspas e descrições com várias linhas,
  neutraliza fórmulas e, sem resultados, contém apenas o cabeçalho.
- [ ] PDF com várias páginas, acentos e descrições longas passa por revisão visual.
- [ ] Interface cobre geração, sucesso, falha JSON em resposta `Blob`, sessão
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
explícito de escape e fórmulas e testes de conteúdo. PDF e XLSX ainda exigem
seleção de bibliotecas. Os dados exportados vêm de uma única consulta com joins
de conta/categoria, limite no banco e cópia para um modelo imutável antes da
renderização. Os totais usam essa mesma cópia.

- [x] Revisar escopo e registrar decisões e sequência incremental.
- [x] Entregar consulta comum: filtros, propriedade, ordenação, limites e totais.
- [x] Entregar API CSV: arquivo, erros padronizados, download e CORS.
- [x] Validar CSV com testes unitários e HTTP real em PostgreSQL descartável.
- [ ] Implementar XLSX com células tipadas e validar conteúdo.
- [ ] Implementar PDF e validar conteúdo e paginação visualmente.
- [ ] Implementar diálogo Angular, download e estados de erro/privacidade.
- [ ] Executar verificações completas do MVP e conferir os critérios de aceite.

**Evidência desta etapa (30/10/2026):** `mvnw.cmd -B verify` passou com 246 testes,
sem falhas ou testes ignorados, e cobertura mínima exigida atendida. Os novos
cenários estão em
[CsvReportRendererTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/report/service/CsvReportRendererTest.java),
[ReportExportRequestSpecTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/requestspec/ReportExportRequestSpecTest.java)
e [CorsIntegrationTest](../finance-manager-api/src/test/java/com/rodrigs/finance_manager_api/config/CorsIntegrationTest.java).
Cobrem conteúdo/escape, fórmulas, filtros, isolamento, datas inclusivas, ordem,
totais, recorte vazio, limites de 366 dias/5.000 linhas, cabeçalhos e erros sem
arquivo parcial. PDF, XLSX e interface ainda não foram implementados ou validados.

**Disponibilidade por etapa:** inicialmente, `format=CSV` é o único valor aceito
na API. `PDF` e `XLSX` passarão a ser aceitos quando seus renderizadores forem
implementados. Não há botão de exportação na interface nesta primeira etapa.
Uma futura entrada no dashboard poderá preencher o período do mês selecionado,
reutilizando o mesmo fluxo de exportação.
