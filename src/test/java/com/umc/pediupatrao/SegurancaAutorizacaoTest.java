package com.umc.pediupatrao;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UsuarioController.class, ClienteController.class, PedidoController.class, HomeController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioService.class,
        ClienteService.class, PedidoService.class, ProdutoService.class})
class SegurancaAutorizacaoTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioRepository usuarioRepo;

    @MockitoBean
    private ClienteRepository clienteRepo;

    @MockitoBean
    private PedidoRepository pedidoRepo;

    @MockitoBean
    private ProdutoRepository produtoRepo;

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("hash");
        u.setRole(role);
        return u;
    }

    private void mockUsuarioDinamico() {
        when(usuarioRepo.findByUsername(any())).thenReturn(Optional.empty());
        when(usuarioRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ========================
    // Não autenticado
    // ========================

    @Test
    void naoAutenticadoEhEncaminhadoAoLogin() throws Exception {
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login*"));
    }

    // ========================
    // Administração de usuários
    // ========================

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminListaUsuarios() throws Exception {
        when(usuarioRepo.findAll()).thenReturn(List.of(usuario("1", "admin", "ADMIN")));
        mvc.perform(get("/api/usuarios")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCriaUsuarioComCadaPerfilValido() throws Exception {
        mockUsuarioDinamico();
        for (String role : new String[]{"ADMIN", "GERENTE", "ATENDENTE"}) {
            mvc.perform(post("/api/usuarios").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"novo-" + role + "\",\"password\":\"senha123\",\"role\":\"" + role + "\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAlteraPerfil() throws Exception {
        when(usuarioRepo.findById("1")).thenReturn(Optional.of(usuario("1", "alvo", "ATENDENTE")));
        when(usuarioRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(put("/api/usuarios/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"GERENTE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminExcluiUsuario() throws Exception {
        when(usuarioRepo.existsById("1")).thenReturn(true);
        mvc.perform(delete("/api/usuarios/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/usuarios/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/novo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoConsegueAutoelevacaoPelaApi() throws Exception {
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"invasor\",\"password\":\"senha123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void perfilInvalidoRecebe400() throws Exception {
        mockUsuarioDinamico();
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\",\"role\":\"SUPER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roleComPrefixoRecebe400() throws Exception {
        mockUsuarioDinamico();
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\",\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void perfilVazioRecebe400() throws Exception {
        mockUsuarioDinamico();
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void perfilOmitidoRecebe400() throws Exception {
        mockUsuarioDinamico();
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAcessaTelaDeUsuarios() throws Exception {
        when(usuarioRepo.findAll()).thenReturn(List.of(usuario("1", "admin", "ADMIN")));
        mvc.perform(get("/usuarios")).andExpect(status().isOk());
    }

    // ========================
    // Clientes
    // ========================

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaClientes() throws Exception {
        when(clienteRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteExcluiCliente() throws Exception {
        when(clienteRepo.existsById("1")).thenReturn(true);
        mvc.perform(delete("/api/clientes/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoCadastraNemEditaCliente() throws Exception {
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteConsultaCadastraEEditaCliente() throws Exception {
        when(clienteRepo.findAll()).thenReturn(List.of());
        when(clienteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(clienteRepo.existsById("1")).thenReturn(true);

        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/clientes/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoExcluiCliente() throws Exception {
        mvc.perform(delete("/api/clientes/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoTemAcessoAutomaticoAClientes() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Fulano\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/1").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/clientes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteAcessaTelaDeClientes() throws Exception {
        when(clienteRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/clientes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoAbreNemSalvaFormularioDeCliente() throws Exception {
        mvc.perform(get("/clientes/editar/1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/salvar").with(csrf())
                .param("nome", "Fulano")
                .param("telefone", "11999999999"))
                .andExpect(status().isForbidden());
    }

    // ========================
    // Pedidos
    // ========================

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaPedidos() throws Exception {
        when(pedidoRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(get("/pedidos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteConsultaPedidos() throws Exception {
        when(pedidoRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(get("/pedidos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteConsultaPedidos() throws Exception {
        when(pedidoRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(get("/pedidos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteVisualizaPedidoComItensEValores() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setClienteId("c1");
        pedido.setItens(List.of(new ItemPedido(
                "p1", "Mussarela", 2, new BigDecimal("40.00"), new BigDecimal("80.00"))));
        pedido.setValorSubtotal(new BigDecimal("80.00"));
        pedido.setValorDesconto(BigDecimal.ZERO);
        pedido.setValorTotal(new BigDecimal("80.00"));
        pedido.setResponsavelRegistro("atendente01");
        pedido.setDataHoraEntrada(Instant.parse("2026-01-01T15:00:00Z"));
        pedido.setStatus("RECEBIDO");
        when(pedidoRepo.findAll()).thenReturn(List.of(pedido));

        mvc.perform(get("/pedidos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2x Mussarela")))
                .andExpect(content().string(containsString("R$ 80,00")))
                .andExpect(content().string(containsString("atendente01")))
                .andExpect(content().string(containsString("01/01/2026")));
    }

    private void mockCatalogoOk() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        when(clienteRepo.findById("c1")).thenReturn(Optional.of(cliente));
        Produto produto = new Produto();
        produto.setId("p1");
        produto.setNome("Mussarela");
        produto.setPreco(40.0);
        produto.setAtivo(true);
        when(produtoRepo.findById("p1")).thenReturn(Optional.of(produto));
        when(pedidoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private String pedidoValidoJson() {
        return "{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":2}]}";
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteRegistraPedido() throws Exception {
        mockCatalogoOk();
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoValidoJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.responsavelRegistro").value("user"))
                .andExpect(jsonPath("$.valorTotal").value(80.0))
                .andExpect(jsonPath("$.valorDesconto").value(0.0));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void valoresEnviadosPeloClienteSaoIgnorados() throws Exception {
        mockCatalogoOk();
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":2}],"
                        + "\"valorTotal\":0.01,\"valorSubtotal\":0.01,\"valorDesconto\":99.0,"
                        + "\"responsavelRegistro\":\"admin\",\"status\":\"CANCELADO\","
                        + "\"dataHoraEntrada\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepo).save(captor.capture());
        Pedido salvo = captor.getValue();
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("user", salvo.getResponsavelRegistro());
        assertEquals(0, salvo.getValorTotal().compareTo(new BigDecimal("80.00")));
        assertEquals(0, salvo.getValorDesconto().compareTo(BigDecimal.ZERO));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void payloadInvalidoRecebe400() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"itens\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":0}]}"))
                .andExpect(status().isBadRequest());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void quantidadeFracionariaRecebe400() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":1.5}]}"))
                .andExpect(status().isBadRequest());

        verify(pedidoRepo, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void clienteInexistenteRecebe404() throws Exception {
        when(clienteRepo.findById("fantasma")).thenReturn(Optional.empty());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"fantasma\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":1}]}"))
                .andExpect(status().isNotFound());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void produtoInexistenteRecebe404() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        when(clienteRepo.findById("c1")).thenReturn(Optional.of(cliente));
        when(produtoRepo.findById("fantasma")).thenReturn(Optional.empty());
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"fantasma\",\"quantidade\":1}]}"))
                .andExpect(status().isNotFound());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void postPedidosSemCsrfRecebe403() throws Exception {
        mvc.perform(post("/api/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoValidoJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteAcessaNovoPedidoForm() throws Exception {
        when(clienteRepo.findAll()).thenReturn(List.of());
        when(produtoRepo.findAll()).thenReturn(List.of());
        mvc.perform(get("/pedidos/novo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoAcessaNovoPedidoForm() throws Exception {
        mvc.perform(get("/pedidos/novo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoAcessaNovoPedidoForm() throws Exception {
        mvc.perform(get("/pedidos/novo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoRegistraNemAtualizaPedido() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoValidoJson()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "EM_PREPARO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoRegistraPedido() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoValidoJson()))
                .andExpect(status().isForbidden());
    }

    private void mockStatusOk() {
        Pedido pedido = new Pedido();
        pedido.setId("1");
        pedido.setStatus("NOVO");
        when(pedidoRepo.findById("1")).thenReturn(Optional.of(pedido));
        when(pedidoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteAtualizaStatusComum() throws Exception {
        mockStatusOk();
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "EM_PREPARO"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteAtualizaStatusComum() throws Exception {
        mockStatusOk();
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "EM_PREPARO"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoDefineCanceladoPeloEndpointGenerico() throws Exception {
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "CANCELADO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoDefineCanceladoPeloEndpointGenerico() throws Exception {
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "CANCELADO"))
                .andExpect(status().isBadRequest());
    }

    // ========================
    // CSRF
    // ========================

    @Test
    @WithMockUser(roles = "ADMIN")
    void postSemCsrfRecebe403() throws Exception {
        mvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"senha123\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void deleteSemCsrfRecebe403() throws Exception {
        mvc.perform(delete("/api/clientes/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putSemCsrfRecebe403() throws Exception {
        mvc.perform(put("/api/usuarios/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"GERENTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postComCsrfChegaAoController() throws Exception {
        mockUsuarioDinamico();
        mvc.perform(post("/api/usuarios").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"com-token\",\"password\":\"senha123\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isCreated());
    }
}
