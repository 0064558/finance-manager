import { Component, inject } from '@angular/core';
import { LucideCircleCheck, LucideCircleAlert, LucideX } from '@lucide/angular';
import { Notifications } from '../../core/notifications';

@Component({
  selector: 'app-notification-host',
  imports: [LucideCircleCheck, LucideCircleAlert, LucideX],
  templateUrl: './notification-host.html',
  styleUrl: './notification-host.css',
})
export class NotificationHost {
  protected readonly notifications = inject(Notifications);

  protected resume(id: number, element: HTMLElement): void {
    // Não reinicia o prazo se o usuário ainda está lendo ou usando o botão.
    if (element.matches(':hover') || element.contains(element.ownerDocument.activeElement)) return;
    this.notifications.resume(id);
  }
}
