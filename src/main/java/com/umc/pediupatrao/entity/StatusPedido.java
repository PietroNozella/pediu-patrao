package com.umc.pediupatrao.entity;

public enum StatusPedido {
    RECEBIDO,
    EM_PREPARACAO,
    PRONTO,
    SAIU_PARA_ENTREGA,
    RETIRADO,
    FINALIZADO,
    CANCELADO;

    public boolean permiteTransicaoPara(StatusPedido proximoStatus) {
        if (proximoStatus == null) {
            return false;
        }

        return switch (this) {
            case RECEBIDO -> proximoStatus == EM_PREPARACAO;
            case EM_PREPARACAO -> proximoStatus == PRONTO;
            case PRONTO -> proximoStatus == SAIU_PARA_ENTREGA || proximoStatus == RETIRADO;
            case SAIU_PARA_ENTREGA, RETIRADO -> proximoStatus == FINALIZADO;
            case FINALIZADO, CANCELADO -> false;
        };
    }

    public boolean registraSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }
}
