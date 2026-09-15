import { DestroyRef, Service, inject, signal } from '@angular/core';

export interface Notification {
  id: number;
  kind: 'success' | 'error';
  message: string;
}

@Service()
export class Notifications {
  private readonly state = signal<Notification[]>([]);
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();
  private nextId = 0;

  // A interface lê o sinal, mas apenas este serviço altera as notificações.
  readonly items = this.state.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clear());
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  dismiss(id: number): void {
    this.pause(id);
    this.state.update((items) => items.filter((item) => item.id !== id));
  }

  pause(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) clearTimeout(timer);
    this.timers.delete(id);
  }

  resume(id: number): void {
    this.pause(id);
    const item = this.state().find((item) => item.id === id);
    // Erros permanecem visíveis até o fechamento manual.
    if (item?.kind === 'success') {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), 6000),
      );
    }
  }

  clear(): void {
    for (const id of this.timers.keys()) this.pause(id);
    this.state.set([]);
  }

  private show(message: string, kind: Notification['kind']): void {
    const oldest = this.state()[0];
    if (this.state().length >= 3 && oldest) this.dismiss(oldest.id);

    const item: Notification = { id: ++this.nextId, kind, message };
    this.state.update((items) => [...items, item]);
    this.resume(item.id);
  }
}
