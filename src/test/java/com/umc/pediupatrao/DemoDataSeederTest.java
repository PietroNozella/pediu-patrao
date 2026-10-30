package com.umc.pediupatrao;

import com.umc.pediupatrao.config.DemoDataSeeder;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void geraPedidosComTipoEntregaETipoProduto() {
        when(pedidoRepository.count()).thenReturn(0L);
        when(clienteRepository.count()).thenReturn(0L);
        when(produtoRepository.count()).thenReturn(0L);
        when(usuarioRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(clienteRepository.saveAll(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(produtoRepository.saveAll(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(pedidoRepository.saveAll(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        DemoDataSeeder seeder = new DemoDataSeeder(clienteRepository, produtoRepository,
                pedidoRepository, usuarioRepository, passwordEncoder);
        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Pedido>> captor = ArgumentCaptor.forClass(List.class);
        verify(pedidoRepository).saveAll(captor.capture());

        List<Pedido> pedidos = captor.getValue();
        assertFalse(pedidos.isEmpty());
        assertTrue(pedidos.stream().allMatch(pedido -> pedido.getTipoEntrega() != null));
        assertTrue(pedidos.stream()
                .flatMap(pedido -> pedido.getItens().stream())
                .allMatch(item -> item.getTipoProduto() != null));
        assertTrue(pedidos.stream().anyMatch(pedido -> "FINALIZADO".equals(pedido.getStatus())));
        assertTrue(pedidos.stream().anyMatch(pedido -> "CANCELADO".equals(pedido.getStatus())));
        assertTrue(pedidos.stream().allMatch(pedido -> pedido.getDataHoraEntrada() != null));
    }
}
