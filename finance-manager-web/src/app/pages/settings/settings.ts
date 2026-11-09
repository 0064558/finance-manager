import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideCheck, LucideEye, LucideEyeOff, LucideMessageCircle, LucideMoon, LucideSettings, LucideSun } from '@lucide/angular';
import { finalize } from 'rxjs';
import { ChatHistoryActions } from '../../core/chat-history-actions';
import { Notifications } from '../../core/notifications';
import { Theme } from '../../core/theme';
import { ValuePrivacy } from '../../core/value-privacy';

@Component({
  selector: 'app-settings',
  imports: [LucideCheck, LucideEye, LucideEyeOff, LucideMessageCircle, LucideMoon, LucideSettings, LucideSun],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(Notifications);
  private readonly chatHistory = inject(ChatHistoryActions);

  // O serviço Theme é injetado no componente Settings para gerenciar o tema da aplicação. Ele fornece métodos para alternar entre os modos claro e escuro, bem como para salvar a preferência do usuário.
  protected readonly theme = inject(Theme);
  protected readonly valuePrivacy = inject(ValuePrivacy);
  protected readonly clearingHistory = signal(false);
  protected readonly clearHistoryError = signal('');
  protected readonly historyClearBlocked = computed(() => this.chatHistory.busy() || this.chatHistory.chatInUse());

  // O método setDarkMode é responsável por alterar o modo de tema da aplicação para escuro ou claro, dependendo do valor booleano fornecido como argumento. Ele utiliza o serviço Theme injetado para aplicar a mudança de tema e salvar a preferência do usuário.
  protected setDarkMode(enabled: boolean): void {
    this.theme.setDark(enabled);
  }

  protected setValuesHidden(hidden: boolean): void {
    this.valuePrivacy.setHidden(hidden);
  }

  protected openClearHistory(dialog: HTMLDialogElement): void {
    if (this.historyClearBlocked() || dialog.open) return;
    this.clearHistoryError.set('');
    dialog.showModal();
  }

  protected cancelClearHistory(dialog: HTMLDialogElement, event?: Event): void {
    event?.preventDefault();
    if (!this.clearingHistory()) dialog.close();
  }

  protected confirmClearHistory(dialog: HTMLDialogElement): void {
    if (!dialog.open || this.historyClearBlocked() || this.clearingHistory()) return;
    this.clearingHistory.set(true);
    this.clearHistoryError.set('');

    this.chatHistory.clear().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.clearingHistory.set(false)),
    ).subscribe({
      next: () => {
        dialog.close();
        this.notifications.success('Histórico do chat limpo.');
      },
      error: () => this.clearHistoryError.set('Não foi possível limpar o histórico do chat. Tente novamente.'),
    });
  }
}
