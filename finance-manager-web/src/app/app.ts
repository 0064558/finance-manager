import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationHost } from './shared/notification-host/notification-host';
import { SlowApiNotice } from './shared/slow-api-notice/slow-api-notice';

@Component({
  imports: [RouterOutlet, NotificationHost, SlowApiNotice],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('finance-manager-web');
}
