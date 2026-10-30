# API do Dashboard — Pediu Patrão

## 1. Visão geral

A API do dashboard é somente leitura, responde em JSON e é consumida pela página `/dashboard` via `fetch`.

- **Base:** `/api/dashboard`
- **Autenticação:** sessão de login do Pediu Patrão (cookie `JSESSIONID`)
- **Perfis autorizados:** `ADMIN` e `GERENTE`
- **Formato:** `application/json`
- **Erros de negócio:** `application/problem+json` (RFC 7807)
- **Fuso horário:** `America/Sao_Paulo`
- **Datas:** `AAAA-MM-DD`

> A API não aceita `POST`, `PUT` ou `DELETE`. O dashboard é apenas de consulta.

## 2. Autenticação e autorização

| Situação | Página `/dashboard` | API `/api/dashboard/**` |
| --- | --- | --- |
| Não autenticado | `302` para `/login` | `401 Unauthorized` |
| `ATENDENTE` | `403 Forbidden` | `403 Forbidden` |
| `GERENTE` | `200 OK` | `200 OK` |
| `ADMIN` | `200 OK` | `200 OK` |

A autorização é feita no backend com `@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")`. O menu lateral só exibe o link para esses perfis.

### Exemplo de sessão com PowerShell

```powershell
$login = Invoke-WebRequest "http://localhost:8080/login" -SessionVariable sessao
$token = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
Invoke-WebRequest "http://localhost:8080/login" -Method Post -WebSession $sessao -Body @{
    username = "gerente"
    password = "gerente123"
    _csrf    = $token
} | Out-Null

Invoke-RestMethod "http://localhost:8080/api/dashboard/resumo" -WebSession $sessao |
    ConvertTo-Json -Depth 6
```

### Exemplo com curl

```bash
# Obtenha o cookie e o token CSRF da página de login
curl -s -c cookies.txt http://localhost:8080/login -o login.html
TOKEN=$(grep -oP 'name="_csrf" value="\K[^"]+' login.html)

# Autentique-se
curl -s -b cookies.txt -c cookies.txt -X POST http://localhost:8080/login \
  -d "username=gerente" \
  -d "password=gerente123" \
  -d "_csrf=$TOKEN"

# Consulte a API
curl -s -b cookies.txt \
  "http://localhost:8080/api/dashboard/resumo?inicio=2026-10-01&fim=2026-10-30"
```

## 3. Parâmetros comuns

