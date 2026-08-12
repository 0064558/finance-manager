package com.rodrigs.finance_manager_api.category.service;

import com.rodrigs.finance_manager_api.category.dto.CategoryResponseDTO;
import com.rodrigs.finance_manager_api.category.dto.CreateCategoryRequestDTO;
import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;


    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
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
