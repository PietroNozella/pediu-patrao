package com.umc.pediupatrao;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Pedido;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void atendenteRegistraPedido() throws Exception {
        when(pedidoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"tipoPizza\":\"mussarela\",\"quantidade\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteNaoRegistraPedidoJaCancelado() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"status\":\"CANCELADO\"}"))
                .andExpect(status().isBadRequest());
        verify(pedidoRepo, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNaoRegistraNemAtualizaPedido() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/status").with(csrf()).param("status", "EM_PREPARO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteNaoRegistraPedido() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\"}"))
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
