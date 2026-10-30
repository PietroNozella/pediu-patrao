package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ComparacaoResponse(
        BigDecimal faturamentoVariacaoPercentual,
        BigDecimal pedidosVariacaoPercentual) {
}
