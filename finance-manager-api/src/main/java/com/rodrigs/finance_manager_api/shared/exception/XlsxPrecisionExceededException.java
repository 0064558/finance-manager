package com.rodrigs.finance_manager_api.shared.exception;

public class XlsxPrecisionExceededException extends RuntimeException {
    public XlsxPrecisionExceededException() {
        super("Excel cannot preserve cents for one or more amounts or totals. Narrow the filters or export as CSV.");
    }
}
