# Evidências de Testes — Dashboard

## 1. Ambiente de execução

| Item | Valor |
| --- | --- |
| Data da execução | 30/10/2026 |
| Sistema | Windows |
| Java em execução | 22.0.1 (projeto compilado com `--release 21`) |
| Build | Maven Wrapper (`mvnw.cmd`) |
| Spring Boot | 3.4.4 |
| Banco para testes automatizados | Mocks (sem MongoDB real) |

## 2. Resultado da suíte completa

Comando executado na raiz do projeto:

```powershell
.\mvnw.cmd test
```

Resultado:

```text
[INFO] Tests run: 156, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **156 testes executados**
- **0 falhas**
- **0 erros**
- **0 testes ignorados**

A suíte completa inclui os testes já existentes de pedidos, status, descontos, cancelamentos, auditoria, perfis e segurança, além dos testes novos do dashboard. Nenhuma regressão foi identificada.

## 3. Testes específicos do dashboard

| Classe | Testes | Cobertura principal |
| --- | ---: | --- |
| `DashboardMathTest` | 7 | Ticket médio, percentuais, variação, arredondamento e divisão por zero. |
| `DashboardServiceValidationTest` | 8 | Período invertido, período > 366 dias, agrupamento, limite, status, tipo de entrega, página e tamanho de página inválidos. |
| `DashboardControllerTest` | 5 | Acesso `ADMIN`/`GERENTE`, bloqueio de `ATENDENTE`, `401` anônimo e `ProblemDetail` em erro de validação. |
| `SegurancaAutorizacaoTest` | 63 (4 do dashboard) | Página `/dashboard`: redirecionamento ao login, `403` para atendente e `200` para gerente/admin. |
| `DemoDataSeederTest` | 1 | Geração de pedidos com `tipoEntrega`, snapshot `tipoProduto`, status finalizado/cancelado e data de entrada. |
| **Total relacionado ao dashboard** | **25** | Inclui os 4 casos de página dentro da suíte de segurança. |

### 3.1 Cálculos (`DashboardMathTest`)

Casos verificados:

- Ticket médio com duas casas decimais.
- Ticket médio zero quando não há pedidos finalizados.
- Percentual simples e percentual sem total.
- Variação percentual positiva.
- Variação nula quando a base anterior é zero ou nula.
- Arredondamento `HALF_UP` e tratamento de valores nulos.

### 3.2 Validações (`DashboardServiceValidationTest`)

Casos verificados:

- `inicio` posterior a `fim` → `400`.
- Período maior que 366 dias → `400`.
- `agrupamento` inválido → `400`.
- `limit` fora de 1–50 → `400`.
- `status` inválido → `400`.
- `tipoEntrega` inválido → `400`.
- `page` negativa → `400`.
- `size` fora de 1–100 → `400`.

Em todos os casos, o teste confirma que o `MongoTemplate` não é chamado antes da validação (`verifyNoInteractions`).

### 3.3 Segurança e erros (`DashboardControllerTest`)

| Teste | Cenário | Resultado esperado | Resultado |
| --- | --- | --- | --- |
| `gerenteAcessaResumo` | `GERENTE` em `/api/dashboard/resumo` | `200` | ✅ |
| `adminAcessaResumo` | `ADMIN` em `/api/dashboard/resumo` | `200` | ✅ |
| `atendenteRecebeAcessoNegado` | `ATENDENTE` em `/api/dashboard/resumo` | `403` | ✅ |
| `usuarioAnonimoRecebeNaoAutenticado` | Sem sessão em `/api/dashboard/resumo` | `401` | ✅ |
| `erroDeValidacaoRetornaProblemDetail` | Erro de período | `400` com `title` e `detail` | ✅ |

Exemplo de resposta validada:

```json
{
  "status": 400,
  "title": "Parametro invalido",
  "detail": "inicio deve ser anterior ou igual a fim"
}
```

### 3.4 Página e menu (`SegurancaAutorizacaoTest`)

| Teste | Cenário | Resultado |
| --- | --- | --- |
| `naoAutenticadoNaoAcessaDashboard` | `GET /dashboard` sem sessão | `302` para `/login` |
| `atendenteNaoAcessaDashboard` | `GET /dashboard` como `ATENDENTE` | `403` |
| `gerenteAcessaDashboard` | `GET /dashboard` como `GERENTE` | `200` + conteúdo “Evolução das vendas” + `/js/dashboard.js` |
| `adminAcessaDashboard` | `GET /dashboard` como `ADMIN` | `200` + conteúdo “Top produtos” |

### 3.5 Dados de demonstração (`DemoDataSeederTest`)

O teste executa o seeder com repositórios mockados e verifica:

- Lista de pedidos não vazia.
- Todos os pedidos possuem `tipoEntrega`.
- Todos os itens possuem `tipoProduto`.
- Existem pedidos `FINALIZADO` e `CANCELADO`.
- Todos os pedidos possuem `dataHoraEntrada`.

## 4. Evidência de compilação

```powershell
.\mvnw.cmd -q -DskipTests compile
```

Resultado: conclusão sem erros.

## 5. Cobertura funcional dos requisitos

| Requisito | Evidência automatizada | Evidência manual pendente |
| --- | --- | --- |
| RF01 — KPIs | `DashboardControllerTest`, `DashboardMathTest` | Conferir valores com dados do profile `demo`. |
| RF02 — Gráficos | Renderização da página e endpoints de vendas | Inspeção visual dos gráficos no navegador. |
| RF03 — Status | `porStatus` no resumo e filtros de status | Conferir doughnut e badges da tabela. |
| RF04 — Top produtos | Endpoint `/produtos` e `DashboardServiceValidationTest` | Conferir ranking e tooltips. |
| RF05 — Filtros | Validações de parâmetros e página renderizada | Testar cada filtro na interface. |
| RF06 — API | `DashboardControllerTest` e documentação da API | Chamadas reais com sessão. |
| RF07 — Atualização/erros | `ProblemDetail`, estados da UI e teste de erro | Simular queda de rede e refresh automático. |
| Acesso restrito | `DashboardControllerTest` e `SegurancaAutorizacaoTest` | Repetir com os três perfis. |

## 6. Roteiro de verificação manual

> A verificação manual abaixo depende de uma instância MongoDB acessível. No ambiente usado para desenvolver esta entrega não havia MongoDB, Docker ou `MONGODB_URI` configurado; por isso a validação visual e das agregações em runtime não foi executada nesta máquina.

### 6.1 Subir a aplicação com dados de demonstração

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/pizzaria"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

O profile `demo` cria:

- Usuários: `gerente` / `gerente123` e `atendente` / `atendente123`.
- 8 clientes, 12 produtos e cerca de 200 pedidos nos últimos 45 dias.
- Pedidos com `ENTREGA`, `RETIRADA` e `BALCAO`, status variados, descontos e cancelamentos.

O usuário `admin` continua sendo criado pelo `DataInitializer` com `admin` / `teste123` (ou variáveis `APP_ADMIN_*`).

### 6.2 Checklist

- [ ] Login como `gerente` e acesso a `/dashboard`.
- [ ] KPIs de faturamento, pedidos, ticket médio, cancelamentos, andamento, tempo médio, descontos e finalizados coerentes com o período.
- [ ] Gráfico de evolução com barras de faturamento e linha de pedidos.
- [ ] Alternância de agrupamento entre dia, semana e mês.
- [ ] Filtro por status e por tipo de entrega.
- [ ] Ranking Top 5/10/20 por quantidade e por receita.
- [ ] Gráficos de status, categorias e tipos de entrega.
- [ ] Tabela paginada, botões anterior/próxima e informação de total.
- [ ] Botão **Atualizar** mantém filtros e página.
- [ ] Atualização automática de 60s.
- [ ] Erro de rede exibe alerta e **Tentar novamente**.
- [ ] Período inválido exibe mensagem do backend.
- [ ] Layout responsivo em largura mobile.
- [ ] Login como `atendente`: link do menu oculto e acesso direto a `/dashboard` retorna `403`.
- [ ] Sem sessão: `/dashboard` redireciona ao login e `/api/dashboard/resumo` retorna `401`.

### 6.3 Verificação rápida das regras no MongoDB

Exemplo de conferência do faturamento do período (Mongo shell):

```javascript
db.pedidos.aggregate([
  {
    $match: {
      dataHoraEntrada: {
        $gte: ISODate("2026-10-01T03:00:00Z"),
        $lt: ISODate("2026-10-31T03:00:00Z")
      },
      status: "FINALIZADO"
    }
  },
  {
    $group: {
      _id: null,
      faturamento: { $sum: "$valorTotal" },
      finalizados: { $sum: 1 }
    }
  }
])
```

> O intervalo em UTC acima corresponde a `2026-10-01 00:00` até `2026-10-30 23:59:59` em `America/Sao_Paulo` (offset `-03:00`). A API calcula esse intervalo automaticamente.

## 7. Limitações desta execução

- Não havia MongoDB local, Docker ou `MONGODB_URI` no ambiente, então as agregações não foram executadas contra uma base real nesta máquina.
- A página foi validada por testes `@WebMvcTest` (renderização do fragmento, autorização e presença dos scripts), não por inspeção visual em navegador.
- Os testes de cálculo usam `DashboardMath`; os pipelines de agregação são exercitados apenas em runtime com MongoDB.
- Recomenda-se executar o checklist da seção 6 antes da entrega final e gravar o vídeo com o profile `demo`.

## 8. Como reproduzir

```powershell
# Suíte completa
.\mvnw.cmd test

# Apenas dashboard
.\mvnw.cmd "-Dtest=DashboardMathTest,DashboardServiceValidationTest,DashboardControllerTest,DemoDataSeederTest" test

# Apenas segurança da página/API
.\mvnw.cmd "-Dtest=SegurancaAutorizacaoTest" test
```

No Linux/macOS, substitua `.\mvnw.cmd` por `./mvnw`.
