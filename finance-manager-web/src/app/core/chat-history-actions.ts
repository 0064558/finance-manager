import { inject, Injector, Service, signal } from '@angular/core';
import { defer, finalize, Observable, shareReplay, Subject, tap, throwError } from 'rxjs';
import { TransactionApi } from './transaction';

@Service()
export class ChatHistoryActions {
  private readonly injector = inject(Injector);
  private readonly mutationPending = signal(false);
  private readonly activeChatWork = signal(false);
  private readonly cleared = new Subject<void>();

  readonly busy = this.mutationPending.asReadonly();
  readonly chatInUse = this.activeChatWork.asReadonly();
  readonly cleared$ = this.cleared.asObservable();

  setChatInUse(inUse: boolean): void {
    this.activeChatWork.set(inUse);
  }

  clear(): Observable<void> {
    return this.run(api => api.clearChatHistory(), () => this.cleared.next());
  }

  deleteEntry(id: string, byRequestId = false): Observable<void> {
    return this.run(api => api.deleteChatMessage(id, byRequestId));
  }

  private run(request: (api: TransactionApi) => Observable<void>, onSuccess?: () => void): Observable<void> {
    return defer(() => {
      if (this.busy() || this.chatInUse()) {
        return throwError(() => new Error('Há uma ação em andamento no chat.'));
      }
      this.mutationPending.set(true);
      let settled = false;
      const release = () => {
        if (settled) return;
        settled = true;
        this.mutationPending.set(false);
      };
      return defer(() => request(this.injector.get(TransactionApi))).pipe(
        tap({
          next: () => {
            release();
            onSuccess?.();
          },
          error: release,
        }),
        finalize(release),
      );
    // Finish the deletion and notify the chat even if the settings page is left.
    }).pipe(shareReplay({ bufferSize: 1, refCount: false }));
  }
}
