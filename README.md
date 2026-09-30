# Pediu Patrão — Gestão de Pedidos para Pizzarias

O Pediu Patrão reúne o gerenciamento de pedidos, clientes, produtos e usuários em uma aplicação web. O sistema controla o fluxo dos pedidos, restringe operações por perfil e registra alterações importantes para auditoria.

O projeto atende operações de pizzaria, lanchonete e esfiharia com pedidos para entrega ou retirada.

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
  controller/   # HomeController (telas) + PedidoController, ClienteController, ProdutoController, UsuarioController, AuditoriaController
  service/      # PedidoService, ClienteService, ProdutoService, UsuarioService, AuditoriaService
  entity/       # Pedido, ItemPedido, Cliente, Produto, Usuario, StatusPedido, PerfilUsuario, RegistroAuditoria
  repository/   # Repositórios MongoDB
  dto/          # CriarPedidoRequest, ItemPedidoRequest, AplicarDescontoRequest, CancelarPedidoRequest
  config/       # SecurityConfig, DataInitializer, MongoInitConfig
src/main/resources/
  templates/    # layout, home, login, pedidos, clientes, produtos, usuarios, auditoria, configuracoes
  static/       # css, js, images, plugins
  application.properties
src/test/java/  # PedidoService, desconto/cancelamento, status, segurança, auditoria, perfil, DataInitializer
```

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
  "itens": [
    { "produtoId": "<P_ID>", "quantidade": 2 }
  ]
}
```

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

Os testes cobrem regras de status, desconto e cancelamento, permissões por perfil, auditoria de clientes e inicialização do banco.

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
- Relatórios de ticket médio, cancelamento e desconto por operador

## Suporte e licença

Projeto comercial. Para demonstração, implantação na sua loja ou customização (logo, cardápio, taxas, impressora), fale com o responsável pela implantação.

Todos os direitos reservados. Uso, cópia ou revenda sem autorização não são permitidos.
