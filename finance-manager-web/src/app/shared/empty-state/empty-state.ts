import { Component, input, output } from '@angular/core';
import { Params, RouterLink } from '@angular/router';
import { LucideChartNoAxesCombined, LucidePlus, LucideReceiptText, LucideSearch, LucideTags, LucideWalletCards } from '@lucide/angular';

@Component({
  selector: 'app-empty-state',
  imports: [RouterLink, LucideChartNoAxesCombined, LucidePlus, LucideReceiptText, LucideSearch, LucideTags, LucideWalletCards],
  templateUrl: './empty-state.html',
  styleUrl: './empty-state.css',
})
export class EmptyState {
  readonly icon = input<'wallet' | 'transactions' | 'chart' | 'categories' | 'search'>('transactions');
  readonly title = input.required<string>();
  readonly description = input.required<string>();
  readonly actionLabel = input.required<string>();
  readonly actionRoute = input<string | null>(null);
  readonly actionParams = input<Params | null>(null);
  readonly secondaryLabel = input('');
  readonly compact = input(false);
  readonly framed = input(false);
  readonly action = output<void>();
  readonly secondaryAction = output<void>();
}
