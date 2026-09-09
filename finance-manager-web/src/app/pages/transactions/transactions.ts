import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';

import { CategoryApi } from '../../core/categories';
import { FinancialAccountApi } from '../../core/financial-accounts';
import { TransactionApi } from '../../core/transaction';

import { Category } from '../../core/category.models';
import { FinancialAccount } from '../../core/financial-account.models';
import { TransactionResponse, TransactionType } from '../../core/transaction.models';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';

// Representa uma transação financeira, incluindo informações como conta, categoria, tipo, valor, data de ocorrência e descrição.
interface TransactionViewModel extends TransactionResponse {
  accountName: string;
  categoryName: string;
}

@Component({
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule],
  selector: 'app-transactions',
  styleUrl: './transactions.css',
  templateUrl: './transactions.html',
})
export class Transactions implements OnInit {

  private readonly transactionApi = inject(TransactionApi);
  private readonly accountApi = inject(FinancialAccountApi);
  private readonly categoryApi = inject(CategoryApi);


  // Sinais para armazenar o estado das transações, contas financeiras, categorias, carregamento, mensagens de erro e informações de paginação.
  protected readonly transactions = signal<TransactionViewModel[]>([]);
  protected readonly accounts = signal<FinancialAccount[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  // FormBuilder é injetado para criar formulários reativos, permitindo a criação e validação de formulários de maneira mais fácil e estruturada.
   protected readonly formBuilder = inject(FormBuilder);

   protected readonly filterForm = this.formBuilder.nonNullable.group({
    startDate: [''],
    endDate: [''],
    type: this.formBuilder.nonNullable.control<TransactionType | ''>(''),
    accountId: [''],
    categoryId: [''],
   });


  ngOnInit(): void {
    this.loadTransactions();
  }

  protected loadTransactions(): void {

    this.isLoading.set(true);
    this.errorMessage.set(null);

    // forkJoin é usado para criar um único observable que aguarda a conclusão de várias requisições HTTP simultâneas, 
    // que emite um objeto contendo os resultados de todas as requisições quando todas forem concluídas.
    // "$" indica que a variável é um Observable, e o forkJoin aguarda todos os Observables completarem antes de emitir os resultados.
    const request$ = forkJoin({
      accounts: this.accountApi.getAll(),
      categories: this.categoryApi.getAll(),
      transactionsPage: this.transactionApi.getAll({ page: this.currentPage(), size: 10 }), // Aqui você pode ajustar o tamanho da página conforme necessário
    });

    // Assina o Observable resultante para processar os dados recebidos.
    request$
      // Pipe serve para encadear operadores de transformação e manipulação de dados em Observables, 
      // permitindo processar os resultados antes de assiná-los.
      .pipe(
        finalize(() => this.isLoading.set(false)) // O operador finalize é usado para executar uma ação (neste caso, definir isLoading como false) quando o Observable é concluído, independentemente de ter sido bem-sucedido ou não.
      )
      .subscribe({
        next: ({ accounts, categories, transactionsPage }) => {
          console.log('Accounts:', accounts);
          console.log('Categories:', categories);
          console.log('Transactions Page:', transactionsPage);

          this.accounts.set(accounts);
          this.categories.set(categories);

          this.currentPage.set(transactionsPage.number);
          this.totalPages.set(transactionsPage.totalPages);
          this.totalElements.set(transactionsPage.totalElements);

          // criar um map de contas usando id como chave e name como valor
          // O operador map é usado para transformar um array em outro array, aplicando uma função a cada elemento do array original.
          const accountMap = accounts.map(
            (account): [string, string] => [
              account.id,
              account.name,
            ],
          );
          const accountNameById = new Map<string, string>(accountMap);

          // criar um map de categorias usando id como chave e name como valor
          const categoryMap = categories.map(
            (category): [string, string] => [
              category.id,
              category.name,
            ],
          );
          const categoryNameById = new Map<string, string>(categoryMap);

          // Mapeia as transações para incluir os nomes das contas e categorias correspondentes.
          const transactionsWithNames: TransactionViewModel[] = transactionsPage.content.map(
            (transaction) => ({
              // Spread operator é usado para copiar todas as propriedades do objeto transaction para o novo objeto, e em seguida adiciona as propriedades accountName e categoryName.
              ...transaction,
              accountName: accountNameById.get(transaction.accountId) ?? 'Conta desconhecida',
              categoryName: categoryNameById.get(transaction.categoryId) ?? 'Categoria desconhecida',
            }),
          );

          this.transactions.set(transactionsWithNames);
        },
        error: (error) => {
          console.error('Error loading data:', error);
          this.errorMessage.set('Erro ao carregar os dados. Por favor, tente novamente mais tarde.');
        }
      });
  }

  protected goToPreviousPage(): void {
    if (this.currentPage() === 0) {
      return;
    }

    this.currentPage.set(this.currentPage() - 1);
    this.loadTransactions();
  }

  protected goToNextPage(): void {
    if (this.currentPage() >= this.totalPages() - 1) {
      return;
    }
    this.currentPage.set(this.currentPage() + 1);
    this.loadTransactions();
  }

 

}
