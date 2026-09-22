import { Component, ElementRef, HostListener, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, finalize, map } from 'rxjs';
import {
  LucideArrowLeftRight,
  LucideChartNoAxesCombined,
  LucideLayoutDashboard,
  LucideLogOut,
  LucideMenu,
  LucideEye,
  LucideEyeOff,
  LucideSettings,
  LucideTags,
  LucideUserRound,
  LucideWalletCards,
  LucideX,
} from '@lucide/angular';
import { Auth } from '../../core/auth';
import { AuthUser } from '../../core/auth.models';
import { ValuePrivacy } from '../../core/value-privacy';

const CURRENT_ONBOARDING_VERSION = 1;

type OnboardingStep = {
  id: 'dashboard' | 'accounts' | 'transactions' | 'categories' | 'settings';
  route: string;
  section: string;
  title: string;
  description: string;
  detail: string;
};

@Component({
  selector: 'app-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideArrowLeftRight,
    LucideChartNoAxesCombined,
    LucideLayoutDashboard,
    LucideLogOut,
    LucideMenu,
    LucideEye,
    LucideEyeOff,
    LucideSettings,
    LucideTags,
    LucideUserRound,
    LucideWalletCards,
    LucideX,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
// AppShell é um componente Angular que serve como moldura principal da aplicação, fornecendo uma barra lateral de navegação,
// um cabeçalho e um espaço para exibir o conteúdo das páginas filhas. Ele gerencia o estado da barra lateral (colapsada ou expandida),
// o menu móvel (aberto ou fechado) e as informações do usuário autenticado. O componente também lida com eventos de teclado e redimensionamento
// da janela para melhorar a experiência do usuário em diferentes dispositivos.
export class AppShell implements OnInit {
  protected readonly valuePrivacy = inject(ValuePrivacy);
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly pageContext = computed(() => {
    const path = this.currentUrl().split(/[?#]/)[0];
    switch (path) {
      case '/accounts': return { label: 'SUAS CONTAS', caption: 'Cada saldo no seu lugar' };
      case '/transactions': return { label: 'TRANSAÇÕES', caption: 'Clareza em cada movimentação' };
      case '/categories': return { label: 'CATEGORIAS', caption: 'Mais organização para suas finanças' };
      case '/settings': return { label: 'CONFIGURAÇÕES', caption: 'Preferências do seu espaço' };
      default: return { label: 'VISÃO GERAL', caption: 'Acompanhe sua saúde financeira' };
    }
  });

  protected readonly sidebarCollapsed = signal(false);
  protected readonly mobileMenuOpen = signal(false);
  protected readonly currentUser = signal<AuthUser | null>(null);
  protected readonly onboardingStepIndex = signal<number | null>(null);
  protected readonly isSavingOnboarding = signal(false);
  protected readonly onboardingError = signal<string | null>(null);
  protected readonly onboardingSteps: OnboardingStep[] = [
    {
      id: 'dashboard',
      route: '/dashboard',
      section: 'SUA VISÃO GERAL',
      title: 'Tudo em um só lugar',
      description: 'Acompanhe seus saldos, receitas e despesas em uma visão rápida da sua vida financeira.',
      detail: 'A Dashboard reúne os principais números e as movimentações recentes das suas contas.',
    },
    {
      id: 'accounts',
      route: '/accounts',
      section: 'CONTAS',
      title: 'Organize onde seu dinheiro está',
      description: 'Cadastre conta corrente, poupança e dinheiro em mãos para manter os saldos reunidos.',
      detail: 'Você pode criar, editar e acompanhar cada conta sem misturar os valores.',
    },
    {
      id: 'transactions',
      route: '/transactions',
      section: 'TRANSAÇÕES',
      title: 'Registre entradas e despesas',
      description: 'Cada movimentação ajuda a manter seus saldos e relatórios em dia.',
      detail: 'Use os filtros por período, conta, categoria ou tipo para encontrar um lançamento.',
    },
    {
      id: 'categories',
      route: '/categories',
      section: 'CATEGORIAS',
      title: 'Entenda para onde vai seu dinheiro',
      description: 'Separe receitas e despesas em categorias que façam sentido para você.',
      detail: 'As categorias ajudam a dar contexto às transações e a enxergar seus hábitos.',
    },
    {
      id: 'settings',
      route: '/settings',
      section: 'CONFIGURAÇÕES',
      title: 'Deixe o espaço com a sua cara',
      description: 'Escolha o tema visual e controle quando os valores financeiros ficam visíveis.',
      detail: 'Suas preferências ficam salvas neste dispositivo e podem ser alteradas quando quiser.',
    },
  ];
  protected readonly activeOnboardingStep = computed(() => {
    const index = this.onboardingStepIndex();
    return index === null ? null : this.onboardingSteps[index] ?? null;
  });
  protected readonly onboardingStepNumber = computed(() => (this.onboardingStepIndex() ?? 0) + 1);
  protected readonly isFirstOnboardingStep = computed(() => this.onboardingStepIndex() === 0);
  protected readonly isLastOnboardingStep = computed(
    () => this.onboardingStepIndex() === this.onboardingSteps.length - 1,
  );
  @ViewChild('onboardingTitle') private onboardingTitle?: ElementRef<HTMLHeadingElement>;

  protected readonly userInitials = computed(() => {
    const fullName = this.currentUser()?.name?.trim() ?? '';
    const nameParts = fullName ? fullName.split(/\s+/) : [];

    return nameParts
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
  });

  ngOnInit(): void {
    this.auth.getCurrentUser().subscribe({
      next: (user) => {
        this.currentUser.set(user);
        if (user.onboardingVersion < CURRENT_ONBOARDING_VERSION) {
          this.navigateToOnboardingStep(0);
        }
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.logout();
        }
      },
    });
  }

  protected toggleNavigation(): void {
    if (this.isMobileViewport()) {
      this.mobileMenuOpen.update((isOpen) => !isOpen);
      return;
    }

    this.sidebarCollapsed.update((isCollapsed) => !isCollapsed);
  }

  protected closeMobileNavigation(): void {
    this.mobileMenuOpen.set(false);
  }

  protected openSettings(): void {
    this.closeMobileNavigation();
    void this.router.navigateByUrl('/settings');
  }

  protected isNavigationExpanded(): boolean {
    return this.isMobileViewport() ? this.mobileMenuOpen() : !this.sidebarCollapsed();
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  protected goToNextOnboardingStep(): void {
    if (this.isSavingOnboarding()) {
      return;
    }

    if (this.isLastOnboardingStep()) {
      this.completeOnboarding();
      return;
    }

    const currentIndex = this.onboardingStepIndex();
    if (currentIndex !== null) {
      this.navigateToOnboardingStep(currentIndex + 1);
    }
  }

  protected goToPreviousOnboardingStep(): void {
    if (this.isSavingOnboarding()) {
      return;
    }

    const currentIndex = this.onboardingStepIndex();
    if (currentIndex !== null && currentIndex > 0) {
      this.navigateToOnboardingStep(currentIndex - 1);
    }
  }

  protected skipOnboarding(): void {
    this.completeOnboarding();
  }

  private navigateToOnboardingStep(index: number): void {
    const step = this.onboardingSteps[index];
    if (!step) {
      return;
    }

    this.onboardingError.set(null);
    const currentPath = this.router.url.split(/[?#]/)[0];
    if (currentPath === step.route) {
      this.onboardingStepIndex.set(index);
      this.focusOnboardingTitle();
      return;
    }

    void this.router.navigateByUrl(step.route).then((navigated) => {
      if (navigated) {
        this.onboardingStepIndex.set(index);
        this.focusOnboardingTitle();
      }
    });
  }

  private completeOnboarding(): void {
    if (this.isSavingOnboarding()) {
      return;
    }

    this.isSavingOnboarding.set(true);
    this.onboardingError.set(null);
    this.auth
      .updateOnboardingVersion({ onboardingVersion: CURRENT_ONBOARDING_VERSION })
      .pipe(finalize(() => this.isSavingOnboarding.set(false)))
      .subscribe({
        next: (user) => {
          this.currentUser.set(user);
          this.onboardingStepIndex.set(null);
          document.getElementById('main-content')?.focus({ preventScroll: true });
        },
        error: () => {
          this.onboardingError.set('Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.');
        },
      });
  }

  private focusOnboardingTitle(): void {
    setTimeout(() => this.onboardingTitle?.nativeElement.focus(), 0);
  }

  @HostListener('document:keydown', ['$event'])
  protected handleOnboardingKeyboard(event: KeyboardEvent): void {
    if (!this.activeOnboardingStep()) {
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      this.skipOnboarding();
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const dialog = document.querySelector<HTMLElement>('.onboarding-dialog');
    const focusableElements = dialog
      ? Array.from(dialog.querySelectorAll<HTMLElement>(
          'button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
        ))
      : [];
    if (!dialog || focusableElements.length === 0) {
      event.preventDefault();
      dialog?.focus();
      return;
    }

    const first = focusableElements[0];
    const last = focusableElements[focusableElements.length - 1];
    const activeIndex = focusableElements.indexOf(document.activeElement as HTMLElement);
    if (activeIndex === -1) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    } else if (event.shiftKey && activeIndex === 0) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && activeIndex === focusableElements.length - 1) {
      event.preventDefault();
      first.focus();
    }
  }

  @HostListener('document:keydown.escape')
  protected closeNavigationWithEscape(): void {
    this.closeMobileNavigation();
  }

  @HostListener('window:resize')
  protected closeMobileNavigationAfterResize(): void {
    if (!this.isMobileViewport()) {
      this.closeMobileNavigation();
    }
  }

  private isMobileViewport(): boolean {
    return window.matchMedia('(max-width: 760px)').matches;
  }
}
