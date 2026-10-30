package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ProdutoVendidoResponse(
        String produtoId,
        String nome,
        long quantidade,
        BigDecimal receita,
        BigDecimal participacaoQuantidadePercentual,
        BigDecimal participacaoReceitaPercentual) {
}
