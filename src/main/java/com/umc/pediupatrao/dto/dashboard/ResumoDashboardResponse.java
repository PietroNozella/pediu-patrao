package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record ResumoDashboardResponse(
        PeriodoResponse periodo,
        BigDecimal faturamento,
        long pedidos,
        long finalizados,
        BigDecimal ticketMedio,
        long cancelados,
        BigDecimal taxaCancelamento,
        BigDecimal descontos,
        long emAndamento,
        BigDecimal tempoMedioMinutos,
        ComparacaoResponse comparacao,
        List<StatusResumoResponse> porStatus) {
}
