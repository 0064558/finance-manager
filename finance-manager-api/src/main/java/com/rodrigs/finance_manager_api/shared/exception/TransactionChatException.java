package com.rodrigs.finance_manager_api.shared.exception;

public class TransactionChatException extends RuntimeException {
    private final Reason reason;

    public TransactionChatException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        ENTRY_NOT_FOUND, DRAFT_UNAVAILABLE, REQUEST_CONFLICT, CONTEXT_LIMIT, INVALID_TIME_ZONE, HISTORY_CHANGED
    }
}
