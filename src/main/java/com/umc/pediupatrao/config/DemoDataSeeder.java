package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Popula a base com dados realistas para demonstração do dashboard.
 * Ative com o profile "demo": --spring.profiles.active=demo
 */
@Configuration
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Random RANDOM = new Random(20261030L);
    private static final int DIAS_DE_HISTORICO = 45;

    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(ClienteRepository clienteRepository,
                          ProdutoRepository produtoRepository,
                          PedidoRepository pedidoRepository,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        criarUsuariosDemo();

        if (pedidoRepository.count() > 0) {
            System.out.println("[demo] Base ja possui pedidos; seed de pedidos ignorado.");
            return;
        }

        List<Cliente> clientes = clienteRepository.count() == 0
                ? criarClientes()
                : clienteRepository.findAll();
        List<Produto> produtos = produtoRepository.count() == 0
                ? criarProdutos()
                : produtoRepository.findAll();

        if (clientes.isEmpty()) {
            clientes = criarClientes();
        }
        if (produtos.isEmpty()) {
            produtos = criarProdutos();
        }

        List<Pedido> pedidos = gerarPedidos(clientes, produtos);
        pedidoRepository.saveAll(pedidos);
        System.out.println("[demo] " + pedidos.size() + " pedidos de demonstracao criados.");
    }

    private void criarUsuariosDemo() {
        criarUsuario("gerente", "gerente123", "GERENTE");
        criarUsuario("atendente", "atendente123", "ATENDENTE");
    }

    private void criarUsuario(String username, String senha, String role) {
        if (usuarioRepository.findByUsername(username).isPresent()) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(senha));
        usuario.setRole(role);
        usuarioRepository.save(usuario);
        System.out.println("[demo] Usuario " + username + " criado.");
    }

    private List<Cliente> criarClientes() {
        Object[][] dados = {
                {"Ana Souza", "(11) 98888-1001", "Rua das Flores", "120", "Centro", "Sao Paulo", "SP", "01001-000"},
                {"Bruno Oliveira", "(11) 98888-1002", "Av. Paulista", "900", "Bela Vista", "Sao Paulo", "SP", "01310-100"},
                {"Carla Mendes", "(11) 98888-1003", "Rua Harmonia", "45", "Vila Madalena", "Sao Paulo", "SP", "05435-000"},
                {"Diego Ferreira", "(11) 98888-1004", "Rua do Comercio", "77", "Jardim Angela", "Sao Paulo", "SP", "04941-000"},
                {"Elisa Martins", "(11) 98888-1005", "Av. Ipiranga", "310", "Republica", "Sao Paulo", "SP", "01046-010"},
                {"Felipe Ramos", "(11) 98888-1006", "Rua Aurora", "58", "Santa Ifigenia", "Sao Paulo", "SP", "01209-000"},
                {"Gabriela Lima", "(11) 98888-1007", "Rua Girassol", "210", "Vila Madalena", "Sao Paulo", "SP", "05433-000"},
                {"Henrique Alves", "(11) 98888-1008", "Av. Reboucas", "1500", "Pinheiros", "Sao Paulo", "SP", "05402-200"}
        };

        List<Cliente> clientes = new ArrayList<>();
        for (Object[] dado : dados) {
            Cliente cliente = new Cliente();
            cliente.setNome((String) dado[0]);
            cliente.setTelefone((String) dado[1]);
            cliente.setLogradouro((String) dado[2]);
            cliente.setNumero((String) dado[3]);
            cliente.setBairro((String) dado[4]);
            cliente.setCidade((String) dado[5]);
            cliente.setEstado((String) dado[6]);
            cliente.setCep((String) dado[7]);
            cliente.setEndereco(dado[2] + ", " + dado[3] + " - " + dado[4]);
            clientes.add(cliente);
        }
        return clienteRepository.saveAll(clientes);
    }

    private List<Produto> criarProdutos() {
        Object[][] dados = {
                {"Pizza Margherita", "Pizza", 49.90},
                {"Pizza Calabresa", "Pizza", 52.90},
                {"Pizza Portuguesa", "Pizza", 56.90},
                {"Pizza Quatro Queijos", "Pizza", 59.90},
                {"Esfiha de Carne", "Esfiha", 8.50},
                {"Esfiha de Queijo", "Esfiha", 8.00},
                {"X-Burguer", "Lanche", 24.90},
                {"Batata Frita", "Porcao", 19.90},
                {"Refrigerante 2L", "Bebida", 12.00},
                {"Suco de Laranja 500ml", "Bebida", 9.50},
                {"Pudim", "Sobremesa", 14.00},
                {"Brownie", "Sobremesa", 16.00}
        };

        List<Produto> produtos = new ArrayList<>();
        for (Object[] dado : dados) {
            Produto produto = new Produto();
            produto.setNome((String) dado[0]);
            produto.setTipo((String) dado[1]);
            produto.setPreco((Double) dado[2]);
            produto.setAtivo(Boolean.TRUE);
            produtos.add(produto);
        }
        return produtoRepository.saveAll(produtos);
    }

    private List<Pedido> gerarPedidos(List<Cliente> clientes, List<Produto> produtos) {
        LocalDate hoje = LocalDate.now(ZONA);
        List<Pedido> pedidos = new ArrayList<>();

        for (int diasAtras = DIAS_DE_HISTORICO; diasAtras >= 0; diasAtras--) {
            LocalDate data = hoje.minusDays(diasAtras);
            int quantidadePedidos = 2 + RANDOM.nextInt(4);
            if (data.getDayOfWeek().getValue() >= 5) {
                quantidadePedidos += 2;
            }
            for (int indice = 0; indice < quantidadePedidos; indice++) {
                pedidos.add(gerarPedido(diasAtras, clientes, produtos));
            }
        }
        return pedidos;
    }

    private Pedido gerarPedido(int diasAtras, List<Cliente> clientes, List<Produto> produtos) {
        Cliente cliente = clientes.get(RANDOM.nextInt(clientes.size()));
        String tipoEntrega = sortearTipoEntrega();
        String status = sortearStatus(diasAtras, tipoEntrega);
        Instant entrada = sortearEntrada(diasAtras);

        List<Produto> disponiveis = new ArrayList<>(produtos);
        Collections.shuffle(disponiveis, RANDOM);
        int quantidadeItens = Math.min(1 + RANDOM.nextInt(3), disponiveis.size());

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalUnidades = 0;

        for (int indice = 0; indice < quantidadeItens; indice++) {
            Produto produto = disponiveis.get(indice);
            int quantidade = 1 + RANDOM.nextInt(3);
            double precoBase = produto.getPreco() == null ? 0.0 : produto.getPreco();
            BigDecimal preco = BigDecimal.valueOf(precoBase)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalItem = preco.multiply(BigDecimal.valueOf(quantidade))
                    .setScale(2, RoundingMode.HALF_UP);

            itens.add(new ItemPedido(
                    produto.getId(),
                    produto.getNome(),
                    produto.getTipo(),
                    quantidade,
                    preco,
                    subtotalItem));

            subtotal = subtotal.add(subtotalItem);
            totalUnidades += quantidade;
        }

        BigDecimal percentualDesconto = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorDesconto = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (RANDOM.nextInt(100) < 18) {
            percentualDesconto = RANDOM.nextBoolean()
                    ? new BigDecimal("5.00")
                    : new BigDecimal("10.00");
            valorDesconto = subtotal.multiply(percentualDesconto)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setTipoEntrega(tipoEntrega);
        pedido.setStatus(status);
        pedido.setItens(itens);
        pedido.setQuantidade(totalUnidades);
        pedido.setValorSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setPercentualDesconto(percentualDesconto);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(total);
        pedido.setResponsavelRegistro(RANDOM.nextBoolean() ? "atendente" : "gerente");
        pedido.setDataHoraEntrada(entrada);

        if (status.equals("FINALIZADO") || status.equals("RETIRADO")
                || status.equals("SAIU_PARA_ENTREGA")) {
            Instant saida = entrada.plusSeconds((15 + RANDOM.nextInt(45)) * 60L);
            pedido.setDataHoraSaida(saida.isAfter(Instant.now()) ? Instant.now() : saida);
            pedido.setResponsavelSaida("atendente");
        }

        if (status.equals("CANCELADO")) {
            Instant cancelamento = entrada.plusSeconds((5 + RANDOM.nextInt(20)) * 60L);
            pedido.setDataHoraCancelamento(cancelamento.isAfter(Instant.now()) ? Instant.now() : cancelamento);
            pedido.setResponsavelCancelamento("gerente");
            pedido.setMotivoCancelamento(RANDOM.nextBoolean()
                    ? "Cliente desistiu do pedido"
                    : "Endereco fora da area de entrega");
        }

        return pedido;
    }

    private String sortearTipoEntrega() {
        int sorteio = RANDOM.nextInt(100);
        if (sorteio < 55) {
            return "ENTREGA";
        }
        if (sorteio < 80) {
            return "RETIRADA";
        }
        return "BALCAO";
    }

    private String sortearStatus(int diasAtras, String tipoEntrega) {
        if (diasAtras == 0) {
            String[] recentes = tipoEntrega.equals("ENTREGA")
                    ? new String[]{"RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA"}
                    : new String[]{"RECEBIDO", "EM_PREPARACAO", "PRONTO", "RETIRADO"};
            return recentes[RANDOM.nextInt(recentes.length)];
        }
        if (diasAtras <= 2 && RANDOM.nextInt(100) < 30) {
            String[] recentes = tipoEntrega.equals("ENTREGA")
                    ? new String[]{"EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA"}
                    : new String[]{"EM_PREPARACAO", "PRONTO", "RETIRADO"};
            return recentes[RANDOM.nextInt(recentes.length)];
        }
        return RANDOM.nextInt(100) < 88 ? "FINALIZADO" : "CANCELADO";
    }

    private Instant sortearEntrada(int diasAtras) {
        if (diasAtras == 0) {
            return Instant.now().minusSeconds((10 + RANDOM.nextInt(180)) * 60L);
        }
        LocalDate data = LocalDate.now(ZONA).minusDays(diasAtras);
        int hora = 11 + RANDOM.nextInt(11);
        int minuto = RANDOM.nextInt(60);
        return data.atTime(hora, minuto).atZone(ZONA).toInstant();
    }
}
