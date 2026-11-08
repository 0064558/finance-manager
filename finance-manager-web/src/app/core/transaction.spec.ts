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

it('sends a message and reads history through the chat endpoint', () => {
  TestBed.configureTestingModule({ providers: [TransactionApi, provideHttpClient(), provideHttpClientTesting()] });
  const api = TestBed.inject(TransactionApi);
  const http = TestBed.inject(HttpTestingController);
  const entry = {
    id: 'chat-id', prompt: 'Gastei 47,90 no almoço.', saved: false,
    response: { status: 'SUGESTAO', descricao: 'Almoço', valor: 47.90, tipo: 'DESPESA' },
  };
  const received = vi.fn();

  const message = {
    description: entry.prompt, replyTo: 'previous-id', requestId: 'new-id', timeZone: 'America/Sao_Paulo',
  };
  api.sendChatMessage(message).subscribe(received);

  const request = http.expectOne('/api/v1/transactions/chat');
  expect(request.request.method).toBe('POST');
  expect(request.request.body).toEqual(message);
  expect(request.request.headers.has('Idempotency-Key')).toBe(false);
  request.flush(entry);
  expect(received).toHaveBeenCalledWith(entry);

  api.getChatHistory().subscribe(received);
  const history = http.expectOne('/api/v1/transactions/chat');
  expect(history.request.method).toBe('GET');
  history.flush([entry]);
  expect(received).toHaveBeenLastCalledWith([entry]);

  api.discardChatDraft('draft-id').subscribe();
  const discard = http.expectOne('/api/v1/transactions/chat/drafts/draft-id');
  expect(discard.request.method).toBe('DELETE');
  discard.flush(null, { status: 204, statusText: 'No Content' });
  http.verify();
});
