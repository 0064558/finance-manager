# Ativação do RAG no Finance Manager

O RAG é um piloto de categorização no chat. O backend calcula os vetores com palavras e trechos de palavras, consulta o pgvector no Neon e aplica a categoria dentro do Finance Manager. O Gemini continua gerando o rascunho a partir da conversa e do catálogo de categorias. O histórico recuperado e os vetores ficam no Finance Manager e no Neon. O Angular usa o mesmo contrato e a mesma URL da API no Render.

## 1. Validar o Neon antes do deploy

1. Registre o SHA da API ativa, o deploy saudável anterior no Render e um ponto de recuperação do Neon. Em uma branch de teste do Neon com o mesmo schema, ensaie a atualização de V10 para V11 com os papéis de produção e o RAG desligado. Use um banco descartável para testes que gravem dados fictícios. Se a branch contiver dados reais, mantenha a indexação desativada durante o ensaio.
2. No banco e branch corretos, consulte as versões e o schema da extensão com a conexão direta do Flyway. O script assume o schema `public` usado pelo projeto.

   ```sql
   SELECT version, script, success
   FROM public.flyway_schema_history
   WHERE version IN ('10', '11')
   ORDER BY installed_rank;

   SELECT a.name, a.default_version, a.installed_version, n.nspname AS extension_schema
   FROM pg_available_extensions a
   LEFT JOIN pg_extension e ON e.extname = a.name
   LEFT JOIN pg_namespace n ON n.oid = e.extnamespace
   WHERE a.name = 'vector';

   SELECT current_user, current_database(), current_schemas(false),
          has_schema_privilege(current_user, 'public', 'USAGE') AS can_use_public,
          has_schema_privilege(current_user, 'public', 'CREATE') AS can_create_in_public,
          has_database_privilege(current_user, current_database(), 'CREATE') AS can_create_in_database;

   SELECT pg_get_userbyid(relowner) AS transactions_owner,
          pg_has_role(current_user, relowner, 'USAGE') AS has_owner_privileges,
          has_column_privilege(current_user, 'public.users', 'id', 'REFERENCES') AS can_reference_users,
          has_column_privilege(current_user, 'public.transactions', 'id', 'REFERENCES') AS can_reference_transactions
   FROM pg_class WHERE oid = 'public.transactions'::regclass;
   ```

