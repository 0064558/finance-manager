# Finance Manager Web

Frontend Angular do Finance Manager. A aplicação consome a API Spring Boot,
permite autenticação com JWT e oferece os fluxos do MVP para contas,
categorias, transações e resumo financeiro.

## Aplicação publicada

- Frontend: [finance-manager-lime.vercel.app](https://finance-manager-lime.vercel.app)
- API consumida: [finance-manager-fttd.onrender.com](https://finance-manager-fttd.onrender.com)

O frontend é um site estático publicado na Vercel. Ele não armazena segredos,
senhas ou credenciais de banco.

## Stack

- Angular 22.
- TypeScript.
- Angular Router e HttpClient.
- Vitest para testes unitários.
- Vercel para hospedagem do build estático.

## Funcionalidades

- Cadastro e login de usuários.
- Persistência do JWT no fluxo de autenticação do cliente.
- Dashboard com resumo financeiro e visualizações.
- Guia inicial para usuários com onboarding pendente.
- CRUD de contas financeiras.
- CRUD de categorias de receitas e despesas.
- CRUD de transações com filtros e paginação.
- Telas de configurações e feedback visual para estados de carregamento e erro.

## Guia inicial e estados de carregamento

Após autenticar, a aplicação consulta `GET /api/v1/auth/me`. Quando
`onboardingVersion` é menor que `CURRENT_ONBOARDING_VERSION` (atualmente `1`),
o guia começa na dashboard e percorre Contas, Transações, Categorias e
Configurações. Cada etapa navega para a tela correspondente, destaca o ponto
de partida e mantém o restante da página visível. O usuário pode voltar,
avançar, pular ou concluir. Pular e concluir enviam
`PATCH /api/v1/auth/me/onboarding` com `{ "onboardingVersion": 1 }`. Se a
gravação falhar, o guia permanece aberto e oferece uma mensagem de erro.

O título da dashboard mostra um skeleton enquanto o nome do usuário é
carregado e usa “Olá, usuário” caso a consulta falhe. Na lista de contas da
dashboard, o ícone acompanha o tipo: dinheiro (`CASH`), conta corrente
(`CHECKING`) ou poupança (`SAVINGS`).

Os estados de carregamento de cada tela continuam ativos. Um aviso global
complementar aparece quando alguma requisição HTTP ultrapassa cinco segundos,
explicando a possível demora da API hospedada no Render; ele desaparece quando
não há mais requisições lentas pendentes. Esse aviso não indica falha nem
substitui as mensagens de erro das telas.

## Estrutura principal

```text
finance-manager-web/
├── src/app/core/          # autenticação, interceptors e serviços da API
├── src/app/pages/         # páginas e fluxos de negócio
├── src/app/shared/        # componentes reutilizáveis e visualizações
├── src/environments/      # URL da API por ambiente
├── public/                # arquivos estáticos
├── angular.json           # build, serve e substituição de ambientes
└── vercel.json            # rewrite das rotas da SPA
```

## Pré-requisitos

- Node.js compatível com o projeto.
- npm 11 ou superior, conforme `package.json`.
- API local do Finance Manager disponível em `http://localhost:8080` para o
  fluxo completo de desenvolvimento.

## Executar localmente

Instale as dependências:

```powershell
npm ci
```

Inicie o servidor de desenvolvimento:

```powershell
npm start
```

Abra [http://localhost:4200](http://localhost:4200).

No desenvolvimento, `environment.development.ts` deixa `apiBaseUrl` vazio.
As chamadas iniciadas com `/api/` permanecem relativas e são encaminhadas pelo
`proxy.conf.json` para a API local em `http://localhost:8080`.

## Configuração da API

O ambiente de produção está em:

```typescript
export const environment = {
  apiBaseUrl: 'https://finance-manager-fttd.onrender.com',
  production: true,
};
```

O ambiente de desenvolvimento usa:

```typescript
export const environment = {
  apiBaseUrl: '',
  production: false,
};
```

O `api-url-interceptor` adiciona `apiBaseUrl` somente às URLs que começam com
`/api/`. Assim, os serviços podem usar caminhos relativos e a mesma aplicação
funciona localmente pelo proxy e em produção diretamente contra a API pública.

Não coloque tokens, senhas, chaves privadas ou URLs de conexão do banco no
frontend. O endereço público da API não é um segredo.

## Testes e build

Execute os testes unitários uma vez, sem modo de observação:

```powershell
npm test -- --watch=false
```

Gere o build otimizado de produção:

```powershell
npm run build
```

Os arquivos são gerados em:

```text
dist/finance-manager-web/browser
```

O GitHub Actions executa `npm ci`, os testes e o build do frontend em um job
independente do backend. Uma alteração só deve ser publicada depois que os dois
jobs estiverem verdes.

## Deploy na Vercel

O projeto está configurado como um projeto Angular estático:

- **Root Directory:** `finance-manager-web`
- **Install Command:** `npm ci`
- **Build Command:** `npm run build`
- **Output Directory:** `dist/finance-manager-web/browser`
- **Branch de deploy:** `main`

O arquivo `vercel.json` contém o rewrite:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

Esse rewrite permite que rotas do Angular continuem funcionando quando o
usuário acessa ou atualiza diretamente uma URL interna da aplicação.

## Integração com CORS

Como o frontend e a API estão em origens diferentes, o backend deve autorizar a
origem publicada:

```text
https://finance-manager-lime.vercel.app
```

Essa configuração é feita na variável `CORS_ALLOWED_ORIGINS` do Render, não no
frontend. Se o domínio da Vercel mudar, atualize o backend e faça um novo
deploy da API.

## Solução de problemas

- **Erro `ng: command not found`:** verifique se a instalação usa `npm ci` antes
  de executar `npm run build`.
- **Erro de CORS:** confira se a URL atual da Vercel está em
  `CORS_ALLOWED_ORIGINS` no Render, sem barra final.
- **Rota interna retorna 404 após atualizar:** confirme se o `vercel.json` está
  na raiz de `finance-manager-web`.
- **Chamadas locais não chegam à API:** confirme que o backend está ativo na
  porta `8080` e que o `npm start` está usando `proxy.conf.json`.

## Documentação relacionada

- [Documentação principal do projeto](../README.md)
- [Documentação da API](../finance-manager-api/README.md)
- [Configuração de ambiente de desenvolvimento](src/environments/environment.development.ts)
- [Configuração de produção](src/environments/environment.ts)
