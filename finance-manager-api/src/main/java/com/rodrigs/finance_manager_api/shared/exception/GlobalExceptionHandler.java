package com.rodrigs.finance_manager_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * Classe de tratamento global de excecoes.
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.CONFLICT,
                "Email already registered",
                "The provided email is already in use.",
                request
        );
        problemDetail.setProperty("code", "EMAIL_ALREADY_REGISTERED");

        return problemDetail;
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ProblemDetail handleCategoryAlreadyExists(
            CategoryAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.CONFLICT,
                "Category already exists",
                "A category with this name and transaction type already exists for the authenticated user.",
                request
        );
        problemDetail.setProperty("code", "CATEGORY_ALREADY_EXISTS");

        return problemDetail;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(
            CategoryNotFoundException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Category not found",
                "The requested category was not found.",
                request
        );
        problemDetail.setProperty("code", "CATEGORY_NOT_FOUND");

        return problemDetail;
    }

    @ExceptionHandler(CategoryHasTransactionsException.class)
    public ProblemDetail handleCategoryHasTransactions(
            CategoryHasTransactionsException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.CONFLICT,
                "Category has transactions",
                "The requested category has transactions and cannot be changed in this way.",
                request
        );
        problemDetail.setProperty("code", "CATEGORY_HAS_TRANSACTIONS");

        return problemDetail;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                "Email or password is invalid.",
                request
        );
        problemDetail.setProperty("code", "INVALID_CREDENTIALS");

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields are invalid.",
                request
        );
        problemDetail.setProperty("code", "VALIDATION_FAILED");
        problemDetail.setProperty("fields", exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldErrorDetail(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList());

        return problemDetail;
    }

    private ProblemDetail createProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", OffsetDateTime.now());

        return problemDetail;
    }

    // Trata a excecao de conta financeira nao encontrada.
    @ExceptionHandler(FinancialAccountNotFoundException.class)
    public ProblemDetail handleFinancialAccountNotFound(
            FinancialAccountNotFoundException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Financial account not found",
                "The requested financial account was not found.",
                request
        );
        problemDetail.setProperty("code", "FINANCIAL_ACCOUNT_NOT_FOUND");

        return problemDetail;
    }

    @ExceptionHandler(FinancialAccountHasTransactionsException.class)
    public ProblemDetail handleFinancialAccountHasTransactions(
            FinancialAccountHasTransactionsException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = createProblemDetail(
                HttpStatus.CONFLICT,
                "Financial account has transactions",
                "This financial account has transactions and cannot be changed in this way.",
                request
        );
        problemDetail.setProperty("code", "FINANCIAL_ACCOUNT_HAS_TRANSACTIONS");

        return problemDetail;
    }

    private record FieldErrorDetail(String field, String message) {
    }
}
