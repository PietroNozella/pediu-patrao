package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AplicarDescontoRequest {
    @NotNull
    @DecimalMin(value = "0.01", message = "Percentual deve ser positivo")
    @DecimalMax(value = "20.00", message = "Desconto limitado a 20%")
    private BigDecimal percentual;

    public BigDecimal getPercentual() {
        return percentual;
    }

    public void setPercentual(BigDecimal percentual) {
        this.percentual = percentual;
    }
}
