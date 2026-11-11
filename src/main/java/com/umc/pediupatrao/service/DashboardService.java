package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.CategoriaResponse;
import com.umc.pediupatrao.dto.dashboard.ComparacaoResponse;
import com.umc.pediupatrao.dto.dashboard.FiltrosDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.PaginaResponse;
import com.umc.pediupatrao.dto.dashboard.PedidoResumoResponse;
import com.umc.pediupatrao.dto.dashboard.PeriodoResponse;
import com.umc.pediupatrao.dto.dashboard.PontoSerieResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutosDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.StatusResumoResponse;
import com.umc.pediupatrao.dto.dashboard.TipoEntregaResumoResponse;
import com.umc.pediupatrao.dto.dashboard.VendasDashboardResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final String COLECAO_PEDIDOS = "pedidos";
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final String[] MESES = {
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };
    private static final int PERIODO_MAXIMO_DIAS = 366;
    private static final int LIMITE_MAXIMO_PRODUTOS = 50;
    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final MongoTemplate mongoTemplate;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public DashboardService(MongoTemplate mongoTemplate,
                            ClienteRepository clienteRepository,
                            ProdutoRepository produtoRepository) {
        this.mongoTemplate = mongoTemplate;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    public ResumoDashboardResponse resumo(LocalDate inicio, LocalDate fim) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        Document resultado = agregarUnico(matchPeriodo(periodo, null, null, false), facetResumo());

        List<Document> totaisDocs = lista(resultado, "totais");
        Document totais = totaisDocs.isEmpty() ? new Document() : totaisDocs.get(0);
        List<Document> statusDocs = lista(resultado, "status");

        long pedidos = toLong(totais.get("pedidos"));
        long finalizados = toLong(totais.get("finalizados"));
        long cancelados = toLong(totais.get("cancelados"));
        long emAndamento = toLong(totais.get("emAndamento"));
        BigDecimal faturamento = toBigDecimal(totais.get("faturamento"));
        BigDecimal descontos = toBigDecimal(totais.get("descontos"));
        BigDecimal tempoMedio = toBigDecimal(totais.get("tempoMedioMinutos"));

        TotaisPeriodo anteriores = calcularTotais(periodoAnterior(periodo));
        Map<String, Long> contagemStatus = new HashMap<>();
        for (Document statusDoc : statusDocs) {
            String status = statusDoc.getString("_id");
            if (status != null) {
                contagemStatus.put(status, toLong(statusDoc.get("pedidos")));
            }
        }

        List<StatusResumoResponse> porStatus = Arrays.stream(StatusPedido.values())
                .map(status -> new StatusResumoResponse(status.name(),
                        contagemStatus.getOrDefault(status.name(), 0L)))
                .toList();

        return new ResumoDashboardResponse(
                new PeriodoResponse(periodo.inicio(), periodo.fim()),
                DashboardMath.arredondar(faturamento),
                pedidos,
                finalizados,
                DashboardMath.ticketMedio(faturamento, finalizados),
                cancelados,
                DashboardMath.percentual(cancelados, pedidos),
                DashboardMath.arredondar(descontos),
                emAndamento,
                DashboardMath.arredondar(tempoMedio),
                new ComparacaoResponse(
                        DashboardMath.variacaoPercentual(faturamento, anteriores.faturamento()),
                        DashboardMath.variacaoPercentual(
                                BigDecimal.valueOf(pedidos),
                                BigDecimal.valueOf(anteriores.pedidos()))),
                porStatus);
    }

    public VendasDashboardResponse vendas(LocalDate inicio, LocalDate fim, String agrupamento,
                                          String status, String tipoEntrega) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        String agrupamentoNormalizado = normalizarAgrupamento(agrupamento);
        StatusPedido statusNormalizado = normalizarStatus(status);
        TipoEntrega entregaNormalizada = normalizarTipoEntrega(tipoEntrega);

        List<Document> serieDocs = agregarLista(
                matchPeriodo(periodo, statusNormalizado, entregaNormalizada, true),
                new Document("$group", new Document("_id", dateToString())
                        .append("pedidos", new Document("$sum", 1))
                        .append("faturamento", new Document("$sum",
                                cond(eq("$status", StatusPedido.FINALIZADO.name()),
                                        valorMonetario("$valorTotal"), 0)))),
                new Document("$sort", new Document("_id", 1)));

        Map<LocalDate, DiaVenda> porDia = new HashMap<>();
        for (Document doc : serieDocs) {
            String data = doc.getString("_id");
            if (data == null) {
                continue;
            }
            porDia.put(LocalDate.parse(data),
                    new DiaVenda(toLong(doc.get("pedidos")), toBigDecimal(doc.get("faturamento"))));
        }

        return new VendasDashboardResponse(
                new PeriodoResponse(periodo.inicio(), periodo.fim()),
                agrupamentoNormalizado,
                montarSerie(periodo, agrupamentoNormalizado, porDia),
                calcularCategorias(periodo, statusNormalizado, entregaNormalizada),
                calcularTiposEntrega(periodo, statusNormalizado, entregaNormalizada));
    }

    public ProdutosDashboardResponse produtos(LocalDate inicio, LocalDate fim, String ordenarPor,
                                              Integer limit, String tipoProduto, String status,
                                              String tipoEntrega) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        String ordenacao = normalizarOrdenacao(ordenarPor);
        int limite = normalizarLimite(limit);
        StatusPedido statusNormalizado = normalizarStatus(status);
        TipoEntrega entregaNormalizada = normalizarTipoEntrega(tipoEntrega);

        Document match = matchPeriodo(periodo, statusNormalizado, entregaNormalizada, true);
        Document unwind = new Document("$unwind", new Document("path", "$itens"));
        List<Document> estagios = new ArrayList<>();
        estagios.add(match);
        estagios.add(unwind);
        if (tipoProduto != null && !tipoProduto.isBlank()) {
            estagios.add(new Document("$match",
                    new Document("itens.tipoProduto", tipoProduto.trim())));
        }

        Document group = new Document("$group", new Document("_id",
                new Document("id", ifNull("$itens.produtoId", "$itens.nomeProduto"))
                        .append("nome", ifNull("$itens.nomeProduto", "Produto removido")))
                .append("quantidade", new Document("$sum", ifNull("$itens.quantidade", 0)))
                .append("receita", new Document("$sum", valorMonetario("$itens.subtotal"))));

        Document facet = new Document("$facet",
                new Document("ranking", List.of(
                        group,
                        new Document("$sort", new Document(ordenacao, -1)),
                        new Document("$limit", limite)))
                        .append("totais", List.of(
                                new Document("$group", new Document("_id", null)
                                        .append("quantidadeTotal",
                                                new Document("$sum", ifNull("$itens.quantidade", 0)))
                                        .append("receitaTotal",
                                                new Document("$sum", valorMonetario("$itens.subtotal")))))));

        estagios.add(facet);
        Document resultado = agregarUnico(estagios.toArray(Document[]::new));

        List<Document> ranking = lista(resultado, "ranking");
        List<Document> totaisDocs = lista(resultado, "totais");
        Document totais = totaisDocs.isEmpty() ? new Document() : totaisDocs.get(0);
        long quantidadeTotal = toLong(totais.get("quantidadeTotal"));
        BigDecimal receitaTotal = toBigDecimal(totais.get("receitaTotal"));

        List<ProdutoVendidoResponse> itens = ranking.stream().map(doc -> {
            Document id = doc.get("_id", Document.class);
            String produtoId = id == null ? null : id.getString("id");
            String nome = id == null ? "Produto removido" : id.getString("nome");
            long quantidade = toLong(doc.get("quantidade"));
            BigDecimal receita = toBigDecimal(doc.get("receita"));
            return new ProdutoVendidoResponse(
                    produtoId,
                    nome,
                    quantidade,
                    DashboardMath.arredondar(receita),
                    DashboardMath.percentual(quantidade, quantidadeTotal),
                    DashboardMath.percentual(receita, receitaTotal));
        }).toList();

        return new ProdutosDashboardResponse(
                new PeriodoResponse(periodo.inicio(), periodo.fim()),
                ordenacao,
                itens);
    }

    public PaginaResponse<PedidoResumoResponse> pedidos(LocalDate inicio, LocalDate fim,
                                                        String status, String tipoEntrega,
                                                        int page, int size) {
        Periodo periodo = normalizarPeriodo(inicio, fim);
        StatusPedido statusNormalizado = normalizarStatus(status);
        TipoEntrega entregaNormalizada = normalizarTipoEntrega(tipoEntrega);
        int pagina = normalizarPagina(page);
        int tamanho = normalizarTamanhoPagina(size);

        Criteria criteria = Criteria.where("dataHoraEntrada")
                .gte(Date.from(periodo.from()))
                .lt(Date.from(periodo.to()));
        if (statusNormalizado != null) {
            criteria = criteria.and("status").is(statusNormalizado.name());
        }
        if (entregaNormalizada != null) {
            criteria = criteria.and("tipoEntrega").is(entregaNormalizada.name());
        }

        long total = mongoTemplate.count(new Query(criteria), Pedido.class, COLECAO_PEDIDOS);
        Query consulta = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"))
                .skip((long) pagina * tamanho)
                .limit(tamanho);
        List<Pedido> pedidos = mongoTemplate.find(consulta, Pedido.class, COLECAO_PEDIDOS);
        Map<String, String> nomesClientes = nomesClientes(pedidos);

        List<PedidoResumoResponse> conteudo = pedidos.stream()
                .map(pedido -> new PedidoResumoResponse(
                        pedido.getId(),
                        pedido.getClienteId(),
                        nomesClientes.getOrDefault(pedido.getClienteId(), "Cliente nao encontrado"),
                        pedido.getStatus(),
                        pedido.getTipoEntrega() == null ? "NAO_INFORMADO" : pedido.getTipoEntrega(),
                        pedido.getValorTotal() == null ? BigDecimal.ZERO : pedido.getValorTotal(),
                        pedido.getDataHoraEntrada(),
                        quantidadeItens(pedido),
                        pedido.getResponsavelRegistro()))
                .toList();

        int totalPaginas = tamanho == 0 ? 0 : (int) Math.ceil((double) total / tamanho);
        return new PaginaResponse<>(conteudo, pagina, tamanho, total, totalPaginas);
    }

    public FiltrosDashboardResponse filtros() {
        List<String> status = Arrays.stream(StatusPedido.values()).map(Enum::name).toList();
        List<String> tiposEntrega = Arrays.stream(TipoEntrega.values()).map(Enum::name).toList();
        List<String> tiposProduto = produtoRepository.findAll().stream()
                .map(Produto::getTipo)
                .filter(tipo -> tipo != null && !tipo.isBlank())
                .distinct()
                .sorted()
                .toList();
        return new FiltrosDashboardResponse(status, tiposEntrega, tiposProduto);
    }

    private TotaisPeriodo calcularTotais(Periodo periodo) {
        Document doc = agregarUnico(matchPeriodo(periodo, null, null, false), groupTotais());
        if (doc == null) {
            return TotaisPeriodo.ZERO;
        }
        return new TotaisPeriodo(
                toLong(doc.get("pedidos")),
                toLong(doc.get("finalizados")),
                toLong(doc.get("cancelados")),
                toLong(doc.get("emAndamento")),
                toBigDecimal(doc.get("faturamento")),
                toBigDecimal(doc.get("descontos")),
                toBigDecimal(doc.get("tempoMedioMinutos")));
    }

    private Document groupTotais() {
        return new Document("$group", new Document("_id", null)
                .append("pedidos", new Document("$sum", 1))
                .append("finalizados", new Document("$sum",
                        cond(eq("$status", StatusPedido.FINALIZADO.name()), 1, 0)))
                .append("cancelados", new Document("$sum",
                        cond(eq("$status", StatusPedido.CANCELADO.name()), 1, 0)))
                .append("emAndamento", new Document("$sum",
                        cond(new Document("$and", List.of(
                                        ne("$status", StatusPedido.FINALIZADO.name()),
                                        ne("$status", StatusPedido.CANCELADO.name()))),
                                1, 0)))
                .append("faturamento", new Document("$sum",
                        cond(eq("$status", StatusPedido.FINALIZADO.name()),
                                valorMonetario("$valorTotal"), 0)))
                .append("descontos", new Document("$sum",
                        cond(ne("$status", StatusPedido.CANCELADO.name()),
                                valorMonetario("$valorDesconto"), 0)))
                .append("tempoMedioMinutos", new Document("$avg",
                        new Document("$divide", List.of(
                                new Document("$subtract", List.of(
                                        "$dataHoraSaida", "$dataHoraEntrada")),
                                60000)))));
    }

    private Document facetResumo() {
        return new Document("$facet", new Document("totais", List.of(groupTotais()))
                .append("status", List.of(
                        new Document("$group", new Document("_id", "$status")
                                .append("pedidos", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1)))));
    }

    private List<CategoriaResponse> calcularCategorias(Periodo periodo, StatusPedido status,
                                                       TipoEntrega tipoEntrega) {
        List<Document> docs = agregarLista(
                matchPeriodo(periodo, status, tipoEntrega, true),
                new Document("$unwind", new Document("path", "$itens")),
                new Document("$group", new Document("_id",
                        ifNull("$itens.tipoProduto", "Nao informado"))
                        .append("quantidade", new Document("$sum", ifNull("$itens.quantidade", 0)))
                        .append("receita", new Document("$sum", valorMonetario("$itens.subtotal")))),
                new Document("$sort", new Document("receita", -1)));

        return docs.stream()
                .map(doc -> new CategoriaResponse(
                        doc.getString("_id"),
                        toLong(doc.get("quantidade")),
                        DashboardMath.arredondar(toBigDecimal(doc.get("receita")))))
                .toList();
    }

    private List<TipoEntregaResumoResponse> calcularTiposEntrega(Periodo periodo, StatusPedido status,
                                                                 TipoEntrega tipoEntrega) {
        List<Document> docs = agregarLista(
                matchPeriodo(periodo, status, tipoEntrega, true),
                new Document("$group", new Document("_id",
                        ifNull("$tipoEntrega", "NAO_INFORMADO"))
                        .append("pedidos", new Document("$sum", 1))
                        .append("faturamento", new Document("$sum",
                                cond(eq("$status", StatusPedido.FINALIZADO.name()),
                                        valorMonetario("$valorTotal"), 0)))),
                new Document("$sort", new Document("pedidos", -1)));

        return docs.stream()
                .map(doc -> new TipoEntregaResumoResponse(
                        doc.getString("_id"),
                        toLong(doc.get("pedidos")),
                        DashboardMath.arredondar(toBigDecimal(doc.get("faturamento")))))
                .toList();
    }

    private List<PontoSerieResponse> montarSerie(Periodo periodo, String agrupamento,
                                                 Map<LocalDate, DiaVenda> porDia) {
        if ("dia".equals(agrupamento)) {
            List<PontoSerieResponse> serie = new ArrayList<>();
            for (LocalDate data = periodo.inicio(); !data.isAfter(periodo.fim()); data = data.plusDays(1)) {
                DiaVenda dia = porDia.getOrDefault(data, DiaVenda.ZERO);
                serie.add(new PontoSerieResponse(
                        data.toString(),
                        FORMATO_DIA.format(data),
                        dia.pedidos(),
                        DashboardMath.arredondar(dia.faturamento())));
            }
            return serie;
        }

        Map<LocalDate, Acumulado> agrupado = new LinkedHashMap<>();
        for (LocalDate data = periodo.inicio(); !data.isAfter(periodo.fim()); data = data.plusDays(1)) {
            LocalDate chave = "semana".equals(agrupamento)
                    ? data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : data.withDayOfMonth(1);
            Acumulado acumulado = agrupado.computeIfAbsent(chave, ignorado -> new Acumulado());
            DiaVenda dia = porDia.getOrDefault(data, DiaVenda.ZERO);
            acumulado.pedidos += dia.pedidos();
            acumulado.faturamento = acumulado.faturamento.add(dia.faturamento());
        }

        List<PontoSerieResponse> serie = new ArrayList<>();
        for (Map.Entry<LocalDate, Acumulado> entrada : agrupado.entrySet()) {
            LocalDate chave = entrada.getKey();
            LocalDate inicioGrupo = chave.isBefore(periodo.inicio()) ? periodo.inicio() : chave;
            String rotulo;
            if ("semana".equals(agrupamento)) {
                LocalDate fimGrupo = chave.plusDays(6);
                if (fimGrupo.isAfter(periodo.fim())) {
                    fimGrupo = periodo.fim();
                }
                rotulo = FORMATO_DIA.format(inicioGrupo) + " - " + FORMATO_DIA.format(fimGrupo);
            } else {
                rotulo = MESES[chave.getMonthValue() - 1] + "/" + chave.getYear();
            }
            serie.add(new PontoSerieResponse(
                    inicioGrupo.toString(),
                    rotulo,
                    entrada.getValue().pedidos,
                    DashboardMath.arredondar(entrada.getValue().faturamento)));
        }
        return serie;
    }

    private Document matchPeriodo(Periodo periodo, StatusPedido status, TipoEntrega tipoEntrega,
                                  boolean excluirCancelados) {
        Document filtro = new Document("dataHoraEntrada",
                new Document("$gte", Date.from(periodo.from()))
                        .append("$lt", Date.from(periodo.to())));
        if (status != null) {
            filtro.append("status", status.name());
        } else if (excluirCancelados) {
            filtro.append("status", new Document("$ne", StatusPedido.CANCELADO.name()));
        }
        if (tipoEntrega != null) {
            filtro.append("tipoEntrega", tipoEntrega.name());
        }
        return new Document("$match", filtro);
    }

    private Periodo normalizarPeriodo(LocalDate inicio, LocalDate fim) {
        LocalDate hoje = LocalDate.now(ZONA);
        LocalDate fimNormalizado = fim == null ? hoje : fim;
        LocalDate inicioNormalizado = inicio == null ? fimNormalizado.minusDays(29) : inicio;

        if (inicioNormalizado.isAfter(fimNormalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "inicio deve ser anterior ou igual a fim");
        }
        long dias = ChronoUnit.DAYS.between(inicioNormalizado, fimNormalizado) + 1;
        if (dias > PERIODO_MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Periodo maximo de " + PERIODO_MAXIMO_DIAS + " dias");
        }
        return new Periodo(
                inicioNormalizado,
                fimNormalizado,
                inicioNormalizado.atStartOfDay(ZONA).toInstant(),
                fimNormalizado.plusDays(1).atStartOfDay(ZONA).toInstant(),
                (int) dias);
    }

    private Periodo periodoAnterior(Periodo periodo) {
        LocalDate anteriorFim = periodo.inicio().minusDays(1);
        LocalDate anteriorInicio = anteriorFim.minusDays(periodo.dias() - 1L);
        return normalizarPeriodo(anteriorInicio, anteriorFim);
    }

    private String normalizarAgrupamento(String agrupamento) {
        if (agrupamento == null || agrupamento.isBlank()) {
            return "dia";
        }
        String normalizado = removerAcentos(agrupamento.trim().toLowerCase(Locale.ROOT));
        return switch (normalizado) {
            case "dia", "dias" -> "dia";
            case "semana", "semanas" -> "semana";
            case "mes", "meses" -> "mes";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "agrupamento deve ser dia, semana ou mes");
        };
    }

    private String normalizarOrdenacao(String ordenarPor) {
        if (ordenarPor == null || ordenarPor.isBlank()) {
            return "quantidade";
        }
        String normalizado = removerAcentos(ordenarPor.trim().toLowerCase(Locale.ROOT));
        return switch (normalizado) {
            case "quantidade", "quantidadevendida" -> "quantidade";
            case "receita", "faturamento", "valor" -> "receita";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ordenarPor deve ser quantidade ou receita");
        };
    }

    private int normalizarLimite(Integer limit) {
        if (limit == null) {
            return 10;
        }
        if (limit < 1 || limit > LIMITE_MAXIMO_PRODUTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit deve estar entre 1 e " + LIMITE_MAXIMO_PRODUTOS);
        }
        return limit;
    }

    private int normalizarPagina(int page) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page deve ser maior ou igual a zero");
        }
        return page;
    }

    private int normalizarTamanhoPagina(int size) {
        if (size < 1 || size > TAMANHO_MAXIMO_PAGINA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA);
        }
        return size;
    }

    private StatusPedido normalizarStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StatusPedido.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status invalido");
        }
    }

    private TipoEntrega normalizarTipoEntrega(String tipoEntrega) {
        if (tipoEntrega == null || tipoEntrega.isBlank()) {
            return null;
        }
        try {
            return TipoEntrega.valueOf(tipoEntrega.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tipoEntrega invalido. Use ENTREGA, RETIRADA ou BALCAO");
        }
    }

    private static String removerAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private Map<String, String> nomesClientes(List<Pedido> pedidos) {
        Set<String> ids = pedidos.stream()
                .map(Pedido::getClienteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return clienteRepository.findAllById(ids).stream()
                .filter(cliente -> cliente.getId() != null)
                .collect(Collectors.toMap(
                        cliente -> cliente.getId(),
                        cliente -> cliente.getNome() == null ? "Cliente sem nome" : cliente.getNome(),
                        (primeiro, segundo) -> primeiro));
    }

    private int quantidadeItens(Pedido pedido) {
        if (pedido.getItens() == null) {
            return 0;
        }
        return pedido.getItens().stream()
                .filter(Objects::nonNull)
                .mapToInt(ItemPedido::getQuantidade)
                .sum();
    }

    private Document agregarUnico(Document... estagios) {
        Aggregation aggregation = aggregation(estagios);
        Document resultado = mongoTemplate
                .aggregate(aggregation, COLECAO_PEDIDOS, Document.class)
                .getUniqueMappedResult();
        return resultado;
    }

    private List<Document> agregarLista(Document... estagios) {
        Aggregation aggregation = aggregation(estagios);
        return mongoTemplate.aggregate(aggregation, COLECAO_PEDIDOS, Document.class)
                .getMappedResults();
    }

    private Aggregation aggregation(Document... estagios) {
        List<AggregationOperation> operacoes = Arrays.stream(estagios)
                .map(estagio -> (AggregationOperation) contexto -> estagio)
                .toList();
        return Aggregation.newAggregation(operacoes);
    }

    private static List<Document> lista(Document documento, String campo) {
        if (documento == null) {
            return List.of();
        }
        List<Document> valores = documento.getList(campo, Document.class);
        return valores == null ? List.of() : valores;
    }

    private static Document dateToString() {
        return new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", "$dataHoraEntrada")
                .append("timezone", ZONA.getId()));
    }

    private static Document eq(String campo, Object valor) {
        return new Document("$eq", List.of(campo, valor));
    }

    private static Document ne(String campo, Object valor) {
        return new Document("$ne", List.of(campo, valor));
    }

    private static Document cond(Document condicao, Object seVerdadeiro, Object seFalso) {
        return new Document("$cond", List.of(condicao, seVerdadeiro, seFalso));
    }

    private static Document ifNull(String campo, Object alternativa) {
        return new Document("$ifNull", List.of(campo, alternativa));
    }

    private static Document valorMonetario(String campo) {
        // Spring Data 4.4 grava BigDecimal como String; aceita também valores BSON numéricos.
        return new Document("$convert", new Document("input", campo)
                .append("to", "decimal")
                .append("onNull", 0));
    }

    private static long toLong(Object valor) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        if (valor instanceof Decimal128 decimal) {
            return decimal.longValue();
        }
        return 0L;
    }

    private static BigDecimal toBigDecimal(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        if (valor instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (valor instanceof Number numero) {
            return new BigDecimal(numero.toString());
        }
        try {
            return new BigDecimal(valor.toString());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private record Periodo(LocalDate inicio, LocalDate fim, Instant from, Instant to, int dias) {
    }

    private record TotaisPeriodo(long pedidos, long finalizados, long cancelados, long emAndamento,
                                 BigDecimal faturamento, BigDecimal descontos,
                                 BigDecimal tempoMedioMinutos) {
        private static final TotaisPeriodo ZERO = new TotaisPeriodo(
                0, 0, 0, 0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private record DiaVenda(long pedidos, BigDecimal faturamento) {
        private static final DiaVenda ZERO = new DiaVenda(
                0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private static final class Acumulado {
        private long pedidos;
        private BigDecimal faturamento = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
