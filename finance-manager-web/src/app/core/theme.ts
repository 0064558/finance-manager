import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, computed, effect, inject, signal } from '@angular/core';

type ThemeMode = 'light' | 'dark';
const storageKey = 'finance-manager.theme';

@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly media = this.browser && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly systemDark = signal(this.media?.matches ?? false);
  private readonly preference = signal<ThemeMode | null>(this.readPreference());
  readonly dark = computed(() => (this.preference() ?? (this.systemDark() ? 'dark' : 'light')) === 'dark');

  constructor() {
    this.apply();
    effect(() => this.apply());
    const onSystemChange = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
    this.media?.addEventListener?.('change', onSystemChange);
    inject(DestroyRef).onDestroy(() => this.media?.removeEventListener?.('change', onSystemChange));
  }

  toggle(): void {
    const next = this.dark() ? 'light' : 'dark';
    this.preference.set(next);
    this.apply();
    try {
      if (this.browser) localStorage.setItem(storageKey, next);
    } catch {
      // The selected theme remains usable without browser storage.
    }
  }

  private apply(): void {
    this.document.documentElement.dataset['theme'] = this.dark() ? 'dark' : 'light';
  }

  private readPreference(): ThemeMode | null {
    try {
      const saved = this.browser ? localStorage.getItem(storageKey) : null;
      return saved === 'light' || saved === 'dark' ? saved : null;
    } catch {
      return null;
    }
  }
}
