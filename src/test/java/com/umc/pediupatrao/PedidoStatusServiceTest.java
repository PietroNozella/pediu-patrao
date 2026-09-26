package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoStatusServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private PedidoService service;

    @BeforeEach
    void setup() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository);
    }

    private Pedido pedidoNoStatus(String status) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setStatus(status);
        return pedido;
    }

    private Pedido prepararPersistencia(String status) {
        Pedido pedido = pedidoNoStatus(status);
        when(pedidoRepository.findById("pedido-1")).thenAnswer(ignored -> Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return pedido;
    }

    @Test
    void percorreFluxoCompletoAteFinalizadoPorEntrega() {
        Pedido pedido = prepararPersistencia("RECEBIDO");

        service.atualizarStatus(pedido.getId(), StatusPedido.EM_PREPARACAO, "atendente01");
        service.atualizarStatus(pedido.getId(), StatusPedido.PRONTO, "atendente01");
        service.atualizarStatus(pedido.getId(), StatusPedido.SAIU_PARA_ENTREGA, "gerente01");

        assertEquals("SAIU_PARA_ENTREGA", pedido.getStatus());
        assertEquals("gerente01", pedido.getResponsavelSaida());
        assertNotNull(pedido.getDataHoraSaida());
        Instant dataHoraSaida = pedido.getDataHoraSaida();

        service.atualizarStatus(pedido.getId(), StatusPedido.FINALIZADO, "atendente01");

        assertEquals("FINALIZADO", pedido.getStatus());
        assertEquals("gerente01", pedido.getResponsavelSaida());
        assertEquals(dataHoraSaida, pedido.getDataHoraSaida());
        verify(pedidoRepository, times(4)).save(pedido);
    }

    @Test
    void percorreFluxoDeRetiradaERegistraResponsavel() {
        Pedido pedido = prepararPersistencia("PRONTO");

        service.atualizarStatus(pedido.getId(), StatusPedido.RETIRADO, "atendente02");
        service.atualizarStatus(pedido.getId(), StatusPedido.FINALIZADO, "atendente02");

        assertEquals("FINALIZADO", pedido.getStatus());
        assertEquals("atendente02", pedido.getResponsavelSaida());
        assertNotNull(pedido.getDataHoraSaida());
    }

    @Test
    void transicaoComumNaoRegistraSaida() {
        Pedido pedido = prepararPersistencia("RECEBIDO");

        service.atualizarStatus(pedido.getId(), StatusPedido.EM_PREPARACAO, "atendente01");

        assertNull(pedido.getResponsavelSaida());
        assertNull(pedido.getDataHoraSaida());
    }

    @Test
    void impedeSaltoDeStatus() {
        Pedido pedido = pedidoNoStatus("RECEBIDO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.PRONTO, "atendente01"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void impedeRetrocessoERepeticaoDeStatus() {
        Pedido pedido = pedidoNoStatus("PRONTO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente01"))
                .getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.PRONTO, "atendente01"))
                .getStatusCode());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void estadosTerminaisNaoAceitamAlteracoes() {
        for (String status : new String[]{"FINALIZADO", "CANCELADO"}) {
            reset(pedidoRepository);
            Pedido pedido = pedidoNoStatus(status);
            when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente01"));

            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            verify(pedidoRepository, never()).save(any());
        }
    }

    @Test
    void statusAntigoOuDesconhecidoFicaBloqueado() {
        Pedido pedido = pedidoNoStatus("NOVO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente01"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelamentoContinuaForaDoEndpointGenerico() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.CANCELADO, "gerente01"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(pedidoRepository, never()).findById(any());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void pedidoInexistenteRecebe404() {
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente01"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void concorrenciaRecebe409SemRepetirGravacao() {
        Pedido pedido = pedidoNoStatus("PRONTO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(pedido)).thenThrow(new OptimisticLockingFailureException("versao desatualizada"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.RETIRADO, "atendente01"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Pedido alterado por outra operacao; recarregue e tente novamente", exception.getReason());
        verify(pedidoRepository, times(1)).save(pedido);
    }

    @Test
    void responsavelAusenteNaoConsultaNemSalvaPedido() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, " "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(pedidoRepository);
    }
}
