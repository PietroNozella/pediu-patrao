package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private AuditoriaService auditoriaService;
    private UsuarioRepository usuarioRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    @Autowired(required = false)
    public void setAuditoriaDependencias(AuditoriaService auditoriaService, UsuarioRepository usuarioRepository) {
        this.auditoriaService = auditoriaService;
        this.usuarioRepository = usuarioRepository;
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
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        Pedido salvo;
        try {
            salvo = pedidoRepository.save(pedido);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido alterado por outra operacao; recarregue e tente novamente", exception);
        }
        auditar(username, "CRIACAO_PEDIDO", "PEDIDO", salvo.getId(), List.of(
                new AlteracaoCampo("clienteId", null, AuditoriaService.texto(salvo.getClienteId())),
                new AlteracaoCampo("itens", null, descreverItens(salvo.getItens())),
                new AlteracaoCampo("valorSubtotal", null, AuditoriaService.texto(salvo.getValorSubtotal())),
                new AlteracaoCampo("percentualDesconto", null, AuditoriaService.texto(salvo.getPercentualDesconto())),
                new AlteracaoCampo("valorDesconto", null, AuditoriaService.texto(salvo.getValorDesconto())),
                new AlteracaoCampo("valorTotal", null, AuditoriaService.texto(salvo.getValorTotal())),
                new AlteracaoCampo("responsavelRegistro", null, AuditoriaService.texto(salvo.getResponsavelRegistro())),
                new AlteracaoCampo("dataHoraEntrada", null, AuditoriaService.texto(salvo.getDataHoraEntrada())),
                new AlteracaoCampo("status", null, AuditoriaService.texto(salvo.getStatus()))
        ), null);
        return salvo;
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

    public Pedido atualizarStatus(String id, StatusPedido novoStatus, String username) {
        validarResponsavel(username);
        validarCancelamentoIndevido(novoStatus);
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));

        StatusPedido statusAtual = converterStatusAtual(pedido.getStatus());
        if (!statusAtual.permiteTransicaoPara(novoStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transicao de " + statusAtual + " para " + novoStatus + " nao permitida");
        }

        String statusAnterior = pedido.getStatus();
        if (novoStatus.registraSaida()) {
            pedido.setDataHoraSaida(Instant.now());
            pedido.setResponsavelSaida(username);
        }

        pedido.setStatus(novoStatus.name());
        Pedido salvo;
        try {
            salvo = pedidoRepository.save(pedido);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido alterado por outra operacao; recarregue e tente novamente", exception);
        }
        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        alteracoes.add(new AlteracaoCampo("status", statusAnterior, salvo.getStatus()));
        if (novoStatus.registraSaida()) {
            alteracoes.add(new AlteracaoCampo("responsavelSaida", null, AuditoriaService.texto(salvo.getResponsavelSaida())));
            alteracoes.add(new AlteracaoCampo("dataHoraSaida", null, AuditoriaService.texto(salvo.getDataHoraSaida())));
        }
        auditar(username, "MUDANCA_STATUS", "PEDIDO", salvo.getId(), alteracoes, null);
        return salvo;
    }

    public Pedido aplicarDesconto(String id, BigDecimal percentual, String username) {
        validarResponsavel(username);
        if (percentual == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentual obrigatorio");
        }
        if (percentual.compareTo(new BigDecimal("0.01")) < 0 || percentual.compareTo(new BigDecimal("20.00")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desconto deve ser entre 0,01% e 20%");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        StatusPedido statusAtual = converterStatusAtual(pedido.getStatus());
        if (statusAtual == StatusPedido.CANCELADO || statusAtual == StatusPedido.FINALIZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido finalizado ou cancelado nao aceita desconto");
        }
        if (statusAtual == StatusPedido.SAIU_PARA_ENTREGA || statusAtual == StatusPedido.RETIRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Desconto somente antes da saida para entrega ou retirada");
        }
        if (pedido.getValorSubtotal() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem subtotal valido");
        }

        String percAnterior = AuditoriaService.texto(pedido.getPercentualDesconto());
        String descontoAnterior = AuditoriaService.texto(pedido.getValorDesconto());
        String totalAnterior = AuditoriaService.texto(pedido.getValorTotal());

        BigDecimal percNorm = percentual.setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorDesconto = pedido.getValorSubtotal()
                .multiply(percNorm)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal valorTotal = pedido.getValorSubtotal().subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);

        pedido.setPercentualDesconto(percNorm);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(valorTotal);
        Pedido salvo;
        try {
            salvo = pedidoRepository.save(pedido);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido alterado por outra operacao; recarregue e tente novamente", exception);
        }
        auditar(username, "DESCONTO", "PEDIDO", salvo.getId(), List.of(
                new AlteracaoCampo("percentualDesconto", percAnterior, AuditoriaService.texto(salvo.getPercentualDesconto())),
                new AlteracaoCampo("valorDesconto", descontoAnterior, AuditoriaService.texto(salvo.getValorDesconto())),
                new AlteracaoCampo("valorTotal", totalAnterior, AuditoriaService.texto(salvo.getValorTotal()))
        ), null);
        return salvo;
    }

    public Pedido cancelarPedido(String id, String justificativa, String username) {
        validarResponsavel(username);
        if (justificativa == null || justificativa.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Justificativa obrigatoria para cancelamento");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));
        StatusPedido statusAtual = converterStatusAtual(pedido.getStatus());
        if (statusAtual == StatusPedido.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ja cancelado");
        }
        if (statusAtual == StatusPedido.FINALIZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido finalizado nao pode ser cancelado");
        }
        if (statusAtual == StatusPedido.SAIU_PARA_ENTREGA || statusAtual == StatusPedido.RETIRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ja saiu para entrega ou retirada; cancelamento bloqueado");
        }

        String statusAnterior = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setMotivoCancelamento(justificativa.trim());
        pedido.setResponsavelCancelamento(username);
        pedido.setDataHoraCancelamento(Instant.now());
        Pedido salvo;
        try {
            salvo = pedidoRepository.save(pedido);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido alterado por outra operacao; recarregue e tente novamente", exception);
        }
        auditar(username, "CANCELAMENTO", "PEDIDO", salvo.getId(), List.of(
                new AlteracaoCampo("status", statusAnterior, salvo.getStatus()),
                new AlteracaoCampo("motivoCancelamento", null, AuditoriaService.texto(salvo.getMotivoCancelamento()))
        ), justificativa.trim());
        return salvo;
    }

    private void validarResponsavel(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsavel autenticado obrigatorio");
        }
    }

    private void validarCancelamentoIndevido(StatusPedido status) {
        if (status == StatusPedido.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancelamento exige operacao propria com justificativa");
        }
    }

    private StatusPedido converterStatusAtual(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido sem status valido");
        }

        try {
            return StatusPedido.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Status atual do pedido nao reconhecido", exception);
        }
    }

    private String descreverItens(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            return "";
        }
        return itens.stream()
                .map(item -> item.getNomeProduto() + " x" + item.getQuantidade()
                        + " @ " + item.getPrecoUnitario() + " = " + item.getSubtotal())
                .collect(Collectors.joining("; "));
    }

    private void auditar(String username, String tipo, String entidade, String entidadeId,
                         List<AlteracaoCampo> alteracoes, String justificativa) {
        if (auditoriaService == null) {
            return;
        }
        auditoriaService.registrar(username, resolverPerfil(username), tipo, entidade, entidadeId, alteracoes, justificativa);
    }

    private String resolverPerfil(String username) {
        if (usuarioRepository == null) {
            return "DESCONHECIDO";
        }
        return usuarioRepository.findByUsername(username)
                .map(u -> u.getRole() == null ? "DESCONHECIDO" : u.getRole())
                .orElse("DESCONHECIDO");
    }
}
