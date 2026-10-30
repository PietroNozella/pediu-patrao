package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record PontoSerieResponse(
        String data,
        String rotulo,
        long pedidos,
        BigDecimal faturamento) {
}
