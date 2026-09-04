import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
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
export class AppShell implements OnInit {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

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
