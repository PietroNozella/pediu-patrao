# Requisitos do Dashboard — Pediu Patrão

## 1. Contexto e objetivo

O dashboard é a visão gerencial do Pediu Patrão. Ele consolida os pedidos registrados no MongoDB e apresenta indicadores de vendas, status operacional, produtos mais vendidos e filtros de consulta para `ADMIN` e `GERENTE`.

O objetivo é permitir que a gestão acompanhe o desempenho da operação sem precisar percorrer a lista completa de pedidos, mantendo o atendente focado no fluxo de criação e andamento dos pedidos.

## 2. Escopo

### Dentro do escopo

- Indicadores gerais de vendas (RF01).
- Gráficos de vendas por período (RF02).
- Distribuição dos pedidos por status (RF03).
- Ranking dos produtos mais vendidos (RF04).
- Filtros de consulta por período, status, tipo de entrega, agrupamento e ranking (RF05).
- API REST própria do dashboard, consumida pelo frontend via HTTP (RF06).
- Atualização dos dados, estados de carregamento, vazio e erro (RF07).
- Controle de acesso por perfil.
- Documentação da API, requisitos, evidências de teste e apresentação de demonstração.

### Fora do escopo

- Meios de pagamento, conciliação financeira e emissão fiscal.
- Cadastro ou edição de pedidos pelo dashboard.
- Exportação de relatórios em PDF/Excel.
- Dashboard em tempo real por WebSocket.
- Aplicativo mobile nativo.
- Definição de metas, comissões ou custos.

## 3. Atores e permissões

| Ator | Acesso ao dashboard | Observação |
| --- | --- | --- |
| `ADMIN` | Sim | Acesso completo de leitura. |
| `GERENTE` | Sim | Acesso completo de leitura. |
| `ATENDENTE` | Não | Recebe `403 Forbidden` na página e na API. |
| Visitante não autenticado | Não | A página redireciona para `/login`; a API responde `401 Unauthorized`. |

A autorização é aplicada no backend por `@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")` na página (`/dashboard`) e no controller da API (`/api/dashboard`). O frontend também oculta o link do menu para quem não tem permissão, mas essa ocultação é apenas de usabilidade; a decisão de acesso é sempre do servidor.

## 4. Requisitos funcionais

### RF01 — Indicadores gerais de vendas

**Descrição:** apresentar os principais números da operação no período selecionado.

**Indicadores obrigatórios:**

- Faturamento.
- Quantidade de pedidos.
- Ticket médio.
- Pedidos finalizados.
- Pedidos cancelados e taxa de cancelamento.
- Pedidos em andamento.
- Tempo médio entre entrada e saída.
- Total de descontos concedidos.
- Comparação de faturamento e pedidos com o período anterior de mesma duração.

**Critérios de aceite:**

- O faturamento considera somente pedidos com status `FINALIZADO`.
- Pedidos `CANCELADO` não entram no faturamento, no ticket médio, nos descontos nem nos gráficos de produtos/categorias quando não há filtro explícito de status.
- O ticket médio é `faturamento / quantidade de pedidos finalizados`, com duas casas decimais; quando não há finalizados, retorna `0,00`.
- A taxa de cancelamento é `cancelados / pedidos * 100`, com duas casas decimais.
- A variação percentual é nula quando o período anterior não possui base de comparação.
- Todos os valores monetários são retornados em BRL e formatados no frontend com `Intl.NumberFormat('pt-BR')`.

### RF02 — Gráficos de vendas

**Descrição:** exibir a evolução do faturamento e do volume de pedidos ao longo do período.

**Critérios de aceite:**

- O gráfico principal combina barras de faturamento com uma linha de quantidade de pedidos.
- O agrupamento pode ser por dia, semana ou mês.
- Semanas começam na segunda-feira.
- O período sem movimentação é preenchido com zeros para manter a série contínua.
- A receita da série considera somente pedidos `FINALIZADO`; a linha de pedidos considera os pedidos não cancelados do filtro.
- Há gráficos complementares de receita por categoria de produto e pedidos por tipo de entrega.
- Os gráficos usam Chart.js 2.9.4 empacotado localmente em `/plugins/chart.js/Chart.min.js`, sem dependência de CDN para os gráficos.

### RF03 — Status dos pedidos

**Descrição:** mostrar a distribuição dos pedidos por status no período.

**Critérios de aceite:**

- São exibidos os status `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO` e `CANCELADO`.
- Status sem pedidos podem ser omitidos do gráfico, mas permanecem disponíveis no filtro.
- A tabela de pedidos do período exibe o status com destaque visual consistente.
- O filtro de status afeta os gráficos, o ranking e a tabela, mantendo os KPIs gerais do período.

### RF04 — Produtos mais vendidos

**Descrição:** ranquear os produtos por quantidade vendida ou por receita.

**Critérios de aceite:**

