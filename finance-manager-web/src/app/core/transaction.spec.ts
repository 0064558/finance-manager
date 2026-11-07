import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { TransactionApi } from './transaction';

describe('TransactionApi Idempotency-Key', () => {
  it('quando envia uma operação, então preserva a chave inclusive ao repetir a requisição', () => {
    TestBed.configureTestingModule({ providers: [TransactionApi, provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(TransactionApi);
    const http = TestBed.inject(HttpTestingController);
    const operation = api.create({
      accountId: 'account', categoryId: 'category', type: 'EXPENSE', amount: 10, occurredOn: '2026-10-29',
    }, 'same-operation');
    for (let attempt = 0; attempt < 2; attempt++) {
      operation.subscribe();
      const request = http.expectOne('/api/v1/transactions');
      expect(request.request.headers.get('Idempotency-Key')).toBe('same-operation');
      expect(request.request.method).toBe('POST');
      request.flush({ id: 'created' });
    }
    http.verify();
  });
});

it('requests a suggestion without calling the transaction creation endpoint', () => {
  TestBed.configureTestingModule({ providers: [TransactionApi, provideHttpClient(), provideHttpClientTesting()] });
  const api = TestBed.inject(TransactionApi);
  const http = TestBed.inject(HttpTestingController);
  const suggestion = { status: 'SUGESTAO', descricao: 'Almoço', valor: 47.90, tipo: 'DESPESA' };
  const received = vi.fn();

  api.suggest('Gastei 47,90 no almoço.').subscribe(received);

  const request = http.expectOne('/api/v1/transactions/suggestions');
  expect(request.request.method).toBe('POST');
  expect(request.request.body).toEqual({ description: 'Gastei 47,90 no almoço.' });
  expect(request.request.headers.has('Idempotency-Key')).toBe(false);
  request.flush(suggestion);
  expect(received).toHaveBeenCalledWith(suggestion);
  http.verify();
});
