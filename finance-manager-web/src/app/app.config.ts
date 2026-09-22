import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
  provideAppInitializer,
  inject,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/auth.interceptor';
import { apiUrlInterceptor } from './core/api-url-interceptor';
import { slowApiLoadingInterceptor } from './core/slow-api-loading.interceptor';
import { Theme } from './core/theme';

registerLocaleData(localePt, 'pt-BR');

// Configuração do aplicativo Angular, incluindo interceptadores HTTP, inicializadores e provedores de serviços
// necessário para o funcionamento correto do aplicativo, como interceptadores de autenticação e URL da API, inicialização do tema e configuração de localização
export const appConfig: ApplicationConfig = {
  providers: [
    provideAppInitializer(() => { inject(Theme); }),
    // Configura o provedor de cliente HTTP com os interceptadores de URL da API e autenticação
    // O interceptor de URL da API adiciona a URL base da API às requisições
    // O interceptor de autenticação adiciona o token de autenticação aos cabeçalhos das requisições
    provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor, slowApiLoadingInterceptor])),
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
  ],
};
