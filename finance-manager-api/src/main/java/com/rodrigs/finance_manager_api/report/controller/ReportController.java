package com.rodrigs.finance_manager_api.report.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.report.dto.CurrentBalanceResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.ReportSummaryResponseDTO;
import com.rodrigs.finance_manager_api.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Resumos financeiros e saldos das contas do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(
            summary = "Consulta o resumo financeiro de um período",
            description = "Calcula o total de receitas, o total de despesas e o saldo líquido das transações do usuário autenticado. As datas são obrigatórias e o intervalo é inclusivo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo financeiro retornado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Datas ausentes, inválidas ou intervalo invertido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping("/summary")
    public ReportSummaryResponseDTO getSummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Data inicial do período, inclusiva", example = "2026-08-01", required = true)
            @RequestParam(value = "startDate", required = true) LocalDate startDate,
            @Parameter(description = "Data final do período, inclusiva", example = "2026-08-31", required = true)
            @RequestParam(value = "endDate", required = true) LocalDate endDate
            ) {
        return reportService.getSummary(authenticatedUser.id(), startDate, endDate);
    }

    @Operation(
            summary = "Consulta o saldo atual das contas",
            description = "Retorna o saldo atual de cada conta e o saldo total consolidado. O cálculo considera o saldo inicial e todas as receitas e despesas acumuladas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldos atuais retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping("/balances")
    public CurrentBalanceResponseDTO getCurrentBalance(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return reportService.getCurrentBalance(authenticatedUser.id());
    }
}