- O ranking pode ser ordenado por `quantidade` ou `receita`.
- O limite é configurável entre 1 e 50 itens; a interface oferece Top 5, Top 10 e Top 20.
- Cada item apresenta nome, quantidade, receita e participação percentual no total.
- Itens de pedidos cancelados são excluídos do ranking quando não há filtro explícito de status.
- O tipo do produto é lido do snapshot `itens.tipoProduto`, preservando o histórico mesmo se o cadastro do produto mudar.
- Itens legados sem snapshot aparecem como `Nao informado` na categoria e não são retornados ao filtrar por um tipo específico.

### RF05 — Filtros de consulta

**Descrição:** permitir que o gestor ajuste a análise sem sair da página.

**Filtros obrigatórios:**

- Data inicial e data final (`AAAA-MM-DD`).
- Agrupamento: `dia`, `semana` ou `mes`.
- Status do pedido.
- Tipo de entrega: `ENTREGA`, `RETIRADA`, `BALCAO`.
- Ordenação do ranking: `quantidade` ou `receita`.
- Limite do ranking.
- Paginação da tabela de pedidos.

**Critérios de aceite:**

- O período padrão é os últimos 30 dias, incluindo hoje.
- O período máximo aceito é de 366 dias.
- `inicio` posterior a `fim` retorna `400 Bad Request`.
- Valores inválidos de status, tipo de entrega, agrupamento, ordenação, limite ou paginação retornam `400 Bad Request` com `ProblemDetail`.
- A interface valida o período antes de chamar a API e exibe a mensagem retornada pelo backend.
- Há atalhos para 7, 30 e 90 dias e para o mês atual.
- O botão de limpar restaura o período padrão e os filtros iniciais.

### RF06 — Integração via API

**Descrição:** o dashboard deve consumir uma API HTTP própria, e não acessar repositórios ou modelos diretamente na renderização da página.

**Critérios de aceite:**

- A página `/dashboard` é um shell Thymeleaf; os dados são carregados por `fetch` a partir de `/api/dashboard/**`.
- A API é documentada em [`api-dashboard.md`](api-dashboard.md), com parâmetros, exemplos, respostas e erros.
- As respostas são JSON e os erros de negócio usam `application/problem+json` (RFC 7807).
- A API é somente leitura (`GET`).
- O frontend trata `401` redirecionando para o login e `403` exibindo mensagem de permissão.

### RF07 — Atualização e tratamento de erros

**Descrição:** manter os dados atualizados e dar feedback claro ao usuário.

**Critérios de aceite:**

- O botão **Atualizar** recarrega todos os blocos mantendo os filtros e a página atual.
- A opção **Atualizar a cada 60s** ativa recarga automática; a recarga é pausada quando a aba está oculta.
- O horário da última atualização é exibido no cabeçalho.
- Durante a carga, uma barra de progresso é exibida e os controles são desabilitados.
- Em caso de erro, um alerta apresenta a mensagem do backend e um botão **Tentar novamente**.
- Estados vazios são exibidos quando não há pedidos, produtos ou dados suficientes.
- Falhas de rede não apagam os dados já carregados; o alerta permanece visível para nova tentativa.

## 5. Requisitos não funcionais

| Código | Requisito | Como foi atendido |
| --- | --- | --- |
| RNF01 | Desempenho | Agregações executadas no MongoDB; índices em `dataHoraEntrada`, `status + dataHoraEntrada`, `clienteId` e `produtos.tipo`; período máximo de 366 dias; paginação da tabela. |
| RNF02 | Segurança | Sessão autenticada, autorização por perfil no backend, `401` para API anônima, `403` para perfil sem permissão e CSRF mantido nas rotas de escrita existentes. |
| RNF03 | Usabilidade | Layout responsivo com Bootstrap 5, filtros agrupados, atalhos de período, estados de carregamento/vazio/erro, rótulos acessíveis e formatação pt-BR. |
| RNF04 | Manutenibilidade | Separação entre `DashboardController`, `DashboardService`, DTOs, página Thymeleaf e JavaScript; pipelines de agregação isolados no serviço; testes automatizados. |
| RNF05 | Compatibilidade | Java 21, Spring Boot 3.4.4, MongoDB, Chart.js 2.9.4 local e navegadores modernos com suporte a `fetch`, `Intl` e `URLSearchParams`. |
| RNF06 | Observabilidade | Erros inesperados são logados no backend; respostas de erro incluem `title`, `detail`, `status` e `instance`; a interface mostra o horário da última atualização. |
| RNF07 | Integridade dos dados | `tipoEntrega` validado por enum; `tipoProduto` gravado como snapshot no item; migração de pedidos legados; índices criados na inicialização. |
| RNF08 | Privacidade | O dashboard expõe dados agregados e apenas nome/identificador do cliente para perfis gerenciais; não retorna senha, CPF ou dados de pagamento. |
| RNF09 | Disponibilidade da informação | Atualização manual e automática a cada 60 segundos; nova tentativa em caso de erro; nenhum cache de resposta no frontend. |
| RNF10 | Documentação | Requisitos, API e evidências de teste versionados em `docs/`. |

## 6. Definições de cálculo

