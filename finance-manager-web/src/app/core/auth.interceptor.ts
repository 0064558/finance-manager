import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';

const publicAuthEndpoints = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
];

// Interceptor HTTP que adiciona o token de autenticação aos cabeçalhos das requisições, 
// exceto para os endpoints públicos de login e registro
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(Auth);
  const token = auth.getToken();

  // Verifica se a requisição é para um endpoint público de autenticação (login ou registro)
  const isPublicAuthRequest = publicAuthEndpoints.some((endpoint) =>
    request.url.endsWith(endpoint),
  );

  // Se não houver token ou se a requisição for para um endpoint público, permite que a requisição prossiga sem modificação
  if (!token || isPublicAuthRequest) {
    return next(request);
  }

  // Adiciona o token de autenticação aos cabeçalhos da requisição
  const authenticatedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest);
};
