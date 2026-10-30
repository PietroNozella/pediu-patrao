package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record TipoEntregaResumoResponse(
        String tipo,
        long pedidos,
        BigDecimal faturamento) {
}
