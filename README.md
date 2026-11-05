# Pediu Patrão — Gestão de Pedidos para Pizzarias

O Pediu Patrão reúne o gerenciamento de pedidos, clientes, produtos e usuários em uma aplicação web. O sistema controla o fluxo dos pedidos, restringe operações por perfil e registra alterações importantes para auditoria.

O projeto atende operações de pizzaria, lanchonete e esfiharia com pedidos para entrega, retirada ou balcão. O dashboard gerencial consolida vendas, status e produtos mais vendidos para `ADMIN` e `GERENTE`.

## Para quem é

- Pizzarias e operações de delivery
- Operações que precisam de balcão + entrega + retirada no mesmo fluxo
- Equipes que precisam controlar descontos, cancelamentos e acessos por perfil

## Principais alterações realizadas

- O acesso passou a distinguir `ADMIN`, `GERENTE` e `ATENDENTE`, com autorização das operações no backend e validação dos perfis cadastrados.
- O pedido passou a guardar itens, valores calculados no servidor, responsável e horários de entrada e saída, além de validar cada transição de status.
- Descontos e cancelamentos passaram a ser operações exclusivas do GERENTE, com limites e justificativa conforme a operação.
- As mudanças em pedidos e clientes passaram a gerar registros persistentes de auditoria com usuário, perfil, data/hora e valores anteriores e novos.
- O controle de versão do pedido passou a impedir que alterações concorrentes sobrescrevam dados silenciosamente.
- O dashboard gerencial passou a expor KPIs, gráficos, ranking de produtos e filtros por meio de uma API REST própria.
- Os pedidos passaram a registrar `tipoEntrega` (`ENTREGA`, `RETIRADA`, `BALCAO`) e os itens passaram a guardar o snapshot `tipoProduto`, preservando o histórico do cardápio.

## Requisitos implementados

### RF02 e RF03 — Pedidos
- Criação com cliente + itens do cardápio, subtotal e total calculados no backend
- Máquina de estados: `RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA / RETIRADO → FINALIZADO`, além de `CANCELADO`
- Registro automático de responsável, entrada, saída e cancelamento
- Listagem operacional + formulário de novo pedido com clientes e produtos ativos
- API REST em `/api/pedidos` para as operações de pedido

### RF04 — Descontos e cancelamentos

- Descontos entre 0,01% e 20% somente pelo GERENTE e antes da saída ou retirada
- Cancelamento somente pelo GERENTE, antes da saída ou retirada, com justificativa, responsável e data/hora
- Pedidos finalizados ou cancelados não aceitam novas transições

### Clientes
- Cadastro com nome, telefone e endereço completo: CEP, logradouro, número, complemento, bairro, cidade, UF
- Edição e busca para reutilizar no pedido sem redigitar

### Cardápio / Produtos
- Produtos por tipo: Pizza, Esfiha, Bebida, Lanche
- Preço e flag ativo/inativo — produto inativo não entra em pedido novo

### RF01 — Equipe / Usuários
- CRUD de usuários com senha criptografada (BCrypt)
- Perfis `ADMIN`, `GERENTE`, `ATENDENTE`

### RF05 — Auditoria

- Tela dedicada para admin e gerente
- Registra eventos de pedidos e clientes, incluindo alterações de campos quando disponíveis

### Dashboard gerencial — RF01 a RF07

- **RF01:** KPIs de faturamento, pedidos, ticket médio, cancelamentos, andamento, tempo médio, descontos e finalizados, com comparação ao período anterior.
- **RF02:** gráficos de evolução das vendas, receita por categoria e pedidos por tipo de entrega.
- **RF03:** distribuição dos pedidos por status e tabela paginada com status destacado.
- **RF04:** ranking de produtos por quantidade ou receita, com participação percentual.
- **RF05:** filtros de período, agrupamento, status, tipo de entrega, ordenação e limite.
- **RF06:** API REST em `/api/dashboard`, consumida pelo frontend via `fetch`.
- **RF07:** atualização manual e automática (60s), barra de progresso, estados vazios e tratamento de erros com nova tentativa.

Documentação detalhada: [`docs/requisitos.md`](docs/requisitos.md) e [`docs/api-dashboard.md`](docs/api-dashboard.md).

