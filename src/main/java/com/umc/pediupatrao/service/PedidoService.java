package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    public Pedido criarPedido(CriarPedidoRequest request, String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsavel autenticado obrigatorio");
        }
        if (request == null || request.getItens() == null || request.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pedido precisa de ao menos um item");
        }
        if (request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente obrigatorio");
        }
        clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado"));

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotalPedido = BigDecimal.ZERO.setScale(2);
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            itens.add(montarItem(itemRequest));
            subtotalPedido = subtotalPedido.add(itens.get(itens.size() - 1).getSubtotal());
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(request.getClienteId());
        pedido.setItens(itens);
        pedido.setValorSubtotal(subtotalPedido);
        pedido.setPercentualDesconto(new BigDecimal("0.00"));
        pedido.setValorDesconto(new BigDecimal("0.00"));
        pedido.setValorTotal(subtotalPedido);
        pedido.setResponsavelRegistro(username);
        pedido.setDataHoraEntrada(Instant.now());
        pedido.setStatus("RECEBIDO");
        return pedidoRepository.save(pedido);
    }

    private ItemPedido montarItem(ItemPedidoRequest itemRequest) {
        if (itemRequest == null || itemRequest.getQuantidade() == null || itemRequest.getQuantidade() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade deve ser positiva");
        }
        if (itemRequest.getProdutoId() == null || itemRequest.getProdutoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto obrigatorio");
        }
        Produto produto = produtoRepository.findById(itemRequest.getProdutoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto nao encontrado"));
        if (produto.getAtivo() == null || !produto.getAtivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo");
        }
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto sem nome valido");
        }
        if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto sem preco valido");
        }
        BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = preco.multiply(BigDecimal.valueOf(itemRequest.getQuantidade()))
                .setScale(2, RoundingMode.HALF_UP);
        return new ItemPedido(produto.getId(), produto.getNome(),
                itemRequest.getQuantidade(), preco, subtotal);
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
