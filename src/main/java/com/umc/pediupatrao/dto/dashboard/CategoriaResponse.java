package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record CategoriaResponse(
        String categoria,
        long quantidade,
        BigDecimal receita) {
}
