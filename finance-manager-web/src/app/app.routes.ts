import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { authGuard } from './core/auth-guard';
import { Register } from './pages/register/register';
import { AppShell } from './layout/app-shell/app-shell';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    component: Login,
  },
  {
    path: 'register',
    component: Register,
  },
  {
    // A rota raiz da aplicação é protegida pelo authGuard, garantindo que apenas usuários autenticados possam acessar as páginas filhas.
    path: '',
    // O componente AppShell serve como a moldura principal da aplicação, fornecendo uma barra lateral de navegação, um cabeçalho e um espaço para exibir o conteúdo das páginas filhas.
    component: AppShell,
    // Todas as páginas filhas desta moldura exigem autenticação.
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        // A página de dashboard é carregada de forma preguiçosa (lazy loading) para otimizar o desempenho da aplicação.
        loadComponent: () => import('./pages/dashboard/dashboard').then((page) => page.Dashboard),
      },
      {
        path: 'accounts',
        // A página de contas financeiras também é carregada de forma preguiçosa (lazy loading).
        loadComponent: () => import('./pages/accounts/accounts').then((page) => page.Accounts),
      },
    ],
  },
];
