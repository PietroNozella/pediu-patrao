package com.umc.pediupatrao;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
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
class PedidoDescontoCancelamentoTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaRepository auditoriaRepository;

    private PedidoService service;

    @BeforeEach
    void setup() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository);
        service.setAuditoriaDependencias(new AuditoriaService(auditoriaRepository), null);
    }

    private Pedido pedido(String status, String subtotal) {
        Pedido p = new Pedido();
        p.setId("pedido-1");
        p.setStatus(status);
        p.setValorSubtotal(new BigDecimal(subtotal));
        p.setValorTotal(new BigDecimal(subtotal));
        p.setPercentualDesconto(new BigDecimal("0.00"));
        p.setValorDesconto(new BigDecimal("0.00"));
        return p;
    }

    private void mockPedido(Pedido p) {
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(p));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void criacaoDePedidoGeraAuditoriaCompleta() {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(cliente));

        Produto produto = new Produto();
        produto.setId("produto-1");
        produto.setNome("Mussarela");
        produto.setPreco(40.0);
        produto.setAtivo(true);
        when(produtoRepository.findById("produto-1")).thenReturn(Optional.of(produto));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> {
            Pedido pedido = invocacao.getArgument(0);
            pedido.setId("pedido-1");
            return pedido;
        });

        ItemPedidoRequest item = new ItemPedidoRequest();
        item.setProdutoId("produto-1");
        item.setQuantidade(2);
        CriarPedidoRequest request = new CriarPedidoRequest();
        request.setClienteId("cliente-1");
        request.setItens(List.of(item));

        service.criarPedido(request, "atendente01");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        List<String> campos = registro.getCamposAlterados().stream()
                .map(AlteracaoCampo::getCampo)
                .toList();
        assertEquals("CRIACAO_PEDIDO", registro.getTipoOperacao());
        assertTrue(campos.containsAll(List.of(
                "clienteId", "itens", "valorSubtotal", "valorDesconto", "valorTotal",
                "responsavelRegistro", "dataHoraEntrada", "status")));
    }

    @Test
    void mudancaDeStatusGeraAuditoriaComAntesEDepois() {
        Pedido pedido = pedido("RECEBIDO", "50.00");
        mockPedido(pedido);

        service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente01");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        AlteracaoCampo status = registro.getCamposAlterados().stream()
                .filter(alteracao -> "status".equals(alteracao.getCampo()))
                .findFirst()
                .orElseThrow();
        assertEquals("MUDANCA_STATUS", registro.getTipoOperacao());
        assertEquals("RECEBIDO", status.getValorAnterior());
        assertEquals("EM_PREPARACAO", status.getValorNovo());
    }

    @Test
    void aplicaDescontoDezPorCento() {
        Pedido p = pedido("RECEBIDO", "100.00");
        mockPedido(p);
        Pedido salvo = service.aplicarDesconto("pedido-1", new BigDecimal("10"), "gerente01");
        assertEquals(0, salvo.getValorDesconto().compareTo(new BigDecimal("10.00")));
        assertEquals(0, salvo.getValorTotal().compareTo(new BigDecimal("90.00")));

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        AlteracaoCampo desconto = registro.getCamposAlterados().stream()
                .filter(alteracao -> "valorDesconto".equals(alteracao.getCampo()))
                .findFirst()
                .orElseThrow();
        assertEquals("0.00", desconto.getValorAnterior());
        assertEquals("10.00", desconto.getValorNovo());
        assertEquals("gerente01", registro.getUsuario());
        assertEquals("DESCONHECIDO", registro.getPerfil());
        assertNotNull(registro.getDataHora());
    }

    @Test
    void descontoAcimaDeVinteRecebe400() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.aplicarDesconto("pedido-1", new BigDecimal("20.01"), "gerente01")).getStatusCode());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void descontoAposSaidaRecebe409() {
        Pedido p = pedido("SAIU_PARA_ENTREGA", "100.00");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.aplicarDesconto("pedido-1", new BigDecimal("10"), "gerente01")).getStatusCode());
    }

    @Test
    void cancelaComJustificativa() {
        Pedido p = pedido("PRONTO", "50.00");
        mockPedido(p);
        Pedido salvo = service.cancelarPedido("pedido-1", "Cliente desistiu", "gerente01");
        assertEquals("CANCELADO", salvo.getStatus());
        assertEquals("Cliente desistiu", salvo.getMotivoCancelamento());
        assertEquals("gerente01", salvo.getResponsavelCancelamento());
        assertNotNull(salvo.getDataHoraCancelamento());

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        assertEquals("CANCELAMENTO", registro.getTipoOperacao());
        assertEquals("Cliente desistiu", registro.getJustificativa());
    }

    @Test
    void cancelamentoSemJustificativaRecebe400() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.cancelarPedido("pedido-1", "  ", "gerente01")).getStatusCode());
    }

    @Test
    void cancelamentoAposSaidaRecebe409() {
        Pedido p = pedido("RETIRADO", "50.00");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.cancelarPedido("pedido-1", "motivo", "gerente01")).getStatusCode());
    }
}
