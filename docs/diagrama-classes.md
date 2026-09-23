# Diagrama de classes — Finance Manager

Este documento representa as classes centrais encontradas no backend Spring Boot e os clientes de API do frontend Angular. Para manter a leitura clara, DTOs, exceções, configurações e componentes visuais foram resumidos nas fronteiras das camadas.

Os arquivos Mermaid sem marcações Markdown, prontos para importação no Mermaid Live Editor, estão em [`diagrama-dominio.mmd`](diagrama-dominio.mmd) e [`diagrama-arquitetura.mmd`](diagrama-arquitetura.mmd).

## Diagramas renderizados

### Modelo de domínio

[![Entidades e relacionamentos do Finance Manager](images/diagrama-de-dominio.png)](images/diagrama-de-dominio.png)

### Arquitetura da aplicação

[![Camadas e dependências do Finance Manager](images/diagrama-de-arquitetura.png)](images/diagrama-de-arquitetura.png)

## Fontes editáveis

### Modelo de domínio

```mermaid
classDiagram
    direction LR

    class User {
        -UUID id
        -String name
        -String email
        -String passwordHash
        -int onboardingVersion
        -OffsetDateTime createdAt
        -OffsetDateTime updatedAt
        +advanceOnboardingVersion(int requestedVersion) void
    }

    class FinancialAccount {
        -UUID id
        -User user
        -String name
        -AccountType type
        -BigDecimal initialBalance
        -OffsetDateTime createdAt
        -OffsetDateTime updatedAt
        +update(String name, AccountType type, BigDecimal initialBalance) void
    }

    class Category {
        -UUID id
        -User user
        -String name
        -TransactionType transactionType
        -OffsetDateTime createdAt
        -OffsetDateTime updatedAt
        +setName(String name) void
        +setTransactionType(TransactionType type) void
    }

    class Transaction {
        -UUID id
        -User user
        -FinancialAccount financialAccount
        -Category category
        -TransactionType type
        -BigDecimal amount
        -LocalDate occurredOn
        -String description
        -OffsetDateTime createdAt
        -OffsetDateTime updatedAt
        +update(FinancialAccount account, Category category, TransactionType type, BigDecimal amount, LocalDate occurredOn, String description) void
    }

    class AccountType {
        <<enumeration>>
        CHECKING
        SAVINGS
        CASH
    }

    class TransactionType {
        <<enumeration>>
        INCOME
        EXPENSE
    }

    User "1" <-- "0..*" FinancialAccount : pertence a
    User "1" <-- "0..*" Category : pertence a
    User "1" <-- "0..*" Transaction : registra
    FinancialAccount "1" <-- "0..*" Transaction : movimenta
    Category "1" <-- "0..*" Transaction : classifica
    FinancialAccount --> AccountType : usa
    Category --> TransactionType : aceita
    Transaction --> TransactionType : possui
```

### Camadas e dependências

```mermaid
classDiagram
    direction LR

    namespace AngularFrontend {
        class Auth {
            +login(request) Observable
            +register(request) Observable
            +getCurrentUser() Observable
            +logout() void
            +updateOnboardingVersion(request) Observable
        }
        class FinancialAccountApi {
            +getAll() Observable
            +create(request) Observable
            +update(accountId, request) Observable
            +delete(accountId) Observable
        }
        class CategoryApi {
            +getAll() Observable
            +create(request) Observable
            +update(categoryId, request) Observable
            +delete(categoryId) Observable
        }
        class TransactionApi {
            +getRecent(startDate, endDate, size) Observable
            +getAll(filters) Observable
            +create(request) Observable
            +update(transactionId, request) Observable
            +delete(transactionId) Observable
        }
        class Report {
            +getSummary(startDate, endDate) Observable
            +getCurrentBalance() Observable
            +getCashFlow(startDate, endDate) Observable
            +getCategoryBreakdown(startDate, endDate, type) Observable
            +getExpensesByCategory(startDate, endDate) Observable
        }
    }

    namespace Controllers {
        class AuthController
        class FinancialAccountController
        class CategoryController
        class TransactionController
        class ReportController
    }

    namespace Services {
        class UserService
        class FinancialAccountService
        class CategoryService
        class TransactionService
        class ReportService
        class JwtService
    }

    namespace Repositories {
        class UserRepository {
            <<interface>>
        }
        class FinancialAccountRepository {
            <<interface>>
        }
        class CategoryRepository {
            <<interface>>
        }
        class TransactionRepository {
            <<interface>>
        }
    }

    namespace Domain {
        class User
        class FinancialAccount
        class Category
        class Transaction
    }

    Auth ..> AuthController : HTTP /auth
    FinancialAccountApi ..> FinancialAccountController : HTTP /financial-accounts
    CategoryApi ..> CategoryController : HTTP /categories
    TransactionApi ..> TransactionController : HTTP /transactions
    Report ..> ReportController : HTTP /reports

    AuthController --> UserService
    FinancialAccountController --> FinancialAccountService
    CategoryController --> CategoryService
    TransactionController --> TransactionService
    ReportController --> ReportService

    UserService --> UserRepository
    UserService --> JwtService
    FinancialAccountService --> FinancialAccountRepository
    FinancialAccountService --> UserRepository
    FinancialAccountService --> TransactionRepository
    CategoryService --> CategoryRepository
    CategoryService --> UserRepository
    CategoryService --> TransactionRepository
    TransactionService --> TransactionRepository
    TransactionService --> UserRepository
    TransactionService --> FinancialAccountRepository
    TransactionService --> CategoryRepository
    ReportService --> TransactionRepository
    ReportService --> FinancialAccountRepository

    UserRepository ..> User : persiste
    FinancialAccountRepository ..> FinancialAccount : persiste
    CategoryRepository ..> Category : persiste
    TransactionRepository ..> Transaction : persiste
    JwtService ..> User : gera token para
```

## Leitura rápida

- `User` é o agregado proprietário dos dados financeiros.
- Cada `Transaction` pertence a exatamente um usuário, uma conta e uma categoria.
- O tipo da transação deve coincidir com o `TransactionType` da categoria.
- Controllers expõem a API REST, services aplicam regras de negócio e repositories cuidam da persistência.
- Os serviços Angular espelham os cinco grupos de endpoints do backend.
