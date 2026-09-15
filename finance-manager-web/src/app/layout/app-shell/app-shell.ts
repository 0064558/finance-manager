import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import {
  LucideArrowLeftRight,
  LucideChartNoAxesCombined,
  LucideCircleDollarSign,
  LucideLayoutDashboard,
  LucideLogOut,
  LucideMenu,
  LucideSettings,
  LucideTags,
  LucideUserRound,
  LucideWalletCards,
  LucideX,
} from '@lucide/angular';
import { Auth } from '../../core/auth';
import { AuthUser } from '../../core/auth.models';

@Component({
  selector: 'app-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    LucideArrowLeftRight,
    LucideChartNoAxesCombined,
    LucideCircleDollarSign,
    LucideLayoutDashboard,
    LucideLogOut,
    LucideMenu,
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
      default: return { label: 'VISÃO GERAL', caption: 'Acompanhe sua saúde financeira' };
    }
  });

  protected readonly sidebarCollapsed = signal(false);
  protected readonly mobileMenuOpen = signal(false);
  protected readonly currentUser = signal<AuthUser | null>(null);
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
      next: (user) => this.currentUser.set(user),
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

  protected isNavigationExpanded(): boolean {
    return this.isMobileViewport() ? this.mobileMenuOpen() : !this.sidebarCollapsed();
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
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
