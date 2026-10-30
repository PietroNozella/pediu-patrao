package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.FiltrosDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.PaginaResponse;
import com.umc.pediupatrao.dto.dashboard.PedidoResumoResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutosDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.VendasDashboardResponse;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public ResumoDashboardResponse resumo(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return dashboardService.resumo(inicio, fim);
    }

    @GetMapping("/vendas")
    public VendasDashboardResponse vendas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "dia") String agrupamento,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.vendas(inicio, fim, agrupamento, status, tipoEntrega);
    }

    @GetMapping("/produtos")
    public ProdutosDashboardResponse produtos(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "quantidade") String ordenarPor,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String tipoProduto,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega) {
        return dashboardService.produtos(inicio, fim, ordenarPor, limit, tipoProduto,
                status, tipoEntrega);
    }

    @GetMapping("/pedidos")
    public PaginaResponse<PedidoResumoResponse> pedidos(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return dashboardService.pedidos(inicio, fim, status, tipoEntrega, page, size);
    }

    @GetMapping("/filtros")
    public FiltrosDashboardResponse filtros() {
        return dashboardService.filtros();
    }
}
