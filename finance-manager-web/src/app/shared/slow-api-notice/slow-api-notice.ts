import { Component, inject } from '@angular/core';
import { SlowApiLoading } from '../../core/slow-api-loading';

@Component({
  selector: 'app-slow-api-notice',
  templateUrl: './slow-api-notice.html',
  styleUrl: './slow-api-notice.css',
})
export class SlowApiNotice {
  protected readonly loading = inject(SlowApiLoading);
}
