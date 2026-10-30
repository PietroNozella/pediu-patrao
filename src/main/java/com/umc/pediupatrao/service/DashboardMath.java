package com.umc.pediupatrao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DashboardMath {

    private static final BigDecimal CEM = new BigDecimal("100");

    private DashboardMath() {
    }

    public static BigDecimal arredondar(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal ticketMedio(BigDecimal faturamento, long finalizados) {
        if (finalizados <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = faturamento == null ? BigDecimal.ZERO : faturamento;
        return base.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentual(long parte, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(parte)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = parte == null ? BigDecimal.ZERO : parte;
        return base.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal base = atual == null ? BigDecimal.ZERO : atual;
        return base.subtract(anterior)
                .multiply(CEM)
                .divide(anterior, 2, RoundingMode.HALF_UP);
    }
}
