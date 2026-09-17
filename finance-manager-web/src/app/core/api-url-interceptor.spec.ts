import { TestBed } from '@angular/core/testing';
import { apiUrlInterceptor } from './api-url-interceptor';
import { HttpClient } from '@angular/common/http';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';

describe('apiUrlInterceptor', () => {
  
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;

  // Antes de cada teste, configura o módulo de teste com os provedores necessários, incluindo o cliente HTTP com o interceptor de URL da API e o provedor de teste HTTP
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiUrlInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  // Após cada teste, verifica se não há requisições pendentes no provedor de teste HTTP, garantindo que todas as requisições foram tratadas corretamente
  afterEach(() => {
    httpTesting.verify();
  });

  // Teste que verifica se o interceptor adiciona a URL base da API às requisições que começam com '/api/'
  it('should add the API base URL to requests starting with /api/', () => {
    httpClient.get('/api/v1/categories').subscribe();

    const request = httpTesting.expectOne(`${environment.apiBaseUrl}/api/v1/categories`);

    request.flush({}); // Simula uma resposta vazia do servidor
  });

  // Teste que verifica se URLs externas não são modificadas pelo interceptor
  it('externals URLs should not be modified', () => {
    // Faz uma requisição para uma URL externa que não começa com '/api/'
    httpClient.get('https://example.com').subscribe();

    // Verifica se a requisição foi feita para a URL externa sem modificação
    const request = httpTesting.expectOne('https://example.com');

    request.flush({}); // Simula uma resposta vazia do servidor
  });
});
