package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record FiltrosDashboardResponse(
        List<String> status,
        List<String> tiposEntrega,
        List<String> tiposProduto) {
}