## Perfis de acesso

| Recurso | ATENDENTE | GERENTE | ADMIN |
| --- | --- | --- | --- |
| Criar pedido (`POST /api/pedidos` + `/pedidos/novo`) | Sim | Não | Não |
| Listar pedidos | Sim | Sim | Sim |
| Avançar status | Sim | Sim | Não via API* |
| Aplicar desconto 0,01%–20% | Não | Sim | Não |
| Cancelar com justificativa | Não | Sim | Não |
| Clientes listar | Sim | Sim | Não via API |
| Clientes criar/editar | Sim | Não | Não |
| Clientes excluir | Não | Sim | Não |
| API de produtos (`/api/produtos`) | Sem restrição específica por perfil** | Sem restrição específica por perfil** | Sem restrição específica por perfil** |
| Dashboard (`/dashboard` + `/api/dashboard/**`) | Não (`403`) | Sim | Sim |
| Usuários | Não | Não | Total |
| Auditoria | Não | Sim | Sim |

\* `PUT /api/pedidos/{id}/status` aceita `GERENTE` e `ATENDENTE`.
\** As rotas de produtos exigem autenticação, mas não restringem o perfil. Revise essa permissão antes de usar o sistema em produção.

## Stack

- Java 21, Spring Boot 3.4.4, Maven
- Spring Web, Spring Data MongoDB, Spring Security, Validation
- Thymeleaf + Layout Dialect + Bootstrap 5 + Bootstrap Icons
- MongoDB (local ou Atlas)
- Lombok, Spring Boot Test + Spring Security Test

## Estrutura do projeto

```
src/main/java/com/umc/pediupatrao/
  controller/   # HomeController (telas) + PedidoController, ClienteController, ProdutoController, UsuarioController, AuditoriaController, DashboardController
  service/      # PedidoService, ClienteService, ProdutoService, UsuarioService, AuditoriaService, DashboardService, DashboardMath
  entity/       # Pedido, ItemPedido, Cliente, Produto, Usuario, StatusPedido, TipoEntrega, PerfilUsuario, RegistroAuditoria
  repository/   # Repositórios MongoDB
  dto/          # CriarPedidoRequest, ItemPedidoRequest, AplicarDescontoRequest, CancelarPedidoRequest, dto/dashboard
  config/       # SecurityConfig, DataInitializer, MongoInitConfig, DemoDataSeeder
src/main/resources/
  templates/    # layout, home, login, dashboard, pedidos, clientes, produtos, usuarios, auditoria, configuracoes
  static/       # css, js, images, plugins (Chart.js 2.9.4 local)
  application.properties
src/test/java/  # PedidoService, desconto/cancelamento, status, segurança, auditoria, perfil, DataInitializer, dashboard
docs/           # requisitos, API do dashboard e evidências de testes
```

## Documentação

- [`docs/requisitos.md`](docs/requisitos.md) — requisitos funcionais e não funcionais, regras de cálculo e decisões.
- [`docs/api-dashboard.md`](docs/api-dashboard.md) — endpoints, parâmetros, exemplos de resposta e erros.
- [`docs/evidencias-testes.md`](docs/evidencias-testes.md) — resultados da suíte, cobertura e checklist manual.

### Entrega da atividade e Gitflow

| Entregável | Localização |
| --- | --- |
| Requisitos funcionais RF01–RF07 e não funcionais | [`docs/requisitos.md`](docs/requisitos.md) |
| Endpoints, parâmetros, exemplos e erros | [`docs/api-dashboard.md`](docs/api-dashboard.md) |
| Testes e evidências | [`docs/evidencias-testes.md`](docs/evidencias-testes.md) |
| Dashboard integrado ao MongoDB | `/dashboard` e `/api/dashboard/**` |
| Implementação da API em branch própria | `feature/dashboard-api` (commit `c4f3d17`) |
| Integração e release | `develop`, `feature/dashboard-ui`, `feature/dashboard-docs`, `release/dashboard-1.0` e `main` |
| Correções para a entrega | `fix/dashboard-entrega`, criada a partir de `develop` |
| Apresentação de até 3 minutos | Gravação e publicação do vídeo pendentes |

