import {
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { finalize } from 'rxjs';
import { CategoryApi } from '../../core/categories';
import { Category } from '../../core/category.models';

@Component({
  imports: [],
  selector: 'app-categories',
  styleUrl: './categories.css',
  templateUrl: './categories.html',
})
export class Categories implements OnInit {

  // Injeção do serviço CategoryApi para interagir com a API de categorias.
  private readonly categoriesApi = inject(CategoryApi);

  // Sinal para armazenar a lista de categorias do usuário autenticado.
  protected readonly categories = signal<Category[]>([]);

  // Sinal para indicar se os dados estão sendo carregados.
  protected readonly isLoading = signal<boolean>(false);

  // Sinal para armazenar mensagens de erro.
  protected readonly errorMessage = signal<string | null>(null);

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