| Parâmetro | Tipo | Obrigatório | Padrão | Descrição |
| --- | --- | --- | --- | --- |
| `inicio` | data (`AAAA-MM-DD`) | Não | `fim - 29 dias` | Início do período, inclusive. |
| `fim` | data (`AAAA-MM-DD`) | Não | Hoje em `America/Sao_Paulo` | Fim do período, inclusive. |
| `status` | enum | Não | Todos | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO` ou `CANCELADO`. |
| `tipoEntrega` | enum | Não | Todos | `ENTREGA`, `RETIRADA` ou `BALCAO`. |
| `agrupamento` | enum | Não | `dia` | `dia`, `semana` ou `mes` (usado em `/vendas`). |
| `ordenarPor` | enum | Não | `quantidade` | `quantidade` ou `receita` (usado em `/produtos`). |
| `limit` | inteiro | Não | `10` | 1 a 50 (usado em `/produtos`). |
| `page` | inteiro | Não | `0` | Página da listagem, a partir de zero (usado em `/pedidos`). |
| `size` | inteiro | Não | `10` | 1 a 100 (usado em `/pedidos`). |

### Regras do período

- `inicio` deve ser anterior ou igual a `fim`.
- O período máximo é de **366 dias**.
- O intervalo aplicado é `[inicio 00:00, fim + 1 dia 00:00)` no fuso `America/Sao_Paulo`.
- A referência é o campo `dataHoraEntrada` do pedido.
- Datas em formato inválido retornam `400 Bad Request`.

## 4. Endpoints

### 4.1 `GET /api/dashboard/resumo`

Retorna os KPIs gerais, a comparação com o período anterior e a contagem por status.

**Parâmetros:** `inicio`, `fim`.

**Exemplo:**

```http
GET /api/dashboard/resumo?inicio=2026-10-01&fim=2026-10-30
```

**Resposta `200 OK`:**

```json
{
  "periodo": {
    "inicio": "2026-10-01",
    "fim": "2026-10-30"
  },
  "faturamento": 12345.67,
  "pedidos": 120,
  "finalizados": 98,
  "ticketMedio": 125.98,
  "cancelados": 8,
  "taxaCancelamento": 6.67,
  "descontos": 320.50,
  "emAndamento": 14,
  "tempoMedioMinutos": 38.42,
  "comparacao": {
    "faturamentoVariacaoPercentual": 12.34,
    "pedidosVariacaoPercentual": -2.10
  },
  "porStatus": [
    { "status": "RECEBIDO", "quantidade": 3 },
    { "status": "EM_PREPARACAO", "quantidade": 5 },
    { "status": "PRONTO", "quantidade": 4 },
    { "status": "SAIU_PARA_ENTREGA", "quantidade": 2 },
    { "status": "RETIRADO", "quantidade": 0 },
    { "status": "FINALIZADO", "quantidade": 98 },
    { "status": "CANCELADO", "quantidade": 8 }
  ]
}
```

**Campos:**

| Campo | Descrição |
| --- | --- |
| `periodo` | Período efetivamente aplicado. |
| `faturamento` | Soma de `valorTotal` dos pedidos `FINALIZADO`. |
| `pedidos` | Total de pedidos no período, incluindo cancelados. |
| `finalizados` | Pedidos `FINALIZADO`. |
| `ticketMedio` | `faturamento / finalizados`; `0.00` se não houver finalizados. |
| `cancelados` | Pedidos `CANCELADO`. |
| `taxaCancelamento` | `cancelados / pedidos * 100`; `0.00` se não houver pedidos. |
| `descontos` | Soma de `valorDesconto` dos pedidos não cancelados. |
| `emAndamento` | Pedidos com status diferente de `FINALIZADO` e `CANCELADO`. |
| `tempoMedioMinutos` | Média de `dataHoraSaida - dataHoraEntrada` dos pedidos com saída. |
| `comparacao` | Variação percentual em relação ao período anterior de mesma duração. Pode ser `null`. |
| `porStatus` | Contagem por status, na ordem do enum. |

### 4.2 `GET /api/dashboard/vendas`

Retorna a série temporal, a receita por categoria e os pedidos por tipo de entrega.

**Parâmetros:** `inicio`, `fim`, `agrupamento`, `status`, `tipoEntrega`.

**Exemplo:**

```http
GET /api/dashboard/vendas?inicio=2026-10-01&fim=2026-10-30&agrupamento=semana
```

**Resposta `200 OK`:**

```json
{
  "periodo": {
    "inicio": "2026-10-01",
    "fim": "2026-10-30"
  },
  "agrupamento": "semana",
  "serie": [
    {
      "data": "2026-09-28",
      "rotulo": "28/09 - 04/10",
      "pedidos": 22,
      "faturamento": 2310.40
    },
    {
      "data": "2026-10-05",
      "rotulo": "05/10 - 11/10",
      "pedidos": 28,
      "faturamento": 3015.90
    }
  ],
  "porCategoria": [
    { "categoria": "Pizza", "quantidade": 180, "receita": 9120.00 },
    { "categoria": "Bebida", "quantidade": 95, "receita": 1080.50 },
    { "categoria": "Sobremesa", "quantidade": 30, "receita": 450.00 }
  ],
  "porTipoEntrega": [
    { "tipo": "ENTREGA", "pedidos": 66, "faturamento": 7480.20 },
    { "tipo": "RETIRADA", "pedidos": 30, "faturamento": 3200.00 },
    { "tipo": "BALCAO", "pedidos": 24, "faturamento": 1665.47 }
  ]
}
```

**Observações:**

- `serie` é contínua: dias/semanas/meses sem pedidos aparecem com `pedidos: 0` e `faturamento: 0.00`.
- `faturamento` da série soma apenas pedidos `FINALIZADO`; `pedidos` conta os pedidos não cancelados do filtro.
- Quando `status=CANCELADO` é informado, o filtro explícito prevalece e os cancelados são incluídos na contagem.
- `porCategoria` usa o snapshot `itens.tipoProduto`; itens antigos sem snapshot aparecem como `Nao informado`.
- `porTipoEntrega` usa `pedido.tipoEntrega`; pedidos antigos sem o campo aparecem como `NAO_INFORMADO`.

### 4.3 `GET /api/dashboard/produtos`

Retorna o ranking de produtos por quantidade ou receita.

**Parâmetros:** `inicio`, `fim`, `ordenarPor`, `limit`, `tipoProduto`, `status`, `tipoEntrega`.

**Exemplo:**

```http
GET /api/dashboard/produtos?inicio=2026-10-01&fim=2026-10-30&ordenarPor=receita&limit=5
```

**Resposta `200 OK`:**

```json
{
  "periodo": {
    "inicio": "2026-10-01",
    "fim": "2026-10-30"
  },
  "ordenarPor": "receita",
  "itens": [
    {
      "produtoId": "66f1c2a9e4b0a1b2c3d4e5f6",
      "nome": "Pizza Quatro Queijos",
      "quantidade": 42,
      "receita": 2515.80,
      "participacaoQuantidadePercentual": 12.50,
      "participacaoReceitaPercentual": 18.40
    },
    {
      "produtoId": "66f1c2a9e4b0a1b2c3d4e5f7",
      "nome": "Pizza Margherita",
      "quantidade": 38,
      "receita": 1896.20,
      "participacaoQuantidadePercentual": 11.31,
      "participacaoReceitaPercentual": 13.87
    }
  ]
}
```

**Observações:**

- A participação é calculada sobre o total de itens/receita do período filtrado, não apenas sobre o Top N.
- Pedidos cancelados são excluídos quando não há `status` explícito.
- `tipoProduto` filtra pelo snapshot do item; valores desconhecidos retornam lista vazia (não erro).
- `limit` fora de 1–50 retorna `400`.

### 4.4 `GET /api/dashboard/pedidos`

Retorna a listagem paginada dos pedidos do período.

**Parâmetros:** `inicio`, `fim`, `status`, `tipoEntrega`, `page`, `size`.

**Exemplo:**

```http
GET /api/dashboard/pedidos?inicio=2026-10-01&fim=2026-10-30&page=0&size=10
```

**Resposta `200 OK`:**

```json
{
  "content": [
    {
      "id": "66f1c2a9e4b0a1b2c3d4e5f6",
      "clienteId": "66f1c2a9e4b0a1b2c3d4e5a1",
      "clienteNome": "Ana Souza",
      "status": "FINALIZADO",
      "tipoEntrega": "ENTREGA",
      "valorTotal": 97.80,
      "dataHoraEntrada": "2026-10-30T18:20:00Z",
      "quantidadeItens": 3,
      "responsavelRegistro": "atendente"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 120,
  "totalPages": 12
}
```

**Observações:**

- Ordenação fixa por `dataHoraEntrada` decrescente.
- `clienteNome` é resolvido no backend; se o cliente não existir, retorna `Cliente nao encontrado`.
- `tipoEntrega` ausente em pedidos legados retorna `NAO_INFORMADO`.
- `page` negativo ou `size` fora de 1–100 retorna `400`.

### 4.5 `GET /api/dashboard/filtros`

Retorna as opções válidas para os selects da interface.

**Resposta `200 OK`:**

```json
{
  "status": [
    "RECEBIDO",
    "EM_PREPARACAO",
    "PRONTO",
    "SAIU_PARA_ENTREGA",
    "RETIRADO",
    "FINALIZADO",
    "CANCELADO"
  ],
  "tiposEntrega": [
    "ENTREGA",
    "RETIRADA",
    "BALCAO"
  ],
  "tiposProduto": [
    "Bebida",
    "Esfiha",
    "Lanche",
    "Pizza",
    "Porcao",
    "Sobremesa"
  ]
}
```

`tiposProduto` é obtido dos produtos cadastrados, sem duplicatas e em ordem alfabética.

## 5. Erros

### Formato `ProblemDetail`

Erros de validação e execução do dashboard seguem a RFC 7807:

```json
{
  "type": "about:blank",
  "title": "Parametro invalido",
  "status": 400,
  "detail": "inicio deve ser anterior ou igual a fim",
  "instance": "/api/dashboard/resumo"
}
```

### Tabela de status

| Status | Quando ocorre | Corpo |
| --- | --- | --- |
| `200 OK` | Consulta bem-sucedida. | JSON do endpoint. |
| `400 Bad Request` | Data inválida, período invertido, período > 366 dias, enum inválido, `limit`/`page`/`size` inválidos. | `ProblemDetail`. |
| `401 Unauthorized` | API chamada sem sessão autenticada. | Vazio (emitido pelo Spring Security). |
| `403 Forbidden` | Perfil `ATENDENTE` ou outro sem permissão. | Vazio ou `ProblemDetail`, conforme a camada que bloqueou. |
| `500 Internal Server Error` | Falha inesperada na agregação ou no banco. | `ProblemDetail` com mensagem genérica; detalhe técnico fica no log. |

### Exemplos de erro

```http
GET /api/dashboard/resumo?inicio=2026-10-30&fim=2026-10-01
```

```json
{
  "type": "about:blank",
  "title": "Parametro invalido",
  "status": 400,
  "detail": "inicio deve ser anterior ou igual a fim",
  "instance": "/api/dashboard/resumo"
}
```

```http
GET /api/dashboard/produtos?limit=0
```

```json
{
  "type": "about:blank",
  "title": "Parametro invalido",
  "status": 400,
  "detail": "limit deve estar entre 1 e 50",
  "instance": "/api/dashboard/produtos"
}
```

## 6. Regras de agregação

- **Faturamento:** somente `FINALIZADO`.
- **Cancelados:** excluídos de receita, descontos e ranking quando não há filtro explícito de status.
- **Série temporal:** preenchida com zeros para manter continuidade.
- **Semana:** começa na segunda-feira.
- **Mês:** rótulo no formato `Mmm/AAAA` (ex.: `Out/2026`).
- **Comparação:** período anterior de mesma quantidade de dias; se a base anterior for zero, a variação é `null`.
- **Tempo médio:** média entre `dataHoraEntrada` e `dataHoraSaida` dos pedidos com saída registrada.
- **Participação:** calculada sobre o total do período filtrado.

## 7. Exemplos adicionais

### Últimos 7 dias, pedidos de entrega

```http
GET /api/dashboard/vendas?inicio=2026-10-24&fim=2026-10-30&agrupamento=dia&tipoEntrega=ENTREGA
```

### Ranking de bebidas por quantidade

```http
GET /api/dashboard/produtos?inicio=2026-10-01&fim=2026-10-30&tipoProduto=Bebida&ordenarPor=quantidade&limit=10
```

### Pedidos cancelados, página 2

```http
GET /api/dashboard/pedidos?inicio=2026-10-01&fim=2026-10-30&status=CANCELADO&page=1&size=10
```