3. A [V11](../finance-manager-api/src/main/resources/db/migration/V11__transaction_embeddings.sql) instala `vector`, cria `transaction_embedding_pilots`, `transaction_embeddings` com `vector(768)` e índices para o recorte por usuário e a fila. O Flyway aplica a migration mesmo com o RAG desligado. Se a extensão estiver ausente e o migrador não puder instalá-la no ensaio, peça ao administrador do banco para executar `CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public` no banco certo. Com a extensão instalada, confirme que o tipo `vector` e o operador de cosseno ficam visíveis para o papel da API. O índice em `transactions` é criado na migration e pode bloquear escritas durante a construção. Avalie o tamanho da tabela no ensaio para escolher a janela do deploy. [pgvector no Neon](https://neon.com/docs/extensions/pgvector) · [Papéis no Neon](https://neon.com/docs/manage/roles)
4. Depois da V11, confira os acessos efetivos com o papel usado pelo Render, inclusive se já houver privilégios padrão definidos. A consulta retorna uma linha por permissão e todas devem ser verdadeiras.

   ```sql
   WITH required(relation, privilege) AS (VALUES
       ('public.transaction_embedding_pilots', 'SELECT'),
       ('public.transaction_embedding_pilots', 'INSERT'),
       ('public.transaction_embeddings', 'SELECT'),
       ('public.transaction_embeddings', 'INSERT'),
       ('public.transaction_embeddings', 'UPDATE'),
       ('public.transaction_embeddings', 'DELETE')
   )
   SELECT relation, privilege, has_table_privilege(current_user, relation, privilege) AS allowed
   FROM required;

   SELECT ('[1,0]'::vector <=> '[1,0]'::vector) = 0 AS cosine_ok;
   ```

   Se faltarem privilégios, o dono das tabelas pode concedê-los pontualmente após conferir o nome do papel da aplicação no Render. Os acessos a `users` e `transactions` continuam sendo os do CRUD atual.

   ```sql
   GRANT SELECT, INSERT ON public.transaction_embedding_pilots TO finance_manager_app;
   GRANT SELECT, INSERT, UPDATE, DELETE ON public.transaction_embeddings TO finance_manager_app;
   ```

## 2. Confirmar o deploy com RAG desligado

- No Render, faça o deploy manual do commit que contém `LocalTransactionEmbeddingClient`. O [PR #7](https://github.com/0064558/finance-manager/pull/7) já inclui a V11 e usa o Gemini para embeddings. Use `SPRING_PROFILES_ACTIVE=prod` e `/actuator/health` como health check. Confirme nos logs do Flyway que V11 já está aplicada. No Neon de produção do Finance Manager, V11, pgvector 0.8.0 e os seis acessos da API às tabelas novas já foram confirmados.
- Confira `FINANCE_MANAGER_DB_URL`, `FINANCE_MANAGER_DB_USERNAME` e `FINANCE_MANAGER_DB_PASSWORD` para a conexão pooled da aplicação. Confira `FINANCE_MANAGER_FLYWAY_URL`, `FINANCE_MANAGER_FLYWAY_USERNAME` e `FINANCE_MANAGER_FLYWAY_PASSWORD` para a conexão direta de migração. Ambas devem apontar para o mesmo banco e branch com TLS. Preserve o `JWT_SECRET` e as origens de produção em `CORS_ALLOWED_ORIGINS`. [Pooling no Neon](https://neon.com/docs/connect/connection-pooling) · [Variáveis e segredos no Render](https://render.com/docs/configure-environment-variables)
- Preserve `TRANSACTION_SUGGESTION_PROVIDER=google-genai` e a `GEMINI_API_KEY` existentes para o chat. Mantenha o faturamento do Gemini desligado e o serviço no plano atual. Mantenha `TRANSACTION_CHAT_RAG_ENABLED=false` e `TRANSACTION_CHAT_RAG_PILOT_USER_IDS` vazio durante o deploy inicial. Verifique HTTP 200 e `UP` no health check, CORS para `https://finance-manager-lime.vercel.app` e o chat com catálogo usando uma conta existente e texto genérico. O Vercel não precisa receber segredos ou variáveis do RAG.

## 3. Ativar para usuários piloto

Confirme quais contas reais fazem parte do piloto antes de preencher a lista. O UUID de cada conta está no campo `id` da resposta autenticada de `GET /api/v1/auth/me`. As descrições do histórico são normalizadas e vetorizadas localmente. A conversa digitada e os nomes das categorias continuam sendo enviados ao Gemini pelo chat. Use texto e nomes genéricos durante a validação, pois a cota gratuita veda informações pessoais, confidenciais ou sensíveis. [Termos de uso e tratamento de dados do Gemini](https://ai.google.dev/gemini-api/terms#unpaid-services)

No Render, mantenha o provedor e a chave já validados. Em um novo deploy com o mesmo build, defina `TRANSACTION_CHAT_RAG_ENABLED=true` e preencha `TRANSACTION_CHAT_RAG_PILOT_USER_IDS` com os UUIDs reais separados por vírgula. Preserve `TRANSACTION_CHAT_RAG_SEARCH_TIMEOUT=2s` e `TRANSACTION_CHAT_RAG_MIN_SIMILARITY=0.55`, avaliados localmente. Na versão com o diagnóstico de busca, `TRANSACTION_CHAT_RAG_LOG_LEVEL=DEBUG` mostra o resultado, a duração e a quantidade de exemplos nos logs do Render. Volte a `INFO` após validar. [Deploy com mudanças de ambiente no Render](https://render.com/docs/configure-environment-variables)

O primeiro ciclo fixa o recorte nos 100 lançamentos confirmados mais recentes com descrição por piloto, pela data de criação. O worker também acompanha os lançamentos posteriores, em lotes de até 20 por usuário. A versão registrada é `local-ptbr-hash-v1:768:normalized-v1`. Vetores de outras versões são recalculados dentro do recorte. Falhas temporárias preservam o trabalho no Neon e adiam a próxima tentativa. Se o Render estiver no plano gratuito, a indexação para enquanto a API estiver suspensa por inatividade e volta quando o serviço subir. [Suspensão por inatividade no Render](https://render.com/docs/free)

## 4. Validar indexação, isolamento e chat

1. Confira no Neon a cobertura após os ciclos de indexação. Substitua `<UUID_REAL_DO_PILOTO>` por um UUID autorizado. A consulta mostra somente contagens e considera vetores de outra versão ou de descrição desatualizada como pendentes. Zero em todas as colunas pode indicar que o recorte ainda não foi criado ou está vazio.

   ```sql
   WITH cohort AS (
       SELECT e.embedding IS NOT NULL AS has_vector, e.skipped,
              e.embedding_version = 'local-ptbr-hash-v1:768:normalized-v1'
                AND e.source_hash = encode(sha256(convert_to(t.description, 'UTF8')), 'hex') AS current_vector
       FROM public.transactions t
       JOIN public.transaction_embedding_pilots p ON p.user_id = t.user_id
       LEFT JOIN public.transaction_embeddings e ON e.transaction_id = t.id
       WHERE t.user_id = '<UUID_REAL_DO_PILOTO>'::uuid
         AND t.description IS NOT NULL
         AND (t.created_at, t.id) >= (p.from_created_at, p.from_transaction_id)
   )
   SELECT count(*) FILTER (WHERE current_vector AND has_vector) AS ready,
          count(*) FILTER (WHERE current_vector AND skipped) AS skipped,
          count(*) FILTER (WHERE (current_vector AND (has_vector OR skipped)) IS NOT TRUE) AS pending
   FROM cohort;
   ```

2. Use uma conta piloto e uma transação real existente no recorte como referência. Em uma conversa nova, use termos genéricos em comum com a transação e um valor inequívoco. Confira o rascunho na Vercel. Nos logs do Render, `outcome=hit` indica recuperação e `Transaction chat applied local RAG category` confirma que o backend preencheu a categoria. A busca acontece apenas quando o Gemini deixa a categoria do lançamento individual em branco. Deixe o rascunho sem confirmar se o lançamento já foi registrado.
3. Com uma conta existente fora da lista, confirme que o mesmo fluxo de rascunho continua disponível e que não há busca RAG para esse pedido. O repositório filtra `transactions.user_id` pelo usuário autenticado e pelo tipo do rascunho. Em caso de timeout, ausência de correspondência, categoria removida ou resultados com categorias diferentes, o rascunho segue com a categoria em branco. Uma falha no Gemini durante a geração continua usando o erro controlado da API.
4. Em ambiente descartável, rode os testes de CRUD e de concorrência. Uma criação direta ou confirmação de rascunho entra na fila após persistir a transação. Uma edição de descrição torna o vetor anterior imediatamente inelegível pelo hash e a próxima indexação gera outro. Categoria e tipo são lidos da transação atual. Sem descrição, o vetor sai da busca e é removido pelo worker. A exclusão de transação remove o vetor pela FK `ON DELETE CASCADE`. O lease evita que um worker com trabalho vencido sobrescreva o resultado atual. Em produção, observe os mesmos indicadores durante operações reais do piloto.

   ```powershell
   # Em finance-manager-api, com Docker disponível
   .\mvnw.cmd verify
   # Em finance-manager-web
   npm test -- --watch=false
   npm run build
   ```

## 5. Reverter se necessário

1. Para interromper busca e indexação, defina `TRANSACTION_CHAT_RAG_ENABLED=false`, limpe a lista de pilotos e faça **Save and deploy** no Render. Confira o deploy efetivo, o health check e uma resposta de chat com catálogo. A operação preserva as transações e o estado de indexação no Neon. Um novo deploy com a flag ligada pode retomar o trabalho pendente.
2. Para regressão da API com a flag desligada, volte ao último deploy saudável com o RAG desligado no Render. Ensaie antes o build anterior contra a branch de teste já com V11. Por padrão o Flyway ignora migrations futuras na validação de um build anterior. Confira se o serviço preserva esse padrão e se o build inicia com o mesmo banco. O rollback pelo painel desativa o deploy automático e pode usar as variáveis do deploy anterior. Confira a flag no deploy restaurado e as variáveis salvas para o próximo deploy normal. [Rollback no Render](https://render.com/docs/rollbacks) · [Validação de migrations futuras no Flyway](https://documentation.red-gate.com/fd/flyway-ignore-migration-patterns-setting-277579002.html)
3. Preserve a V11 aplicada e as tabelas de vetores. Se for necessário corrigir o schema, use uma migration posterior. A remoção de dados, a reversão da extensão ou a restauração do banco de produção exigem confirmação explícita.
