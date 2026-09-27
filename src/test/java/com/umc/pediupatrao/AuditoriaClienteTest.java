package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaClienteTest {

    @Mock
    private ClienteRepository clienteRepo;
    @Mock
    private AuditoriaRepository auditoriaRepo;

    @Test
    void alteracaoDeEnderecoETelefoneGeraDiff() {
        Cliente anterior = new Cliente();
        anterior.setId("c1");
        anterior.setLogradouro("Rua das Flores");
        anterior.setTelefone("1111");
        when(clienteRepo.findById("c1")).thenReturn(Optional.of(anterior));
        when(clienteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auditoriaRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepo);
        ClienteService service = new ClienteService(clienteRepo);
        service.setAuditoriaDependencias(auditoriaService, null);

        Cliente novo = new Cliente();
        novo.setId("c1");
        novo.setLogradouro("Av. Brasil");
        novo.setTelefone("2222");
        service.salvar(novo, "atendente01");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepo).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        assertEquals("ALTERACAO_CLIENTE", registro.getTipoOperacao());
        assertEquals("CLIENTE", registro.getEntidade());
        assertEquals("atendente01", registro.getUsuario());
        assertNotNull(registro.getDataHora());
        List<String> campos = registro.getCamposAlterados().stream().map(AlteracaoCampo::getCampo).toList();
        assertTrue(campos.contains("logradouro"));
        assertTrue(campos.contains("telefone"));
    }

    @Test
    void criacaoDeClienteAuditaEnderecoCompleto() {
        when(clienteRepo.save(any())).thenAnswer(invocacao -> {
            Cliente cliente = invocacao.getArgument(0);
            cliente.setId("c1");
            return cliente;
        });
        when(auditoriaRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ClienteService service = new ClienteService(clienteRepo);
        service.setAuditoriaDependencias(new AuditoriaService(auditoriaRepo), null);

        Cliente cliente = new Cliente();
        cliente.setNome("Ana");
        cliente.setTelefone("11999999999");
        cliente.setCep("01001-000");
        cliente.setLogradouro("Praça da Sé");
        cliente.setNumero("1");
        cliente.setBairro("Sé");
        cliente.setCidade("São Paulo");
        cliente.setEstado("SP");
        service.novoCliente(cliente, "atendente01");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepo).save(captor.capture());
        List<String> campos = captor.getValue().getCamposAlterados().stream()
                .map(AlteracaoCampo::getCampo)
                .toList();
        assertTrue(campos.containsAll(List.of(
                "nome", "telefone", "cep", "logradouro", "numero", "bairro", "cidade", "estado")));
    }

    @Test
    void atualizacaoDeClienteInexistenteRecebe404() {
        when(clienteRepo.findById("inexistente")).thenReturn(Optional.empty());
        ClienteService service = new ClienteService(clienteRepo);
        service.setAuditoriaDependencias(new AuditoriaService(auditoriaRepo), null);

        Cliente cliente = new Cliente();
        cliente.setId("inexistente");

        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> service.salvar(cliente, "atendente01"));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        verify(clienteRepo, never()).save(any());
        verify(auditoriaRepo, never()).save(any());
    }
}
