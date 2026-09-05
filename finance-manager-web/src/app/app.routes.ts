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
    path: '',
    component: AppShell,
    // Todas as páginas filhas desta moldura exigem autenticação.
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then((page) => page.Dashboard),
      },
    ],
  },
];
