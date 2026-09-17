import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment';

// Interceptor HTTP que adiciona a URL base da API às requisições
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {

  // Verifica se a URL da requisição começa com '/api/', indicando que é uma requisição para a API
  if (req.url.startsWith('/api/')) {
    const apiRequest = req.clone({
      // Adiciona a URL base da API às requisições que começam com '/api/'
      url: `${environment.apiBaseUrl}${req.url}`,
    });
    return next(apiRequest); // Permite que a requisição prossiga para o próximo interceptor ou para o backend
  }

  return next(req); // Permite que a requisição prossiga para o próximo interceptor ou para o backend
};