Todas as datas de referência usam o fuso `America/Sao_Paulo`. O período é aplicado sobre `dataHoraEntrada` no intervalo `[inicio 00:00, fim + 1 dia 00:00)`.

| Indicador | Regra |
| --- | --- |
| Faturamento | Soma de `valorTotal` dos pedidos `FINALIZADO`. |
| Pedidos | Quantidade de pedidos no período (inclui cancelados). |
| Finalizados | Pedidos com status `FINALIZADO`. |
| Ticket médio | `faturamento / finalizados`, ou `0,00` sem finalizados. |
| Cancelados | Pedidos com status `CANCELADO`. |
| Taxa de cancelamento | `cancelados / pedidos * 100`, ou `0,00` sem pedidos. |
| Em andamento | Pedidos com status diferente de `FINALIZADO` e `CANCELADO`. |
| Tempo médio | Média de `dataHoraSaida - dataHoraEntrada` dos pedidos com saída registrada. |
| Descontos | Soma de `valorDesconto` dos pedidos não cancelados. |
| Comparação | Mesma duração imediatamente anterior; variação percentual; nula se a base anterior for zero. |

## 7. Alterações no modelo de dados

### `Pedido.tipoEntrega`

- Tipo: `String` com valores `ENTREGA`, `RETIRADA` ou `BALCAO`.
- Pedidos criados sem o campo assumem `BALCAO`.
- Valores inválidos na criação retornam `400 Bad Request`.
- Pedidos legados sem o campo aparecem como `NAO_INFORMADO` nos gráficos e não são retornados ao filtrar por um tipo específico.

### `ItemPedido.tipoProduto`

- Tipo: `String` copiada de `Produto.tipo` no momento da criação do pedido.
- Preserva a categoria histórica mesmo que o produto seja editado ou removido.
- O construtor de cinco argumentos foi mantido para compatibilidade; o construtor de seis argumentos grava o snapshot.

## 8. Decisões de projeto

1. **Agregações no MongoDB:** os cálculos são feitos com `MongoTemplate` e pipelines `Document`, evitando carregar todos os pedidos na JVM.
2. **API dedicada:** o dashboard não reutiliza a listagem completa de pedidos; cada endpoint retorna apenas o necessário para o bloco correspondente.
3. **Thymeleaf + JavaScript:** a página entrega o shell e o JavaScript busca os dados via `fetch`, atendendo ao requisito de integração HTTP.
4. **Chart.js 2.9.4 local:** a versão já presente no projeto foi mantida para evitar dependência de CDN e preservar compatibilidade com os plugins existentes.
5. **Erros padronizados:** `ProblemDetail` é usado para erros de validação e execução do dashboard; `401` é emitido pelo Spring Security antes do controller.
6. **Dados de demonstração:** o profile `demo` cria usuários, clientes, produtos e pedidos realistas para viabilizar a apresentação e a inspeção manual.
7. **Compatibilidade monetária:** os pipelines convertem valores para decimal antes das somas. Isso permite consultar os valores `BigDecimal` persistidos como texto pelo Spring Data 4.4 e documentos com tipos numéricos BSON, sem migração ou alteração dos registros existentes.

## 9. Critérios de aceite consolidados

- [x] `ADMIN` e `GERENTE` acessam `/dashboard` e `/api/dashboard/**`.
- [x] `ATENDENTE` recebe `403` na página e na API.
- [x] Visitante não autenticado é redirecionado ao login na página e recebe `401` na API.
- [x] KPIs, gráficos, ranking, filtros e tabela são carregados via API.
- [x] Faturamento considera apenas `FINALIZADO`.
- [x] Cancelados são excluídos dos indicadores de receita.
- [x] Fuso horário e intervalo de datas são aplicados no backend.
- [x] Erros de validação retornam `ProblemDetail` com `400`.
- [x] Atualização manual e automática estão disponíveis.
- [x] Testes automatizados cobrem cálculos, validações, segurança, controller e dados de demonstração.
- [x] Documentação de requisitos, API e evidências de teste foi adicionada.

## 10. Riscos e limitações

- **Volume de dados:** o período máximo de 366 dias e os índices reduzem o custo, mas bases muito grandes podem exigir índices compostos adicionais ou pré-agregação.
- **Pedidos legados:** registros antigos sem `tipoEntrega` ou `tipoProduto` ficam fora de alguns filtros e são agrupados como `NAO_INFORMADO`/`Nao informado`.
- **Fuso horário:** o backend usa `America/Sao_Paulo`; o frontend envia datas sem horário, portanto o recorte é sempre por dia local da operação.
- **Tempo médio:** considera qualquer pedido com `dataHoraSaida`, não apenas `FINALIZADO`; o rótulo da interface explicita “Entrada até a saída”.
- **Demonstração local:** a execução completa depende de uma instância MongoDB acessível.
- **Banco demo:** execute o profile em uma base exclusiva. Desativá-lo ou reverter o código não remove contas nem pedidos inseridos. O administrador configurado tem prioridade sobre nomes coincidentes de usuários demo.
