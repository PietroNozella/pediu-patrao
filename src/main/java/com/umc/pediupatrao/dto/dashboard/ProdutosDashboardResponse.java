package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record ProdutosDashboardResponse(
        PeriodoResponse periodo,
        String ordenarPor,
        List<ProdutoVendidoResponse> itens) {
}
