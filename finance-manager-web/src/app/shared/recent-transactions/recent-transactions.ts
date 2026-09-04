import { CurrencyPipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import { LucideArrowDownLeft, LucideArrowUpRight, LucideReceiptText } from '@lucide/angular';
import { AccountBalance } from '../../core/report.models';
import { PageResponse, TransactionResponse } from '../../core/transaction.models';

@Component({
  selector: 'app-recent-transactions',
  imports: [CurrencyPipe, LucideArrowDownLeft, LucideArrowUpRight, LucideReceiptText],
  templateUrl: './recent-transactions.html',
  styleUrl: './recent-transactions.css',
})
// RecentTransactions é um componente Angular que exibe uma lista de transações recentes, incluindo informações como data, 
// descrição, valor e conta associada. Ele recebe três entradas obrigatórias: transactions (uma página de transações), 
// accounts (uma lista de saldos de contas) e monthLabel (um rótulo para o mês). 
// O componente também inclui métodos auxiliares para formatar datas e obter o nome da conta associada a cada transação.
export class RecentTransactions {
  @Input({ required: true })
  transactions: PageResponse<TransactionResponse> | null = null;

  @Input({ required: true })
  accounts: AccountBalance[] = [];

  @Input({ required: true })
  monthLabel = '';

  protected formatDate(date: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
    }).format(new Date(`${date}T00:00:00`));
  }

  protected accountNameFor(transaction: TransactionResponse): string {
    return (
      this.accounts.find((account) => account.accountId === transaction.accountId)?.accountName ??
      'Conta'
    );
  }
}
