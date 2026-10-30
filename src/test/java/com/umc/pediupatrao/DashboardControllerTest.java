package com.umc.pediupatrao;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.dto.dashboard.ComparacaoResponse;
import com.umc.pediupatrao.dto.dashboard.PeriodoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @WithMockUser(roles = "GERENTE")
    void gerenteAcessaResumo() throws Exception {
        when(dashboardService.resumo(any(), any())).thenReturn(resumoVazio());

        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAcessaResumo() throws Exception {
        when(dashboardService.resumo(any(), any())).thenReturn(resumoVazio());

        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void atendenteRecebeAcessoNegado() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioAnonimoRecebeNaoAutenticado() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void erroDeValidacaoRetornaProblemDetail() throws Exception {
        when(dashboardService.resumo(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "inicio deve ser anterior ou igual a fim"));

        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Parametro invalido"))
                .andExpect(jsonPath("$.detail").value("inicio deve ser anterior ou igual a fim"));
    }

    private ResumoDashboardResponse resumoVazio() {
        return new ResumoDashboardResponse(
                new PeriodoResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                BigDecimal.ZERO.setScale(2),
                0,
                0,
                BigDecimal.ZERO.setScale(2),
                0,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                0,
                BigDecimal.ZERO.setScale(2),
                new ComparacaoResponse(null, null),
                List.of());
    }
}
