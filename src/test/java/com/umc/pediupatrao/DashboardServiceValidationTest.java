package com.umc.pediupatrao;

import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DashboardServiceValidationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private DashboardService service;

    @BeforeEach
    void setup() {
        service = new DashboardService(mongoTemplate, clienteRepository, produtoRepository);
    }

    @Test
    void rejeitaPeriodoInvertido() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.resumo(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaPeriodoMaiorQueUmAno() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.resumo(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaAgrupamentoInvalido() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.vendas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        "trimestre", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaLimiteDeRankingForaDoIntervalo() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.produtos(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        "quantidade", 0, null, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaStatusInvalidoNaListagem() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.pedidos(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        "INVALIDO", null, 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaTipoDeEntregaInvalido() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.vendas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        "dia", null, "DRONE"));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaPaginaNegativa() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.pedidos(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        null, null, -1, 10));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void rejeitaTamanhoDePaginaForaDoIntervalo() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.pedidos(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        null, null, 0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }
}
