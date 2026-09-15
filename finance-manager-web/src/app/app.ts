import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationHost } from './shared/notification-host/notification-host';

@Component({
  imports: [RouterOutlet, NotificationHost],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('finance-manager-web');
}