As branches e merges preservam a separação entre desenvolvimento, funcionalidades e release. Para consultar o histórico: `git log --graph --oneline --all`.

## Executar localmente

### Pré-requisitos

- Java 21
- Maven Wrapper incluso (`mvnw` / `mvnw.cmd`, sem instalar Maven)
- MongoDB local ou uma instância do MongoDB Atlas

### 1. Configurar ambiente

Não commite credencial. Use variáveis de ambiente:

| Variável | Valor padrão | Descrição |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/pizzaria` | Conexão MongoDB |
| `MONGODB_DATABASE` | `pizzaria` | Banco de dados |
| `APP_ADMIN_USERNAME` | `admin` | Admin criado no primeiro boot |
| `APP_ADMIN_PASSWORD` | `teste123` | Troque em produção |

Windows (PowerShell):

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/pizzaria"
$env:APP_ADMIN_USERNAME="admin"
$env:APP_ADMIN_PASSWORD="troque-aqui"
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
export MONGODB_URI="mongodb://localhost:27017/pizzaria"
export APP_ADMIN_USERNAME="admin"
export APP_ADMIN_PASSWORD="troque-aqui"
./mvnw spring-boot:run
```

### 2. Acessar

- App: `http://localhost:8080`
- Login: `/login`
- No primeiro acesso, use o administrador criado pelo `DataInitializer`. O perfil `ADMIN` pode alterar essa senha na área de usuários.

### 2.1 Dados de demonstração (opcional)

Para popular a base com clientes, produtos e cerca de 200 pedidos dos últimos 45 dias:

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/pizzaria"
$env:MONGODB_DATABASE="pediupatrao_demo"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

O profile `demo` cria os usuários:

| Perfil | Usuário | Senha |
| --- | --- | --- |
| GERENTE | `gerente` | `gerente123` |
| ATENDENTE | `atendente` | `atendente123` |

O seed é idempotente para pedidos: se a coleção `pedidos` já tiver registros, nenhum pedido novo é criado. Os usuários de demonstração são criados apenas se ainda não existirem.

Use um banco exclusivo para demonstração. As contas e os dados inseridos permanecem no banco após desativar o profile. O administrador configurado é criado antes das contas demo; se seu nome for `gerente` ou `atendente`, esse nome conserva o perfil `ADMIN` e a senha configurada.

### 3. Fluxo de demonstração

1. Entre como `ADMIN` e crie usuários `GERENTE` e `ATENDENTE` em `/usuarios`. Cadastre produtos em `/produtos` para os pedidos.
2. Use **Sair** e entre como `ATENDENTE`. Cadastre um cliente, altere seu telefone ou endereço e crie dois pedidos em `/pedidos/novo`.
3. No primeiro pedido, avance `RECEBIDO → EM_PREPARACAO → PRONTO`. Demonstre um bloqueio real da API: no console do navegador, execute `fetch('/api/auditoria').then(r => console.log(r.status))`. O retorno esperado para `ATENDENTE` é `403`.
4. Ainda como `ATENDENTE`, tente aplicar desconto pela API ao primeiro pedido. Substitua `ID_DO_PEDIDO` pelo ID exibido na lista. Inclua o token CSRF para que o `403` comprove a restrição de perfil:

   ```javascript
   const token = document.querySelector('meta[name="_csrf"]').content;
   const header = document.querySelector('meta[name="_csrf_header"]').content;
   fetch('/api/pedidos/ID_DO_PEDIDO/desconto', {
     method: 'PUT',
     headers: { 'Content-Type': 'application/json', [header]: token },
     body: JSON.stringify({ percentual: 10 })
   }).then(r => console.log(r.status)); // 403
   ```

