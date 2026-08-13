package com.rodrigs.finance_manager_api.category.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.category.dto.CategoryResponseDTO;
import com.rodrigs.finance_manager_api.category.dto.CreateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.dto.UpdateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponseDTO createCategory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateCategoryRequestDTO request) {
        return categoryService.createCategory(authenticatedUser.id(), request);
    }

    @GetMapping
    public List<CategoryResponseDTO> findAllCategories(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return categoryService.findAllCategories(authenticatedUser.id());
    }

    @GetMapping("/{categoryId}")
    public CategoryResponseDTO findCategoryById(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return categoryService.findCategoryById(categoryId, authenticatedUser.id());
    }

    @PutMapping("/{categoryId}")
    public CategoryResponseDTO updateCategory(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateCategoryRequestDTO request) {
        return categoryService.updateCategory(categoryId, authenticatedUser.id(), request);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        categoryService.deleteCategory(categoryId, authenticatedUser.id());
    }

}
