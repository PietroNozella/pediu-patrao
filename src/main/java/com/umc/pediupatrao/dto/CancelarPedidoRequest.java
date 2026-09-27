package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;

public class CancelarPedidoRequest {
    @NotBlank(message = "Justificativa obrigatoria")
    private String justificativa;

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
