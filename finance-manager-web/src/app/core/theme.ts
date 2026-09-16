import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, computed, effect, inject, signal } from '@angular/core';

type ThemeMode = 'light' | 'dark';
const storageKey = 'finance-manager.theme';

@Injectable({ providedIn: 'root' })
// O serviço Theme é responsável por gerenciar o tema da aplicação, permitindo alternar entre os modos claro e escuro. 
// Ele utiliza sinais reativos para acompanhar as preferências do usuário e do sistema, aplicando as mudanças de tema de forma dinâmica. 
// O serviço também persiste a preferência do usuário no armazenamento local do navegador, garantindo que a escolha seja mantida entre sessões.
export class Theme {
  
  // injeção de dependências do Angular para acessar o documento, verificar se o código está sendo executado no navegador e 
  // obter informações sobre o sistema operacional.
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));

  // O serviço Theme utiliza a API matchMedia do navegador para detectar se o usuário prefere o modo escuro no sistema operacional.
  private readonly media = this.browser && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  // Sinais reativos para acompanhar as preferências do usuário e do sistema.
  private readonly systemDark = signal(this.media?.matches ?? false);
  private readonly preference = signal<ThemeMode | null>(this.readPreference());
  readonly dark = computed(() => (this.preference() ?? (this.systemDark() ? 'dark' : 'light')) === 'dark');

  // O construtor do serviço Theme aplica o tema inicial da aplicação com base nas preferências do usuário e do sistema.
  constructor() {
    this.apply();
    effect(() => this.apply());
    const onSystemChange = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
    this.media?.addEventListener?.('change', onSystemChange);
    inject(DestroyRef).onDestroy(() => this.media?.removeEventListener?.('change', onSystemChange));
  }

  // O método toggle alterna entre os modos claro e escuro, chamando o método setDark com o valor oposto do estado atual do tema.
  toggle(): void {
    this.setDark(!this.dark());
  }

  // O método setDark altera o modo de tema da aplicação para escuro ou claro, dependendo do valor booleano fornecido como argumento.
  setDark(enabled: boolean): void {
    const next: ThemeMode = enabled ? 'dark' : 'light';
    this.preference.set(next);
    this.apply();
    try {
      if (this.browser) localStorage.setItem(storageKey, next);
    } catch {
      // The selected theme remains usable without browser storage.
    }
  }

  // O método apply aplica o tema atual da aplicação, definindo o atributo data-theme no elemento raiz do documento (document.documentElement) para 'dark' ou 'light', dependendo do estado atual do tema.
  private apply(): void {
    this.document.documentElement.dataset['theme'] = this.dark() ? 'dark' : 'light';
  }

  // O método readPreference lê a preferência de tema do usuário armazenada no armazenamento local do navegador.
  private readPreference(): ThemeMode | null {
    try {
      const saved = this.browser ? localStorage.getItem(storageKey) : null;
      return saved === 'light' || saved === 'dark' ? saved : null;
    } catch {
      return null;
    }
  }
}
