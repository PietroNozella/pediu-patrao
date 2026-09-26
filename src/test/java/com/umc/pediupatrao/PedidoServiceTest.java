package com.umc.pediupatrao;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepo;

    @Mock
    private ClienteRepository clienteRepo;

    @Mock
    private ProdutoRepository produtoRepo;

    private PedidoService service;

    @BeforeEach
    void setup() {
        service = new PedidoService(pedidoRepo, clienteRepo, produtoRepo);
    }

    private Produto produto(String id, String nome, Double preco, Boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    private ItemPedidoRequest item(String produtoId, int quantidade) {
        ItemPedidoRequest i = new ItemPedidoRequest();
        i.setProdutoId(produtoId);
        i.setQuantidade(quantidade);
        return i;
    }

    private CriarPedidoRequest request(String clienteId, ItemPedidoRequest... itens) {
        CriarPedidoRequest r = new CriarPedidoRequest();
        r.setClienteId(clienteId);
        r.setItens(itens == null ? null : List.of(itens));
        return r;
    }

    private void mockClienteOk(String clienteId) {
        Cliente c = new Cliente();
        c.setId(clienteId);
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(c));
    }

    private void mockSaveThrough() {
        when(pedidoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void criaPedidoComUmItem() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Mussarela", 40.0, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 2)), "atendente01");

        assertEquals(1, pedido.getItens().size());
        assertEquals("RECEBIDO", pedido.getStatus());
        assertEquals("atendente01", pedido.getResponsavelRegistro());
        assertNotNull(pedido.getDataHoraEntrada());
    }

    @Test
    void criaPedidoComVariosItens() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Mussarela", 40.0, true)));
        when(produtoRepo.findById("p2")).thenReturn(Optional.of(produto("p2", "Coca", 8.0, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 1), item("p2", 3)), "atendente01");

        assertEquals(2, pedido.getItens().size());
        assertEquals(0, pedido.getValorSubtotal().compareTo(new BigDecimal("64.00")));
        assertEquals(0, pedido.getValorTotal().compareTo(new BigDecimal("64.00")));
    }

    @Test
    void subtotalDoItemEhPrecoVezesQuantidade() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Mussarela", 10.50, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 3)), "atendente01");

        assertEquals(0, pedido.getItens().get(0).getSubtotal().compareTo(new BigDecimal("31.50")));
    }

    @Test
    void descontoInicialEhZeroETotalIgualSubtotal() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Mussarela", 10.0, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 2)), "atendente01");

        assertEquals(0, pedido.getPercentualDesconto().compareTo(BigDecimal.ZERO));
        assertEquals(0, pedido.getValorDesconto().compareTo(BigDecimal.ZERO));
        assertEquals(0, pedido.getValorTotal().compareTo(pedido.getValorSubtotal()));
    }

    @Test
    void usaPrecoDoBancoComSnapshotDeNomeEPreco() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Nome Original", 25.0, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 1)), "atendente01");

        assertEquals("Nome Original", pedido.getItens().get(0).getNomeProduto());
        assertEquals(0, pedido.getItens().get(0).getPrecoUnitario().compareTo(new BigDecimal("25.00")));
    }

    @Test
    void arredondaParaDuasCasasComHalfUp() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", 10.005, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 1)), "atendente01");

        assertEquals(0, pedido.getItens().get(0).getPrecoUnitario().compareTo(new BigDecimal("10.01")));
    }

    @Test
    void clienteInexistenteRecebe404() {
        when(clienteRepo.findById("fantasma")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("fantasma", item("p1", 1)), "atendente01"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void produtoInexistenteRecebe404() {
        mockClienteOk("c1");
        when(produtoRepo.findById("fantasma")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("fantasma", 1)), "atendente01"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void produtoInativoRecebe400() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", 10.0, false)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1)), "atendente01"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void produtoSemPrecoRecebe400() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", null, true)));

        assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1)), "atendente01"));
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void produtoSemNomeRecebe400() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", " ", 10.0, true)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1)), "atendente01"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void produtoComPrecoInvalidoRecebe400() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", 0.0, true)));

        assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1)), "atendente01"));
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void quantidadeZeroRecebe400() {
        mockClienteOk("c1");

        assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 0)), "atendente01"));
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void quantidadeNegativaRecebe400() {
        mockClienteOk("c1");

        assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", -2)), "atendente01"));
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void listaVaziaRecebe400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1"), "atendente01"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void responsavelAusenteRecebe400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1)), null));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void falhaEmQualquerItemNaoSalvaPedido() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "Ok", 10.0, true)));
        when(produtoRepo.findById("p2")).thenReturn(Optional.of(produto("p2", "Inativo", 10.0, false)));

        assertThrows(ResponseStatusException.class,
                () -> service.criarPedido(request("c1", item("p1", 1), item("p2", 1)), "atendente01"));
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    void canceladoNaoPodeSerDefinidoNaCriacao() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", 10.0, true)));
        mockSaveThrough();

        Pedido pedido = service.criarPedido(request("c1", item("p1", 1)), "atendente01");

        assertEquals("RECEBIDO", pedido.getStatus());
    }

    @Test
    void valoresSalvosVemDoServidor() {
        mockClienteOk("c1");
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto("p1", "X", 10.0, true)));
        mockSaveThrough();

        service.criarPedido(request("c1", item("p1", 2)), "atendente01");

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepo).save(captor.capture());
        Pedido salvo = captor.getValue();
        assertEquals(0, salvo.getValorSubtotal().compareTo(new BigDecimal("20.00")));
        assertEquals(0, salvo.getValorTotal().compareTo(new BigDecimal("20.00")));
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("atendente01", salvo.getResponsavelRegistro());
        assertNotNull(salvo.getDataHoraEntrada());
    }
}
