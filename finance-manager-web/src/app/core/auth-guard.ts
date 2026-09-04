import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

// Guarda de rota que verifica se o usuário está autenticado antes de permitir o acesso a uma rota protegida
export const authGuard: CanActivateFn = (route, state) => {
  // Injeta o serviço de autenticação e o roteador para verificar o token de acesso e redirecionar se necessário
  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.getToken();


  // Se o token de acesso estiver presente, permite o acesso à rota
  if (token) {
    return true;
  }

  // Se o token de acesso não estiver presente, redireciona o usuário para a página de login
  return router.createUrlTree(['/login']);
};
