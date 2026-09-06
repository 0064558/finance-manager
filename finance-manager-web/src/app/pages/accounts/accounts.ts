import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, finalize } from 'rxjs';

import { FinancialAccountApi } from '../../core/financial-accounts';
import {
  AccountType,
  FinancialAccount,
} from '../../core/financial-account.models';
import { Report } from '../../core/report';
import { CurrencyPipe } from '@angular/common';
import {
  LucideAlertCircle,
  LucideBanknote,
  LucideLandmark,
  LucidePencil,
  LucidePiggyBank,
  LucidePlus,
  LucideRefreshCw,
  LucideWalletCards,
  LucideX,
} from '@lucide/angular';

interface AccountViewModel extends FinancialAccount {
  currentBalance: number;
}

@Component({
  selector: 'app-accounts',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    // Ícones do Lucide para representar diferentes tipos de contas e ações no componente.
    LucideAlertCircle,
    LucideBanknote,
    LucideLandmark,
    LucidePiggyBank,
    LucidePlus,
    LucideRefreshCw,
    LucideWalletCards,
    LucideX,
    LucidePencil,
  ],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css',
})
// Accounts é um componente Angular que exibe uma lista de contas financeiras, 
// incluindo informações como nome, tipo, saldo inicial e saldo atual.
export class Accounts implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  // Injeção de dependências para interagir com a API de contas financeiras e gerar relatórios.
  private readonly accountApi = inject(FinancialAccountApi);
  private readonly report = inject(Report);

  // Sinais para gerenciar o estado do componente, incluindo a lista de contas, o estado de carregamento e mensagens de erro.
  protected readonly accounts = signal<AccountViewModel[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly errorMessage = signal('');
  protected readonly isFormOpen = signal(false);
  protected readonly isSubmitting = signal(false);
  protected readonly formError = signal('');
  protected readonly totalBalance = computed(() =>
    this.accounts().reduce((total, account) => total + account.currentBalance, 0),
  );

  // Sinais para gerenciar o estado da conta selecionada para edição e se o formulário de edição está aberto.
  protected readonly selectedAccount = signal<AccountViewModel | null>(null);
  protected readonly isEditing = computed(() => this.selectedAccount() !== null);

  // O método ngOnInit é chamado quando o componente é inicializado, e aqui ele chama o método para carregar as contas financeiras.
  ngOnInit(): void {
    this.loadAccounts();
  }

  // Método protegido para carregar as contas financeiras e seus saldos atuais, 
  // lidando com o estado de carregamento e mensagens de erro conforme necessário.
  protected loadAccounts(): void {
    // Define o estado de carregamento como verdadeiro e limpa qualquer mensagem de erro anterior.
    this.isLoading.set(true);
    this.errorMessage.set('');

    // Usa forkJoin para fazer chamadas paralelas à API de contas financeiras e ao serviço de relatórios    
    // combinando os resultados em um único fluxo de dados.
    forkJoin({
      accounts: this.accountApi.getAll(),
      balances: this.report.getCurrentBalance(),
    })
      // O operador finalize é usado para garantir que o estado de carregamento seja definido como falso, 
      // independentemente de a chamada ter sido bem-sucedida ou ter falhado.
      .pipe(finalize(() => this.isLoading.set(false)))
      // O método subscribe é usado para lidar com os resultados da chamada à API, atualizando a lista de contas ou definindo uma mensagem de erro conforme necessário.
      .subscribe({
        // Se a chamada for bem-sucedida, os saldos das contas são combinados com as informações das contas e armazenados no sinal accounts.
        next: ({ accounts, balances }) => {
          const balanceByAccountId = new Map(
            balances.accounts.map((account) => [
              account.accountId,
              account.balance,
            ]),
          );

          // Atualiza o sinal accounts com as contas financeiras e seus saldos atuais.
          this.accounts.set(
            // Mapeia cada conta financeira para um objeto AccountViewModel, incluindo o saldo atual obtido do relatório de saldos.
            accounts.map((account) => ({
              ...account,
              currentBalance:
                balanceByAccountId.get(account.id) ?? account.initialBalance,
            })),
          );
        },
        // Se houver um erro ao carregar as contas, uma mensagem de erro é definida no sinal errorMessage.
        error: () => {
          this.errorMessage.set(
            'Não foi possível carregar suas contas.',
          );
        },
      });
  }

  // Método protegido para obter o rótulo legível do tipo de conta financeira, mapeando os tipos internos para strings amigáveis.
  protected accountTypeLabel(type: FinancialAccount['type']): string {
    const labels: Record<FinancialAccount['type'], string> = {
      CASH: 'Dinheiro',
      CHECKING: 'Conta corrente',
      SAVINGS: 'Poupança',
    };

    return labels[type];
  }

  // Formulário reativo para criar uma nova conta financeira, incluindo validação de campos como nome, tipo e saldo inicial.
  protected readonly accountForm = this.formBuilder.nonNullable.group({
    name: [
      '',
      [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
    ],
    type: ['CHECKING' as AccountType, [Validators.required]],
    initialBalance: [
      0,
      [
        Validators.required,
        Validators.min(-99999999999999999.99),
        Validators.max(99999999999999999.99),
      ],
    ],
  });

  // Métodos protegidos para abrir, editar e fechar o formulário de criação de conta, 
  // bem como para enviar os dados do formulário para a API.

  protected openCreateForm(): void {
    this.selectedAccount.set(null);
    this.formError.set('');

    this.accountForm.reset({
      name: '',
      type: 'CHECKING',
      initialBalance: 0,
    });

    this.isFormOpen.set(true);
  }

  // Método protegido para abrir o formulário de edição de uma conta existente, 
  // preenchendo os campos do formulário com os dados da conta selecionada.
  protected openEditForm(account: AccountViewModel): void {
    this.selectedAccount.set(account);
    this.formError.set('');

    this.accountForm.reset({
      name: account.name,
      type: account.type,
      initialBalance: account.initialBalance,
    });

    this.isFormOpen.set(true);
  }

  protected closeAccountForm(): void {
  if (this.isSubmitting()) {
    return;
  }

  this.isFormOpen.set(false);
  this.selectedAccount.set(null);
  this.formError.set('');
}

  // Método protegido para enviar os dados do formulário de criação de conta para a API,
  // lidando com o estado de envio e mensagens de erro conforme necessário.
  protected submitAccount(): void {
    if (this.accountForm.invalid) {
      this.accountForm.markAllAsTouched();
      return;
    }

    const request = this.accountForm.getRawValue();
    const selectedAccount = this.selectedAccount();

    // Determina se a operação é de criação ou atualização com base na presença de uma conta selecionada.
    const request$ = selectedAccount
      ? this.accountApi.update(selectedAccount.id, request)
      : this.accountApi.create(request);

    this.isSubmitting.set(true);
    this.formError.set('');

    request$
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.isFormOpen.set(false);
          this.selectedAccount.set(null);
          this.loadAccounts();
        },
        error: () => {
          this.formError.set(
            selectedAccount
              ? 'Não foi possível atualizar esta conta.'
              : 'Não foi possível criar esta conta.',
          );
        },
      });
  }
}
