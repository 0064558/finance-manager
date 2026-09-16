import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

const storageKey = 'finance-manager.values-hidden';
export const hiddenAmount = '••••••';

@Injectable({ providedIn: 'root' })
export class ValuePrivacy {
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly state = signal(this.readPreference());
  readonly hidden = this.state.asReadonly();

  // Alterna a visibilidade dos valores e persiste a preferência do usuário no armazenamento local.
  toggle(): void {
    this.state.update(hidden => !hidden);
    try {
      if (this.browser) localStorage.setItem(storageKey, String(this.state()));
    } catch {
      // Ignora falhas de armazenamento local, como quando o armazenamento está cheio ou desativado.
    }
  }

  // Lê a preferência do usuário do armazenamento local, retornando false se não estiver disponível.
  private readPreference(): boolean {
    try {
      return this.browser && localStorage.getItem(storageKey) === 'true';
    } catch {
      return false;
    }
  }
}
