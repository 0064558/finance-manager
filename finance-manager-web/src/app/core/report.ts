import { inject, Service } from "@angular/core";
import { Observable } from "rxjs";
import { CashFlowResponse, CurrentBalance, ReportSummary } from "./report.models";
import { HttpClient } from "@angular/common/http";

@Service()
export class Report {

    private readonly http = inject(HttpClient);

    // Obtém o resumo do relatório financeiro para um intervalo de datas específico
    getSummary(startDate: string, endDate: string): Observable<ReportSummary> {
        // Realiza uma requisição GET para o endpoint de resumo do relatório, passando as datas como parâmetros de consulta
        return this.http.get<ReportSummary>(`/api/v1/reports/summary`, {
            params: {
                startDate,
                endDate,
            },
        });
    }

    // Obtém o saldo atual da conta
    getCurrentBalance(): Observable<CurrentBalance> {
        // Realiza uma requisição GET para o endpoint de saldo atual da conta
        return this.http.get<CurrentBalance>(`/api/v1/reports/balances`);
    }

    // Obtém o fluxo de caixa para um intervalo de datas específico
    getCashFlow(startDate: string, endDate: string): Observable<CashFlowResponse> {
        // Realiza uma requisição GET para o endpoint de fluxo de caixa, passando as datas como parâmetros de consulta
        return this.http.get<CashFlowResponse>(`/api/v1/reports/cash-flow`, {
            params: {
                startDate,
                endDate,
            },
        });
    }
}