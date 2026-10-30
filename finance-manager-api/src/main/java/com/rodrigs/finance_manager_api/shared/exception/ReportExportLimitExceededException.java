package com.rodrigs.finance_manager_api.shared.exception;

public class ReportExportLimitExceededException extends RuntimeException {
    public ReportExportLimitExceededException() {
        super("An export must contain at most 366 inclusive days and 5000 transactions. Narrow the filters.");
    }
}
