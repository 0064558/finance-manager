import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';
import { authGuard } from './core/auth-guard';
import { Register } from './pages/register/register';

export const routes: Routes = [
    {
        path: '',
        redirectTo: 'login',
        pathMatch: 'full'
    },
    {
        path: 'login',
        component: Login
    },
    {
        path: 'dashboard',
        component: Dashboard,
        // Aplica a guarda de rota para proteger a rota do dashboard, garantindo que apenas usuários autenticados possam acessá-la
        canActivate: [authGuard],
    },
    {
        path: 'register',
        component: Register,
    }
];
