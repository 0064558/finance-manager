package com.rodrigs.finance_manager_api.category.service;

import com.rodrigs.finance_manager_api.category.dto.CategoryResponseDTO;
import com.rodrigs.finance_manager_api.category.dto.CreateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.dto.UpdateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.CategoryAlreadyExistsException;
import com.rodrigs.finance_manager_api.shared.exception.CategoryHasTransactionsException;
import com.rodrigs.finance_manager_api.shared.exception.CategoryNotFoundException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(
                categoryRepository,
                userRepository,
                transactionRepository
        );
    }

    @Test
    void shouldRejectDuplicateCategory() {
        // gera um ID de usuário aleatório
        UUID userId = UUID.randomUUID();

        // cria um usuário fictício
        User user = new User(
                "Rodrigo",
                "rodrigo@email.com",
                "hashed-password"
        );

        // cria uma solicitação de criação de categoria com nome duplicado
        CreateCategoryRequestDTO request = new CreateCategoryRequestDTO(
                "  Alimentação  ",
                TransactionType.EXPENSE
        );

        // simula o comportamento do repositório de usuários para retornar o usuário fictício
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // simula o comportamento do repositório de categorias para indicar que a categoria já existe
        when(categoryRepository
                .existsByUserIdAndTransactionTypeAndNameIgnoreCase(
                        userId,
                        TransactionType.EXPENSE,
                        "Alimentação"
                ))
                .thenReturn(true);

        // verifica se a exceção CategoryAlreadyExistsException é lançada ao tentar criar uma categoria duplicada
        assertThatThrownBy(() ->
                categoryService.createCategory(userId, request)
        ).isInstanceOf(CategoryAlreadyExistsException.class);

        // verifica se o método saveAndFlush do repositório de categorias não foi chamado
        verify(categoryRepository, never())
                .saveAndFlush(any());
    }

    @Test
    void shouldCreateCategoryForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        CreateCategoryRequestDTO request = new CreateCategoryRequestDTO(
                "  Salário  ",
                TransactionType.INCOME
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCase(
                userId,
                TransactionType.INCOME,
                "Salário"
        )).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDTO response = categoryService.createCategory(userId, request);

        var categoryCaptor = forClass(Category.class);
        verify(categoryRepository).saveAndFlush(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();
        assertThat(savedCategory.getUser()).isEqualTo(user);
        assertThat(savedCategory.getName()).isEqualTo("Salário");
        assertThat(savedCategory.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(response.name()).isEqualTo("Salário");
    }

    @Test
    void shouldListCategoriesForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category firstCategory = category(user, "Alimentação", TransactionType.EXPENSE);
        Category secondCategory = category(user, "Salário", TransactionType.INCOME);

        when(categoryRepository.findAllByUserIdOrderByTransactionTypeAscNameAsc(userId))
                .thenReturn(List.of(firstCategory, secondCategory));

        List<CategoryResponseDTO> response = categoryService.findAllCategories(userId);

        assertThat(response)
                .extracting(CategoryResponseDTO::name)
                .containsExactly("Alimentação", "Salário");
        verify(categoryRepository).findAllByUserIdOrderByTransactionTypeAscNameAsc(userId);
    }

    @Test
    void shouldFindCategoryOwnedByAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Alimentação", TransactionType.EXPENSE);

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));

        CategoryResponseDTO response = categoryService.findCategoryById(category.getId(), userId);

        assertThat(response.id()).isEqualTo(category.getId());
        assertThat(response.name()).isEqualTo("Alimentação");
        assertThat(response.transactionType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void shouldUpdateCategoryWhenNameIsUnique() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Alimentação", TransactionType.EXPENSE);
        UpdateCategoryRequestDTO request = new UpdateCategoryRequestDTO(
                "  Alimentação atualizada  ",
                TransactionType.EXPENSE
        );

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCaseAndIdNot(
                userId,
                TransactionType.EXPENSE,
                "Alimentação atualizada",
                category.getId()
        )).thenReturn(false);

        CategoryResponseDTO response = categoryService.updateCategory(
                category.getId(), userId, request
        );

        assertThat(response.name()).isEqualTo("Alimentação atualizada");
        assertThat(response.transactionType()).isEqualTo(TransactionType.EXPENSE);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldAllowChangingTypeWhenCategoryHasNoTransactions() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Ajuste", TransactionType.EXPENSE);
        UpdateCategoryRequestDTO request = new UpdateCategoryRequestDTO(
                "Ajuste",
                TransactionType.INCOME
        );

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCaseAndIdNot(
                userId,
                TransactionType.INCOME,
                "Ajuste",
                category.getId()
        )).thenReturn(false);
        when(transactionRepository.existsByCategory_IdAndUser_Id(category.getId(), userId))
                .thenReturn(false);

        CategoryResponseDTO response = categoryService.updateCategory(
                category.getId(), userId, request
        );

        assertThat(response.transactionType()).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void shouldRejectDuplicateCategoryNameOnUpdate() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Transporte", TransactionType.EXPENSE);
        UpdateCategoryRequestDTO request = new UpdateCategoryRequestDTO(
                "Alimentação",
                TransactionType.EXPENSE
        );

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCaseAndIdNot(
                userId,
                TransactionType.EXPENSE,
                "Alimentação",
                category.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(
                category.getId(), userId, request
        )).isInstanceOf(CategoryAlreadyExistsException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldRejectTypeChangeWhenCategoryHasTransactions() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Ajuste", TransactionType.EXPENSE);
        UpdateCategoryRequestDTO request = new UpdateCategoryRequestDTO(
                "Ajuste",
                TransactionType.INCOME
        );

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCaseAndIdNot(
                userId,
                TransactionType.INCOME,
                "Ajuste",
                category.getId()
        )).thenReturn(false);
        when(transactionRepository.existsByCategory_IdAndUser_Id(category.getId(), userId))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(
                category.getId(), userId, request
        )).isInstanceOf(CategoryHasTransactionsException.class);

        assertThat(category.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void shouldDeleteCategoryWhenItHasNoTransactions() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Transporte", TransactionType.EXPENSE);

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategory_IdAndUser_Id(category.getId(), userId))
                .thenReturn(false);

        categoryService.deleteCategory(category.getId(), userId);

        verify(categoryRepository).delete(category);
    }

    @Test
    void shouldRejectDeleteWhenCategoryHasTransactions() {
        UUID userId = UUID.randomUUID();
        User user = userWithId();
        Category category = category(user, "Transporte", TransactionType.EXPENSE);

        when(categoryRepository.findByIdAndUserId(category.getId(), userId))
                .thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategory_IdAndUser_Id(category.getId(), userId))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(
                category.getId(), userId
        )).isInstanceOf(CategoryHasTransactionsException.class);

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void shouldNotUpdateUnknownCategory() {
        UUID categoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateCategoryRequestDTO request = new UpdateCategoryRequestDTO(
                "Nova categoria",
                TransactionType.EXPENSE
        );

        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                categoryId, userId, request
        )).isInstanceOf(CategoryNotFoundException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldNotDeleteUnknownCategory() {
        UUID categoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId, userId))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository, never()).delete(any(Category.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldNotFindCategoryOwnedByAnotherUser() {
        UUID categoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // simula o comportamento do repositório de categorias para indicar que a categoria não pertence ao usuário
        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.empty());

        // verifica se a exceção CategoryNotFoundException é lançada ao tentar buscar uma categoria que não pertence ao usuário
        assertThatThrownBy(() ->
                categoryService.findCategoryById(categoryId, userId)
        ).isInstanceOf(CategoryNotFoundException.class);

        // verifica se o método findByIdAndUserId do repositório de categorias foi chamado com os parâmetros corretos
        verify(categoryRepository)
                .findByIdAndUserId(categoryId, userId);
    }

    private Category category(User owner, String name, TransactionType transactionType) {
        Category category = new Category(owner, name, transactionType);
        setId(category, UUID.randomUUID());
        return category;
    }

    private User userWithId() {
        User user = new User("Rodrigo", "rodrigo@email.com", "hashed-password");
        setId(user, UUID.randomUUID());
        return user;
    }

    private void setId(Object entity, UUID id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not prepare test entity", exception);
        }
    }
}
