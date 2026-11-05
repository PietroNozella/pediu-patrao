package com.umc.pediupatrao;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "DASHBOARD_TEST_MONGODB_URI", matches = ".+")
class DashboardMongoIntegrationTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 15);
    private MongoClient client;
    private MongoTemplate mongo;
    private DashboardService service;

    @BeforeEach
    void preparar() {
        client = MongoClients.create(System.getenv("DASHBOARD_TEST_MONGODB_URI"));
        // Nunca usa o banco informado na URI: cada teste possui uma base descartável própria.
        mongo = new MongoTemplate(client, "dashboard_test_" + UUID.randomUUID().toString().replace("-", ""));
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongo);
        service = new DashboardService(mongo, factory.getRepository(ClienteRepository.class),
                factory.getRepository(ProdutoRepository.class));
        mongo.getCollection("clientes").insertOne(new Document("_id", "cliente").append("nome", "Cliente de teste"));
        mongo.getCollection("pedidos").insertMany(List.of(
                pedido("texto", "FINALIZADO", "100.00", "10.00", "110.00", "Pizza", 2, DIA),
                pedido("decimal", "FINALIZADO", decimal("50.00"), decimal("5.00"), decimal("55.00"), "Bebida", 1, DIA),
                pedido("numero", "EM_PREPARACAO", 25.0, 0, 25.0, "Pizza", 1, DIA),
                pedido("cancelado", "CANCELADO", "900.00", "90.00", "990.00", "Pizza", 9, DIA),
                pedido("sem-valor", "FINALIZADO", null, null, null, "Outro", 0, DIA),
                pedido("anterior", "FINALIZADO", "75.00", "0.00", "75.00", "Pizza", 1, DIA.minusDays(1))));
    }

    @AfterEach
    void limpar() {
        try {
            if (mongo != null) {
                mongo.getDb().drop();
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    void resumoSomaValoresMistosEComparaPeriodoAnterior() {
        var resumo = service.resumo(DIA, DIA);

        assertEquals(5, resumo.pedidos());
        assertEquals(3, resumo.finalizados());
        assertEquals(1, resumo.cancelados());
        assertEquals(1, resumo.emAndamento());
        assertEquals(new BigDecimal("150.00"), resumo.faturamento());
        assertEquals(new BigDecimal("15.00"), resumo.descontos());
        assertEquals(new BigDecimal("50.00"), resumo.ticketMedio());
        assertEquals(new BigDecimal("20.00"), resumo.taxaCancelamento());
        assertEquals(new BigDecimal("100.00"), resumo.comparacao().faturamentoVariacaoPercentual());
    }

    @Test
    void vendasAgregamReceitaCategoriasEEntregaEmTodosOsAgrupamentos() {
        for (String agrupamento : List.of("dia", "semana", "mes")) {
            var vendas = service.vendas(DIA, DIA, agrupamento, null, null);
            assertEquals(new BigDecimal("150.00"), vendas.serie().getFirst().faturamento());
            assertEquals(4, vendas.serie().getFirst().pedidos());
            var pizza = vendas.porCategoria().stream().filter(item -> item.categoria().equals("Pizza")).findFirst().orElseThrow();
            assertEquals(new BigDecimal("135.00"), pizza.receita());
            assertEquals(new BigDecimal("150.00"), vendas.porTipoEntrega().getFirst().faturamento());
        }
    }

    @Test
    void rankingOrdenaReceitaNumericaECalculaParticipacao() {
        var ranking = service.produtos(DIA, DIA, "receita", 10, null, null, null);
        var primeiro = ranking.itens().getFirst();
        assertEquals("Pizza", primeiro.nome());
        assertEquals(3, primeiro.quantidade());
        assertEquals(new BigDecimal("135.00"), primeiro.receita());
        assertEquals(new BigDecimal("75.00"), primeiro.participacaoQuantidadePercentual());
        assertEquals(new BigDecimal("71.05"), primeiro.participacaoReceitaPercentual());

        var filtrado = service.produtos(DIA, DIA, "receita", 10, "Bebida", null, "ENTREGA");
        assertEquals(1, filtrado.itens().size());
        assertEquals(new BigDecimal("55.00"), filtrado.itens().getFirst().receita());
        assertEquals(new BigDecimal("100.00"), filtrado.itens().getFirst().participacaoReceitaPercentual());
    }

    @Test
    void pedidosFiltramStatusEntregaEPaginamComNomeDoCliente() {
        var pagina = service.pedidos(DIA, DIA, "FINALIZADO", "ENTREGA", 0, 2);
        assertEquals(3, pagina.totalElements());
        assertEquals(2, pagina.totalPages());
        assertEquals(2, pagina.content().size());
        assertTrue(pagina.content().stream().allMatch(item -> item.clienteNome().equals("Cliente de teste")));
        var segunda = service.pedidos(DIA, DIA, "FINALIZADO", "ENTREGA", 1, 2);
        assertEquals(1, segunda.content().size());
    }

    @Test
    void periodoVazioRetornaZerosESerieContinua() {
        LocalDate vazio = DIA.plusDays(2);
        assertEquals(new BigDecimal("0.00"), service.resumo(vazio, vazio).faturamento());
        var vendas = service.vendas(vazio, vazio.plusDays(2), "dia", null, null);
        assertEquals(3, vendas.serie().size());
        assertTrue(vendas.serie().stream().allMatch(item -> item.faturamento().signum() == 0));
        assertTrue(service.produtos(vazio, vazio, "receita", 10, null, null, null).itens().isEmpty());
    }

    private Document pedido(String id, String status, Object total, Object desconto, Object subtotal,
                            String tipo, int quantidade, LocalDate dia) {
        Document item = new Document("produtoId", tipo).append("nomeProduto", tipo)
                .append("tipoProduto", tipo).append("quantidade", quantidade).append("subtotal", subtotal);
        return new Document("_id", id).append("clienteId", "cliente").append("status", status)
                .append("tipoEntrega", "ENTREGA").append("valorTotal", total).append("valorDesconto", desconto)
                .append("dataHoraEntrada", Date.from(dia.atTime(12, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant()))
                .append("itens", List.of(item));
    }

    private Decimal128 decimal(String valor) {
        return new Decimal128(new BigDecimal(valor));
    }
}
