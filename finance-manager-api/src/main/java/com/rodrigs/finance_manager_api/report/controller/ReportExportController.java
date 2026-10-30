package com.rodrigs.finance_manager_api.report.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.service.CsvReportRenderer;
import com.rodrigs.finance_manager_api.report.service.ReportExportQueryService;
import com.rodrigs.finance_manager_api.report.service.XlsxReportRenderer;
import com.rodrigs.finance_manager_api.report.service.PdfReportRenderer;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportExportController {
    public enum ExportFormat { CSV, XLSX, PDF }

    private final ReportExportQueryService queryService;
    private final CsvReportRenderer csvRenderer;
    private final XlsxReportRenderer xlsxRenderer;
    private final PdfReportRenderer pdfRenderer;

    public ReportExportController(ReportExportQueryService queryService, CsvReportRenderer csvRenderer,
                                  XlsxReportRenderer xlsxRenderer, PdfReportRenderer pdfRenderer) {
        this.queryService = queryService;
        this.csvRenderer = csvRenderer;
        this.xlsxRenderer = xlsxRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping("/export")
    @Operation(summary = "Exporta movimentações em CSV, Excel ou PDF",
            description = "Exporta todos os resultados filtrados do usuário autenticado, em ordem de data e ID. "
                    + "Datas inclusivas; máximo de 366 dias e 5000 transações. CSV UTF-8 com BOM e separador ponto e vírgula. "
                    + "CSV não inclui resumo; XLSX contém abas Resumo e Transações, com datas e valores tipados. "
                    + "PDF contém resumo e tabela em A4 paisagem, com páginas numeradas. "
                    + "XLSX rejeita valores ou totais que perderiam centavos no Excel (XLSX_PRECISION_EXCEEDED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV, XLSX ou PDF para download",
                    content = {
                            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    schema = @Schema(type = "string", format = "binary"))}),
            @ApiResponse(responseCode = "400", description = "Parâmetro inválido ou limite excedido",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "Conta ou categoria não encontrada para o usuário",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "500", description = "Falha inesperada na geração",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("format") ExportFormat format,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate,
            @RequestParam(value = "type", required = false) TransactionType type,
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId) throws IOException {
        var data = queryService.load(user.id(), new ReportExportFilter(startDate, endDate, type, accountId, categoryId));
        // Rendering happens after the transactional query service returns a detached snapshot.
        byte[] body = switch (format) {
            case CSV -> csvRenderer.render(data);
            case XLSX -> xlsxRenderer.render(data);
            case PDF -> pdfRenderer.render(data);
        };
        String contentType = switch (format) {
            case CSV -> "text/csv; charset=UTF-8";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PDF -> "application/pdf";
        };
        String filename = "finance-manager_%s_%s.%s".formatted(startDate, endDate, format.name().toLowerCase(Locale.ROOT));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
