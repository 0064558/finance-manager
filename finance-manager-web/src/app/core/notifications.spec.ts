import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Notifications } from './notifications';

describe('Notifications', () => {
  let notifications: Notifications;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    notifications = TestBed.inject(Notifications);
  });

  afterEach(() => {
    notifications.clear();
    vi.useRealTimers();
  });

  it('publishes a success message and dismisses it after six seconds', () => {
    notifications.success('Transação criada com sucesso.');
    expect(notifications.items()[0]).toMatchObject({
      kind: 'success',
      message: 'Transação criada com sucesso.',
    });
    vi.advanceTimersByTime(5999);
    expect(notifications.items()).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(notifications.items()).toHaveLength(0);
  });

  it('supports manual dismissal without removing another notification', () => {
    notifications.success('Conta criada.');
    notifications.success('Categoria criada.');
    notifications.dismiss(notifications.items()[0].id);
    expect(notifications.items().map((item) => item.message)).toEqual(['Categoria criada.']);
  });

  it('pauses dismissal and grants six seconds after resuming', () => {
    notifications.success('Transação atualizada.');
    const id = notifications.items()[0].id;
    vi.advanceTimersByTime(2000);
    notifications.pause(id);
    vi.advanceTimersByTime(10000);
    expect(notifications.items()).toHaveLength(1);
    notifications.resume(id);
    vi.advanceTimersByTime(6000);
    expect(notifications.items()).toHaveLength(0);
  });

  it('limits the stack to three messages with unique IDs', () => {
    for (const message of ['One', 'Two', 'Three', 'Four']) notifications.success(message);
    expect(notifications.items().map((item) => item.message)).toEqual(['Two', 'Three', 'Four']);
    expect(new Set(notifications.items().map((item) => item.id)).size).toBe(3);
    expect(vi.getTimerCount()).toBe(3);
  });

  it('keeps errors visible until manual dismissal', () => {
    notifications.error('Não foi possível salvar.');
    vi.advanceTimersByTime(60000);
    expect(notifications.items()).toHaveLength(1);
    notifications.dismiss(notifications.items()[0].id);
    expect(notifications.items()).toHaveLength(0);
  });

  it('clears messages and timers', () => {
    notifications.success('Conta excluída.');
    notifications.clear();
    expect(notifications.items()).toHaveLength(0);
    expect(vi.getTimerCount()).toBe(0);
  });
});
