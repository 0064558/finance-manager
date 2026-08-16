package com.rodrigs.finance_manager_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Email already registered",
                "The provided email is already in use.", "EMAIL_ALREADY_REGISTERED", request);
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ProblemDetail handleCategoryAlreadyExists(CategoryAlreadyExistsException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Category already exists",
                "A category with this name and transaction type already exists for the authenticated user.",
                "CATEGORY_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Category not found",
                "The requested category was not found.", "CATEGORY_NOT_FOUND", request);
    }

    @ExceptionHandler(CategoryHasTransactionsException.class)
    public ProblemDetail handleCategoryHasTransactions(CategoryHasTransactionsException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Category has transactions",
                "The requested category has transactions and cannot be changed in this way.",
                "CATEGORY_HAS_TRANSACTIONS", request);
    }

    @ExceptionHandler(FinancialAccountNotFoundException.class)
    public ProblemDetail handleFinancialAccountNotFound(FinancialAccountNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Financial account not found",
                "The requested financial account was not found.", "FINANCIAL_ACCOUNT_NOT_FOUND", request);
    }

    @ExceptionHandler(FinancialAccountHasTransactionsException.class)
    public ProblemDetail handleFinancialAccountHasTransactions(FinancialAccountHasTransactionsException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Financial account has transactions",
                "This financial account has transactions and cannot be changed in this way.",
                "FINANCIAL_ACCOUNT_HAS_TRANSACTIONS", request);
    }

    @ExceptionHandler(TransactionTypeMismatchException.class)
    public ProblemDetail handleTransactionTypeMismatch(TransactionTypeMismatchException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Transaction type mismatch",
                "The transaction type must be the same as the category type.",
                "TRANSACTION_TYPE_MISMATCH", request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "User not found",
                "The authenticated user was not found.", "USER_NOT_FOUND", request);
    }

    @ExceptionHandler(InvalidTransactionDateRangeException.class)
    public ProblemDetail handleInvalidTransactionDateRange(InvalidTransactionDateRangeException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid transaction date range",
                "The start date must be before or equal to the end date.",
                "INVALID_TRANSACTION_DATE_RANGE", request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleTransactionNotFound(TransactionNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Transaction not found",
                "The requested transaction was not found.", "TRANSACTION_NOT_FOUND", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials",
                "Email or password is invalid.", "INVALID_CREDENTIALS", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request fields are invalid.", "VALIDATION_FAILED", request);
        problem.setProperty("fields", fieldErrors(exception));
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid.", "VALIDATION_FAILED", request);
        problem.setProperty("fields", exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "One or more request parameters have an invalid format.",
                "INVALID_PARAMETER", request);
        problem.setProperty("fields", List.of(new FieldErrorDetail(exception.getName(),
                "The value has an invalid format.")));
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingRequestParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing request parameter",
                "A required request parameter is missing.", "MISSING_PARAMETER", request);
        problem.setProperty("fields", List.of(new FieldErrorDetail(exception.getParameterName(), "The parameter is required.")));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request body is missing or has an invalid format.",
                "MALFORMED_REQUEST_BODY", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data",
                "The request could not be completed because it conflicts with existing data.",
                "DATA_INTEGRITY_VIOLATION", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                "The HTTP method is not supported for this resource.", "METHOD_NOT_ALLOWED", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type",
                "The request content type is not supported.", "UNSUPPORTED_MEDIA_TYPE", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleResourceNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found",
                "The requested resource was not found.", "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        String traceId = problemDetailFactory.traceId(request);
        LOGGER.error("Unexpected error while processing {} {} (traceId={})",
                request.getMethod(), request.getRequestURI(), traceId, exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Use the traceId to contact support.",
                "INTERNAL_SERVER_ERROR", request);
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request
    ) {
        return problemDetailFactory.create(status, title, detail, code, request);
    }

    private List<FieldErrorDetail> fieldErrors(MethodArgumentNotValidException exception) {
        Map<String, String> errorsByField = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errorsByField.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return errorsByField.entrySet().stream()
                .map(entry -> new FieldErrorDetail(entry.getKey(), entry.getValue()))
                .toList();
    }

    private record FieldErrorDetail(String field, String message) {
    }
}
