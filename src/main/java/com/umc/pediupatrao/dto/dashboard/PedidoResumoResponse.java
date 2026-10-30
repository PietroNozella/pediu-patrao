package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record PedidoResumoResponse(
        String id,
        String clienteId,
        String clienteNome,
        String status,
        String tipoEntrega,
        BigDecimal valorTotal,
        Instant dataHoraEntrada,
        int quantidadeItens,
        String responsavelRegistro) {
}
