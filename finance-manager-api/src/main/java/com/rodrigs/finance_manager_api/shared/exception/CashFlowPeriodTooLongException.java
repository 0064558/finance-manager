package com.rodrigs.finance_manager_api.shared.exception;

public class CashFlowPeriodTooLongException extends RuntimeException {
    public CashFlowPeriodTooLongException(int maxDays) {
        super("O fluxo de caixa aceita um período de até " + maxDays + " dias, incluindo as duas datas.");
    }
}