5. Use **Sair** e entre como `GERENTE`. Aplique 10% de desconto ao primeiro pedido, marque `SAIU_PARA_ENTREGA` e depois `FINALIZADO`. Cancele o segundo pedido antes da saída, informando uma justificativa.
6. Em `/auditoria`, confira os registros das ações gravadas: criação e transições dos pedidos, desconto, cancelamento e alteração do cliente. Os identificadores dos registros devem corresponder aos pedidos e ao cliente mostrados no vídeo.
7. Use **Sair** e entre novamente como `ADMIN` para mostrar a autenticação do terceiro perfil e a consulta à auditoria.
8. Com o profile `demo`, entre como `GERENTE` e abra `/dashboard`. Percorra os KPIs, aplique filtros de 7/30/90 dias, alterne o agrupamento, o ranking e a paginação. Ative a atualização automática e simule uma falha de rede no DevTools para ver o alerta e o botão **Tentar novamente**.
9. Em uma janela anônima, entre como `ATENDENTE` e acesse `/dashboard` diretamente: o retorno deve ser `403`. Sem sessão, `GET /api/dashboard/resumo` deve retornar `401`.

## API do dashboard

Base: `/api/dashboard`. Somente leitura, disponível para `ADMIN` e `GERENTE`.

```http
GET /api/dashboard/resumo?inicio=2026-10-01&fim=2026-10-30
GET /api/dashboard/vendas?inicio=2026-10-01&fim=2026-10-30&agrupamento=semana
GET /api/dashboard/produtos?inicio=2026-10-01&fim=2026-10-30&ordenarPor=receita&limit=10
GET /api/dashboard/pedidos?inicio=2026-10-01&fim=2026-10-30&page=0&size=10
GET /api/dashboard/filtros
```

Parâmetros, exemplos de resposta, regras de cálculo e erros: [`docs/api-dashboard.md`](docs/api-dashboard.md).

## API de pedidos

Base: `/api/pedidos`. As rotas usam JSON, exigem autenticação e respeitam as permissões descritas na tabela de perfis.

```http
POST /api/pedidos
PUT /api/pedidos/{id}/status?status=EM_PREPARACAO
PUT /api/pedidos/{id}/desconto
PUT /api/pedidos/{id}/cancelar
GET /api/pedidos
```

Exemplo criar:

```json
{
  "clienteId": "<K_ID>",
  "tipoEntrega": "ENTREGA",
  "itens": [
    { "produtoId": "<P_ID>", "quantidade": 2 }
  ]
}
```

`tipoEntrega` é opcional; quando omitido, o pedido assume `BALCAO`. Valores aceitos: `ENTREGA`, `RETIRADA` e `BALCAO`.

Exemplo desconto (gerente):

```json
{ "percentual": 10.00 }
```

Exemplo cancelamento (gerente):

```json
{ "justificativa": "Cliente desistiu antes do preparo" }
```

## Testes

```bash
./mvnw test
# Windows: .\mvnw.cmd test
```

A suíte tem **163 testes**, incluindo cinco de integração que exigem `DASHBOARD_TEST_MONGODB_URI`. Sem essa variável, os cinco casos são ignorados e os outros 158 são executados. Use Java 21, a versão definida pelo projeto.

```powershell
$env:DASHBOARD_TEST_MONGODB_URI="mongodb://localhost:27017"
.\mvnw.cmd test
```

Os testes de integração criam e removem bases aleatórias com prefixo `dashboard_test_`; não usam o banco indicado na URI. A execução cobre valores monetários em texto e BSON numérico, agregações reais, filtros, ranking, paginação e períodos vazios. Use uma instância local de testes.

Evidências detalhadas: [`docs/evidencias-testes.md`](docs/evidencias-testes.md).

## Segurança e produção

- Senhas com BCrypt, login via form em `/login`, logout em `/logout`
- CSRF habilitado nas telas Thymeleaf
- `spring.thymeleaf.cache=false` está configurado para desenvolvimento; habilite o cache em produção
- Antes de publicar, troque `APP_ADMIN_PASSWORD`, configure uma instância persistente do MongoDB, use HTTPS, defina uma rotina de backup e restrinja o gerenciamento de produtos

## Roadmap

- Impressão de cupom / cozinha (KDS)
- Taxas de entrega por bairro
- Integração iFood / WhatsApp
- Multi-loja com isolamento por unidade
- Metas de vendas e comparativos por operador
- Exportação de relatórios do dashboard

## Suporte e licença

Projeto comercial. Para demonstração, implantação na sua loja ou customização (logo, cardápio, taxas, impressora), fale com o responsável pela implantação.

Todos os direitos reservados. Uso, cópia ou revenda sem autorização não são permitidos.
