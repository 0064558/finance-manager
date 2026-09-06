import { HttpClient } from "@angular/common/http";
import { inject, Service } from "@angular/core";
import { Observable } from "rxjs";
import { Category, CreateCategoryRequest, UpdateCategoryRequest } from "./category.models";

@Service()
export class CategoryApi {
    // Injeção do HttpClient para realizar requisições HTTP à API.
    private readonly http = inject(HttpClient);

    // Endpoint base da API para categorias.
    private readonly endpoint = '/api/v1/categories';

    // Método para listar todas as contas categorias.
    getAll(): Observable<Category[]> {
        return this.http.get<Category[]>(this.endpoint);
    }

    // Método para criar uma nova categoria.
    create(request: CreateCategoryRequest): Observable<Category> {
        return this.http.post<Category>(this.endpoint, request);
    }

    // Método para atualizar uma categoria existente.
    update(categoryId: string, request: UpdateCategoryRequest): Observable<Category> {
        return this.http.put<Category>(`${this.endpoint}/${categoryId}`, request);
    }

    // Método para excluir uma categoria existente.
    delete(categoryId: string): Observable<void> {
        return this.http.delete<void>(`${this.endpoint}/${categoryId}`);
    }
}