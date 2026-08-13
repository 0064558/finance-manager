package com.rodrigs.finance_manager_api.category.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.category.dto.CategoryResponseDTO;
import com.rodrigs.finance_manager_api.category.dto.CreateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.dto.UpdateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.service.CategoryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Gerenciamento de categorias financeiras do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(
            summary = "Cria uma categoria",
            description = "Cria uma categoria de receita ou despesa para o usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "409", description = "Categoria duplicada")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponseDTO createCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateCategoryRequestDTO request) {
        return categoryService.createCategory(authenticatedUser.id(), request);
    }

    @Operation(
            summary = "Lista as categorias",
            description = "Retorna somente as categorias pertencentes ao usuário autenticado, ordenadas por tipo e nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorias retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping
    public List<CategoryResponseDTO> findAllCategories(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return categoryService.findAllCategories(authenticatedUser.id());
    }

    @Operation(
            summary = "Busca uma categoria por ID",
            description = "Consulta uma categoria pertencente ao usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    })
    @GetMapping("/{categoryId}")
    public CategoryResponseDTO findCategoryById(
            @Parameter(
                    description = "ID da categoria",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return categoryService.findCategoryById(categoryId, authenticatedUser.id());
    }

    @Operation(
            summary = "Atualiza uma categoria",
            description = "Atualiza o nome e o tipo da categoria respeitando as regras de unicidade e histórico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "409", description = "Duplicidade ou categoria com histórico")
    })
    @PutMapping("/{categoryId}")
    public CategoryResponseDTO updateCategory(
            @Parameter(
                    description = "ID da categoria",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateCategoryRequestDTO request) {
        return categoryService.updateCategory(categoryId, authenticatedUser.id(), request);
    }

    @Operation(
            summary = "Exclui uma categoria",
            description = "Exclui uma categoria somente quando ela não possui transações vinculadas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria excluída"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "409", description = "Categoria possui transações")
    })
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @Parameter(
                    description = "ID da categoria",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        categoryService.deleteCategory(categoryId, authenticatedUser.id());
    }
}
