package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record VendasDashboardResponse(
        PeriodoResponse periodo,
        String agrupamento,
        List<PontoSerieResponse> serie,
        List<CategoriaResponse> porCategoria,
        List<TipoEntregaResumoResponse> porTipoEntrega) {
}
