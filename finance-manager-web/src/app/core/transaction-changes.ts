import { Service } from '@angular/core';
import { Subject } from 'rxjs';

@Service()
export class TransactionChanges {
  private readonly saved = new Subject<void>();
  readonly saved$ = this.saved.asObservable();

  notifySaved(): void {
    this.saved.next();
  }
}
