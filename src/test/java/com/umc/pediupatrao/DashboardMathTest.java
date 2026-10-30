package com.umc.pediupatrao;

import com.umc.pediupatrao.service.DashboardMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardMathTest {

    @Test
    void calculaTicketMedioComDuasCasas() {
        assertEquals(new BigDecimal("33.33"),
                DashboardMath.ticketMedio(new BigDecimal("100.00"), 3));
    }

    @Test
    void ticketMedioSemPedidosFinalizadosEhZero() {
        assertEquals(new BigDecimal("0.00"),
                DashboardMath.ticketMedio(new BigDecimal("100.00"), 0));
    }

    @Test
    void calculaPercentual() {
        assertEquals(new BigDecimal("12.50"), DashboardMath.percentual(25, 200));
    }

    @Test
    void percentualSemTotalEhZero() {
        assertEquals(new BigDecimal("0.00"), DashboardMath.percentual(10, 0));
    }

    @Test
    void calculaVariacaoPercentual() {
        assertEquals(new BigDecimal("20.00"),
                DashboardMath.variacaoPercentual(new BigDecimal("120.00"), new BigDecimal("100.00")));
    }

    @Test
    void variacaoSemBaseAnteriorEhNula() {
        assertNull(DashboardMath.variacaoPercentual(new BigDecimal("120.00"), BigDecimal.ZERO));
        assertNull(DashboardMath.variacaoPercentual(new BigDecimal("120.00"), null));
    }

    @Test
    void arredondaValoresNulosParaZero() {
        assertEquals(new BigDecimal("0.00"), DashboardMath.arredondar(null));
        assertEquals(new BigDecimal("10.56"), DashboardMath.arredondar(new BigDecimal("10.555")));
    }
}
