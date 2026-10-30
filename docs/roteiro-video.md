# Roteiro do Vídeo — Dashboard (até 3 minutos)

## 1. Preparação

### 1.1 Subir a aplicação com dados de demonstração

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/pizzaria"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Aguarde a mensagem de aplicação iniciada e confirme que o `DemoDataSeeder` criou os pedidos.

### 1.2 Credenciais

| Perfil | Usuário | Senha |
| --- | --- | --- |
| Gerente | `gerente` | `gerente123` |
| Atendente | `atendente` | `atendente123` |
| Admin | `admin` | `teste123` (ou `APP_ADMIN_PASSWORD`) |

### 1.3 Abas e janela

Deixe abertas:

1. `http://localhost:8080/login`
2. Uma janela anônima para demonstrar o bloqueio do atendente.
3. O DevTools na aba **Network** para mostrar a chamada `fetch` da API.

### 1.4 Antes de gravar

- Use uma resolução em que os gráficos fiquem legíveis (1280×720 ou superior).
- Confira se o período padrão tem dados (últimos 30 dias).
- Feche notificações e abas desnecessárias.
- Deixe o cursor longe dos gráficos durante a narração.

## 2. Roteiro cronometrado

### 0:00–0:20 — Abertura e login

**Tela:** página de login.

**Narração sugerida:**

> “Este é o dashboard administrativo do Pediu Patrão. Ele foi feito para que gerentes e administradores acompanhem vendas, status dos pedidos e produtos mais vendidos em uma única tela. Vou entrar com o perfil de gerente.”

**Ação:** preencha `gerente` / `gerente123` e clique em **Entrar**.

### 0:20–0:45 — Visão geral dos KPIs

**Tela:** topo do dashboard.

**Narração sugerida:**

> “No topo temos os indicadores do período: faturamento, pedidos, ticket médio, cancelamentos, pedidos em andamento, tempo médio, descontos e finalizados. O faturamento considera apenas pedidos finalizados, e a comparação mostra a variação em relação ao período anterior.”

**Ação:** aponte para os cards de faturamento, pedidos, ticket médio e cancelamentos. Passe o mouse sobre a variação percentual.

### 0:45–1:15 — Filtros e gráfico de vendas

**Tela:** card de filtros e gráfico “Evolução das vendas”.

**Narração sugerida:**

> “Os filtros permitem escolher o período, o agrupamento e o status. Vou aplicar os últimos sete dias e agrupar por dia. O gráfico combina o faturamento em barras com a quantidade de pedidos em linha. Também posso alternar para semana ou mês.”

**Ação:**

1. Clique em **7 dias**.
2. Troque o agrupamento para **Por dia** (se necessário).
3. Clique em **Aplicar**.
4. Mostre o gráfico atualizado.
5. Troque para **Por semana** e aplique novamente, rapidamente.

### 1:15–1:40 — Status, produtos e categorias

**Tela:** gráficos de status, top produtos, categorias e tipo de entrega.

**Narração sugerida:**

> “Ao lado, o gráfico de status mostra onde os pedidos estão agora. Abaixo, o ranking dos produtos mais vendidos pode ser ordenado por quantidade ou receita. Os gráficos de categorias e de tipo de entrega completam a visão da operação.”

**Ação:**

1. Aponte para o doughnut de status.
2. No filtro **Ranking de produtos**, altere para **Por receita** e clique em **Aplicar**.
3. Passe o mouse em uma barra para mostrar o tooltip com receita e participação.
4. Aponte para os gráficos de categorias e entrega.

### 1:40–2:10 — Tabela, paginação e atualização

**Tela:** tabela “Pedidos do período”.

**Narração sugerida:**

> “A tabela lista os pedidos do período com status, tipo de entrega, total e horário de entrada. A paginação permite navegar sem recarregar a página. O botão Atualizar mantém os filtros, e a atualização automática pode ser ligada para recarregar a cada sessenta segundos.”

**Ação:**

1. Role até a tabela.
2. Clique em **Próxima** e depois em **Anterior**.
3. Clique em **Atualizar** e mostre o horário “Atualizado às …”.
4. Ative o switch **Atualizar a cada 60s** (não é necessário esperar o ciclo).

### 2:10–2:35 — Tratamento de erro e segurança

**Tela:** dashboard e, depois, janela anônima.

**Narração sugerida:**

> “Se a rede falhar, o dashboard mantém os dados e mostra um alerta com a opção de tentar novamente. O acesso é restrito: o atendente não vê o link no menu e, se tentar acessar diretamente, recebe acesso negado. Sem sessão, a API responde não autenticado.”

**Ação:**

1. Abra o DevTools → **Network** → marque **Offline**.
2. Clique em **Atualizar** e mostre o alerta de erro.
3. Desmarque **Offline** e clique em **Tentar novamente**.
4. Na janela anônima, faça login como `atendente` / `atendente123`.
5. Acesse `http://localhost:8080/dashboard` e mostre a página `403`.
6. (Opcional) Em outra aba anônima, acesse `http://localhost:8080/api/dashboard/resumo` e mostre `401`.

### 2:35–3:00 — Encerramento

**Tela:** dashboard como gerente.

**Narração sugerida:**

> “O dashboard consome uma API REST própria, com filtros documentados, erros padronizados e controle de acesso por perfil. Os dados vêm de agregações no MongoDB, e o projeto inclui testes automatizados, documentação da API e um profile de demonstração. Obrigado!”

**Ação:** volte ao dashboard e finalize a gravação.

## 3. Plano B

| Problema | Solução durante a gravação |
| --- | --- |
| Gráficos não aparecem | Confirme `/plugins/chart.js/Chart.min.js` e recarregue com `Ctrl+F5`. |
| Período sem dados | Clique em **90 dias** ou ajuste as datas para o intervalo com pedidos. |
| API retorna `401` | Faça login novamente; a sessão pode ter expirado. |
| API retorna `403` | Confirme que está logado como `gerente` ou `admin`. |
| MongoDB indisponível | Reinicie o MongoDB e a aplicação com o profile `demo`. |
| Tempo estourando | Corte a seção de categorias/entrega ou a paginação; priorize KPIs, filtros, gráfico principal e segurança. |

## 4. Checklist final antes de publicar o vídeo

- [ ] Duração máxima de 3 minutos.
- [ ] Login como gerente e visão dos KPIs.
- [ ] Aplicação de pelo menos um filtro e mudança de agrupamento.
- [ ] Ranking por quantidade ou receita.
- [ ] Paginação da tabela.
- [ ] Atualização manual e switch de atualização automática.
- [ ] Alerta de erro e nova tentativa.
- [ ] Bloqueio do atendente (`403`).
- [ ] API sem sessão (`401`) — opcional.
- [ ] Nome do projeto e conclusão citados.
