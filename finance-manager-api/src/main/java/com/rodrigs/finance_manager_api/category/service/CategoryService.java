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
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;


    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository, TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public CategoryResponseDTO createCategory(UUID authenticatedUserId, CreateCategoryRequestDTO requestDTO) {
        // busca o user pelo id
        User user = userRepository.findById(authenticatedUserId).orElseThrow();

        String normalizedName = requestDTO.name().trim();

        // verifica se a categoria ja existe
        if (categoryAlreadyExists(
                authenticatedUserId,
                requestDTO.transactionType(),
                normalizedName
        )) {
            throw new CategoryAlreadyExistsException();
        }

        Category category = new Category(
                user,
                normalizedName,
                requestDTO.transactionType()
        );

        // salva a categoria no banco
        category = categoryRepository.saveAndFlush(category);

        return toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAllCategories(UUID authenticatedUserId) {
        List<Category> categories = categoryRepository.findAllByUserIdOrderByTransactionTypeAscNameAsc(authenticatedUserId);

        List<CategoryResponseDTO> responseDTOS = new ArrayList<>();

        for (Category category : categories) {
            responseDTOS.add(toResponse(category));
        }

        return responseDTOS;
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO findCategoryById(UUID categoryId, UUID authenticatedUserId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, authenticatedUserId)
                .orElseThrow(CategoryNotFoundException::new);

        return toResponse(category);
    }

    @Transactional
    public CategoryResponseDTO updateCategory(
            UUID categoryId,
            UUID authenticatedUserId,
            UpdateCategoryRequestDTO requestDTO) {

        // busca a categoria
        Category category = categoryRepository.findByIdAndUserId(categoryId, authenticatedUserId)
                .orElseThrow(CategoryNotFoundException::new);

        // normaliza o nome
        String normalizedName = requestDTO.name().trim();

        boolean verify = categoryRepository.existsByUserIdAndTransactionTypeAndNameIgnoreCaseAndIdNot(
                authenticatedUserId,
                requestDTO.transactionType(),
                normalizedName,
                categoryId
        );
        boolean transactionTypeChanged = category.getTransactionType() != requestDTO.transactionType();

        // verifica duplicidade
        if (verify) {
            throw new CategoryAlreadyExistsException();
        } else if (transactionTypeChanged && transactionRepository.existsByCategory_IdAndUser_Id( // verifica se o tipo mudou e se existem transações vinculadas
                categoryId,
                authenticatedUserId
        )) {
            throw new CategoryHasTransactionsException();
        }

        // atualiza nome e transaction type
        category.setName(normalizedName);
        category.setTransactionType(requestDTO.transactionType());

        // retorna a resposta convertida em DTO
        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId, UUID authenticatedUserId) {
        // busca a categoria
        Category category = categoryRepository.findByIdAndUserId(categoryId, authenticatedUserId)
                .orElseThrow(CategoryNotFoundException::new);

        // verifica se existem transações vinculadas
        if (transactionRepository.existsByCategory_IdAndUser_Id(categoryId, authenticatedUserId)) {
            throw new CategoryHasTransactionsException();
        }

        // deleta a categoria
        categoryRepository.delete(category);
    }


    // metodo para converter a entidade categoria para CategoryResponseDTO
    private CategoryResponseDTO toResponse(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getTransactionType(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    // metodo para verificar se a categoria ja existe
    private boolean categoryAlreadyExists(
            UUID userId,
            TransactionType transactionType,
            String name
    ) {
        return categoryRepository
                .existsByUserIdAndTransactionTypeAndNameIgnoreCase(
                        userId,
                        transactionType,
                        name
                );
    }
}
