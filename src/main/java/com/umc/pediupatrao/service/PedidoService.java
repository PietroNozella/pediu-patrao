package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public Pedido criarPedido(Pedido pedido) {
        validarCancelamentoIndevido(pedido.getStatus());
        if (pedido.getStatus() == null || pedido.getStatus().isBlank()) {
            pedido.setStatus("NOVO");
        }
        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido atualizarStatus(String id, String status) {
        validarCancelamentoIndevido(status);
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        pedido.setStatus(status);
        return pedidoRepository.save(pedido);
    }

    private void validarCancelamentoIndevido(String status) {
        if (status != null && "CANCELADO".equalsIgnoreCase(status.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancelamento exige operacao propria com justificativa");
        }
    }
}
