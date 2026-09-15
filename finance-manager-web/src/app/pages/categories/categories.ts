import {
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { finalize } from 'rxjs';
import { CategoryApi } from '../../core/categories';
import { Notifications } from '../../core/notifications';
import { Category, CreateCategoryRequest } from '../../core/category.models';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import type { TransactionType } from '../../core/transaction.models';
import { HttpErrorResponse } from '@angular/common/http';
import {
  LucidePencil,
  LucidePlus,
  LucideTags,
  LucideTrash2,
  LucideX,
} from '@lucide/angular';

@Component({
  imports: [
    ReactiveFormsModule,
    LucidePencil,
    LucidePlus,
    LucideTags,
    LucideTrash2,
    LucideX,
  ], // Isso é necessário pois estamos usando formulários reativos no Angular, e precisamos importar o módulo ReactiveFormsModule para habilitar essa funcionalidade no componente.
  selector: 'app-categories',
  styleUrl: './categories.css',
  templateUrl: './categories.html',
})
export class Categories implements OnInit {
  private readonly notifications = inject(Notifications);

  // Injeção do serviço CategoryApi para interagir com a API de categorias.
  private readonly categoriesApi = inject(CategoryApi);

  // Sinal para armazenar a lista de categorias do usuário autenticado.
  protected readonly categories = signal<Category[]>([]);

  // Sinal para indicar se os dados estão sendo carregados.
  protected readonly isLoading = signal<boolean>(false);

  // Sinal para armazenar mensagens de erro.
  protected readonly errorMessage = signal<string | null>(null);

  // Injeção do FormBuilder para criar formulários reativos.
  protected readonly formBuilder = inject(FormBuilder);

  // Sinal para controlar a abertura e fechamento do formulário de criação/edição de categorias.
  protected readonly isFormOpen = signal<boolean>(false);

  // Sinal para controlar o estado de envio do formulário.
  protected readonly isSubmitting = signal<boolean>(false);

  // Sinal para armazenar mensagens de erro relacionadas ao formulário.
  protected readonly formError = signal<string | null>(null);

  // Sinal para armazenar a categoria selecionada para edição.
  protected readonly selectedCategory = signal<Category | null>(null);

  // Computed sinal para verificar se uma categoria está sendo editada.
  protected readonly isEditing = computed(
    () => this.selectedCategory() !== null,
  );

  // Sinal para armazenar a categoria selecionada para exclusão.
  protected readonly categoryPendingDeletion = signal<Category | null>(null);

  // Sinal para controlar o estado de exclusão de uma categoria.
  protected readonly isDeleting = signal(false);

  // Sinal para armazenar mensagens de erro relacionadas à exclusão de categorias.
  protected readonly deleteError = signal<string | null>(null);

  // Cria um formulário reativo para criação/edição de categorias, com validações para os campos name e transactionType.
  protected readonly categoryForm = this.formBuilder.nonNullable.group({
    name: [
      '',
      [Validators.required, Validators.minLength(2), Validators.maxLength(80)],
    ],
    transactionType: [
      // O tipo de transação é definido como uma string vazia inicialmente, mas é tipado como TransactionType para garantir que apenas valores válidos sejam atribuídos a ele.
      '' as TransactionType | '',
      [Validators.required],
    ],
  });

  // Método para abrir o formulário de criação/edição de categorias.
  protected openCreateForm(): void {

    // Reseta a categoria selecionada para null, indicando que não há categoria selecionada para edição.
    this.selectedCategory.set(null);

    // seta o erro do formulário como null, indicando que não há erros no momento.
    this.formError.set(null);
    // Reseta o valor do campo name para uma string vazia.
    this.categoryForm.reset({
      name: '',
      transactionType: '',
    });
    // Abre o formulário de criação/edição de categorias.
    this.isFormOpen.set(true);
    this.selectedCategory.set(null); // Limpa a categoria selecionada para edição, garantindo que o formulário esteja pronto para criar uma nova categoria.
  }

  // Método para fechar o formulário de criação/edição de categorias.
  protected closeCategoryForm(): void {
    if (this.isSubmitting()) {
      return; // Se o formulário estiver sendo enviado, não faz nada.
    }
    // Fecha o formulário de criação/edição de categorias.
    this.isFormOpen.set(false);
    this.formError.set(null); // Limpa qualquer mensagem de erro do formulário.
    this.selectedCategory.set(null); // Limpa a categoria selecionada para edição.
  }

  // Método para abrir o formulário de edição de categorias, preenchendo os campos com os valores da categoria selecionada.
  protected openEditForm(category: Category): void {
    // Limpa qualquer mensagem de erro do formulário.
    this.formError.set(null);

    // Define a categoria selecionada para edição.
    this.selectedCategory.set(category);

    // Preenche o formulário com os valores da categoria selecionada.
    this.categoryForm.reset({
      name: category.name,
      transactionType: category.transactionType,
    });

    // Abrir o modal de criação/edição de categorias.
    this.isFormOpen.set(true);
  }

  // Método para abrir a confirmação de exclusão de categorias.
  protected openDeleteConfirmation(category: Category): void {
    // Limpa qualquer mensagem de erro relacionada à exclusão de categorias.
    this.deleteError.set(null);

    // Define a categoria selecionada para exclusão.
    this.categoryPendingDeletion.set(category);
  }

  // Método para fechar a confirmação de exclusão de categorias.
  protected closeDeleteConfirmation(): void {
    // Se uma exclusão estiver em andamento, não faz nada.
    if (this.isDeleting()) {
      return;
    }

    // Limpa a categoria selecionada para exclusão e qualquer mensagem de erro relacionada à exclusão.
    this.categoryPendingDeletion.set(null);
    this.deleteError.set(null);
  }

  // Método para confirmar a exclusão de uma categoria selecionada.
  protected confirmDelete(): void {
    // Obtém a categoria selecionada para exclusão.
    const category = this.categoryPendingDeletion();

    // Se não houver uma categoria selecionada ou se uma exclusão já estiver em andamento, não faz nada.
    if (!category || this.isDeleting()) {
      return;
    }

    // Define o sinal isDeleting como true para indicar que a exclusão está em andamento e limpa qualquer mensagem de erro anterior.
    this.isDeleting.set(true);
    this.deleteError.set(null);

    // Chama o método delete() do serviço CategoryApi para excluir a categoria selecionada.
    this.categoriesApi
      .delete(category.id)
      .pipe(
        finalize(() => this.isDeleting.set(false)),
      )
      .subscribe({
        next: () => {
          // Fecha a confirmação de exclusão e recarrega a lista de categorias após a exclusão bem-sucedida.
          this.categoryPendingDeletion.set(null);
          this.notifications.success('Categoria excluída com sucesso.');
          this.loadCategories();
        },
        // Trata erros de exclusão, como categoria associada a transações existentes ou outros erros genéricos.
        error: (error: HttpErrorResponse) => {
          if (
            error.status === 409 &&
            error.error?.code === 'CATEGORY_HAS_TRANSACTIONS'
          ) {
            this.deleteError.set(
              'Esta categoria possui transações e não pode ser excluída.',
            );
            return;
          }

          this.deleteError.set(
            'Não foi possível excluir a categoria. Tente novamente.',
          );
        },
      });
  }

  // Método para enviar o formulário de criação/edição de categorias.
  protected submitCategory(): void {
    // Verificar se categoryForm é inválido
    if (this.categoryForm.invalid) {
      // Se inválido, executar markAllAsTouched() e retornar
      this.categoryForm.markAllAsTouched();
      return;
    }

    // obter name e transactionType do categoryForm com getRawValue()
    const { name, transactionType } = this.categoryForm.getRawValue();

    // Verificar se transactionType é uma string vazia
    if (transactionType === '') {
      // Se for, definir formError com a mensagem apropriada e retornar
      this.formError.set('O tipo de transação é obrigatório.');
      return;
    }

    // Criar o objeto request do tipo CreateCategoryRequest com name e transactionType
    const request: CreateCategoryRequest = {
      name: name.trim(), // Remove espaços em branco no início e no final do nome da categoria.
      transactionType,
    };

    // Obter a categoria selecionada para edição, se houver.
    const selectedCategory = this.selectedCategory();

    // Se houver uma categoria selecionada, significa que estamos editando uma categoria existente, caso contrário, estamos criando uma nova categoria.
    // $ significa que estamos lidando com um Observable, que é uma forma de lidar com fluxos de dados assíncronos no Angular.
    const request$ = selectedCategory
      ? this.categoriesApi.update(selectedCategory.id, request)
      : this.categoriesApi.create(request);

    const action = selectedCategory ? 'atualizar' : 'criar';

    this.isSubmitting.set(true);
    this.formError.set(null);

    request$
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          // Fechar o formulário e recarregar a lista de categorias após a criação bem-sucedida.
          this.isFormOpen.set(false);
          this.selectedCategory.set(null);
          this.notifications.success(selectedCategory ? 'Categoria atualizada com sucesso.' : 'Categoria criada com sucesso.');
          this.loadCategories(); // Recarrega a lista de categorias após a criação bem-sucedida.
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 409 && error.error?.code === 'CATEGORY_ALREADY_EXISTS') {
            // Se a categoria já existir, exibir uma mensagem de erro específica.
            this.formError.set('Já existe uma categoria com esse nome e tipo de transação.');
            return;
          }

          if (error.status === 409 && error.error?.code === 'CATEGORY_HAS_TRANSACTIONS') {
            this.formError.set('Não é possível editar esta categoria, pois ela está associada a transações existentes.');
            return;
          }

          // Para outros erros, exibir uma mensagem de erro genérica.
          this.formError.set('Erro ao ' + action + ' categoria. Por favor, tente novamente.');
        }
      });
  }

  // Computed sinal para filtrar categorias de receitas (INCOME) a partir da lista de categorias.
  protected readonly incomeCategories = computed(() =>
    this.categories().filter(
      (category) => category.transactionType === 'INCOME',
    ),
  );

  // Computed sinal para filtrar categorias de despesas (EXPENSE) a partir da lista de categorias.
  protected readonly expenseCategories = computed(() =>
    this.categories().filter(
      (category) => category.transactionType === 'EXPENSE',
    ),
  );

  // Método do ciclo de vida do Angular que é chamado após a inicialização do componente.
  ngOnInit(): void {
    this.loadCategories();
  }

  // Método para carregar as categorias do usuário autenticado.
  protected loadCategories(): void {
    // Define o sinal isLoading como true para indicar que os dados estão sendo carregados.
    this.isLoading.set(true);
    // Limpa qualquer mensagem de erro anterior.
    this.errorMessage.set(null);

    this.categoriesApi
      // Chama o método getAll() do serviço CategoryApi para obter todas as categorias do usuário autenticado.
      .getAll()
      .pipe(finalize(() => this.isLoading.set(false))) // Pipe serve para aplicar operadores ao observable, como o finalize.
      .subscribe({ // Subscribe inicia o fluxo e define o que fazer com cada resultado.
        // next é executado quando a API entrega os dados.
        next: (categories) => {
          // Atualiza o sinal categories com os dados recebida da API.
          this.categories.set(categories);
        },
        // error é executado quando ocorre algum erro na requisição.
        error: () => {
          // Atualiza o sinal errorMessage com uma mensagem de erro amigável para o usuário.
          this.errorMessage.set('Erro ao carregar categorias. Por favor, tente novamente mais tarde.');
        }
      });
  }
}
