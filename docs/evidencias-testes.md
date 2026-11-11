# Evidências de testes — Dashboard

Validação realizada em 04/11/2026, na branch `fix/dashboard-entrega`, com Java 21.0.12.1, Spring Boot 3.4.4, MongoDB 7.0.43 e Chrome automatizado pelo Playwright CLI. A aplicação e o banco rodaram em containers locais separados dos outros projetos.

## 1. Suíte Java e integração com MongoDB

Resultado da suíte completa:

```text
Tests run: 163, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

[Resumo por classe](evidencias/testes-java21.txt).

| Verificação adicionada | Resultado |
| --- | --- |
| Soma de valores monetários em String, Decimal128 e tipos numéricos BSON | Passou |
| Faturamento, descontos, ticket médio, cancelamentos e comparação anterior | Passou |
| Série diária/semanal/mensal, categorias e tipos de entrega | Passou |
| Ranking ordenado por receita e participação percentual | Passou |
| Filtros, paginação e nome do cliente com banco real | Passou |
| Período vazio com indicadores zero e série contínua | Passou |
| Administrador configurado como `gerente` ou `atendente` antes do seed demo | Passou, nos dois casos |

Os cinco casos de `DashboardMongoIntegrationTest` executam pipelines no MongoDB real. Cada caso cria uma base aleatória `dashboard_test_*`, insere dados conhecidos e remove somente essa base ao terminar. Os testes de inicialização usam o contexto Spring e repositórios mockados para verificar a ordem efetiva dos runners.

### Reprodução com Java 21 e MongoDB local

```powershell
$env:DASHBOARD_TEST_MONGODB_URI="mongodb://localhost:27017"
.\mvnw.cmd test
```

Sem `DASHBOARD_TEST_MONGODB_URI`, os cinco testes de integração são ignorados: ficam 158 testes executados e cinco ignorados. Use instância local dedicada a testes; a conta precisa criar e remover as bases descartáveis.

### Comando usado nesta validação

Com MongoDB já iniciado no container `pediu-patrao-entrega-mongo`, o container Maven compartilhou sua rede. Os caminhos dos volumes abaixo correspondem à máquina de validação:

```powershell
docker run --rm --network container:pediu-patrao-entrega-mongo `
  --mount type=bind,source=D:/Projetos/Estudos/pediu-patrao,target=/workspace `
  --mount type=bind,source=C:/Users/Micro/.m2,target=/root/.m2 `
  --workdir /workspace `
  -e DASHBOARD_TEST_MONGODB_URI=mongodb://localhost:27017 `
  maven:3.9-eclipse-temurin-21 mvn -o -B test
```

`-o` usa as dependências que já estavam no cache; em outro ambiente, remova essa opção se for necessário baixá-las.

## 2. Fluxo real no navegador

Aplicação em `http://localhost:8083`, Java 21, profile `demo`, base exclusiva `pediupatrao_entrega_demo` com 210 pedidos. O script [verificar-dashboard.js](../scripts/verificar-dashboard.js) concluiu com código de saída 0 e 11 cenários aprovados:

| Cenário | Requisito | Resultado |
| --- | --- | --- |
| Gerente acessa indicadores e cinco gráficos Chart.js renderizados | RF01/RF02 | Passou |
| Cinco endpoints reais retornam JSON; faturamento, ticket e receitas positivos | RF06 | Passou |
| Período invertido retorna `400` com `ProblemDetail` | RF05/RF07 | Passou |
| Atalho de 7 dias, semana, status, entrega e ranking por receita | RF03/RF04/RF05 | Passou |
| Repetir clique em Próxima com resposta retida não pula páginas | RF03 | Passou |
| Falha de rede preserva tabela; nova tentativa recupera e mantém a página | RF07 | Passou |
| Atualização automática dispara após avanço controlado de 60 segundos | RF07 | Passou |
| Capturas em 1440×1000 e 390×844 | Interface | Passou |
| Visitante recebe `401` na API e login na página | Acesso | Passou |
| Atendente recebe `403` na API e na página | Acesso | Passou |
| Administrador recebe `200` na API e na página | Acesso | Passou |

O teste de rede aborta deliberadamente as quatro requisições do dashboard, gerando quatro erros `net::ERR_FAILED` esperados no console. Não são falhas da execução.

### Reprodução do E2E

Pré-requisitos: aplicação com profile `demo`, contas padrão de demonstração e Playwright CLI instalado. Esse roteiro é exclusivo para a base demo, pois usa suas credenciais públicas.

```powershell
playwright-cli -s=pediupatrao-entrega open http://localhost:8083/login --browser chrome
playwright-cli -s=pediupatrao-entrega snapshot
playwright-cli -s=pediupatrao-entrega run-code --filename scripts/verificar-dashboard.js
playwright-cli -s=pediupatrao-entrega close
```

Use uma sessão nova do navegador para cada execução; o teste controla o relógio da página para verificar a atualização automática sem aguardar um minuto real.

### Capturas da execução

- [Dashboard desktop](../output/playwright/dashboard-desktop.png).
- [Dashboard mobile](../output/playwright/dashboard-mobile.png).

## 3. Falhas iniciais e limites

- A tentativa inicial no Java 25 do Windows falhou: o Byte Buddy instalado suporta oficialmente até Java 24. A suíte foi executada novamente no Java 21 definido pelo projeto, com sucesso, sem trocar dependências nem habilitar opções experimentais.
- O roteiro E2E precisou de ajustes no ambiente do CLI (`URL` indisponível no contexto do script e seletor de botão com ícone). A versão final completou todos os cenários.
- `node --check` aprovou o JavaScript do dashboard. `git diff --check` não apontou erros de whitespace.
- O E2E cobre Chrome e dois tamanhos de tela; não equivale a uma auditoria de acessibilidade ou teste em todos os navegadores.
- O build de release não foi executado separadamente; a suíte Maven compilou o código e os testes.
- A gravação e a publicação do vídeo de até 3 minutos ainda precisam ser realizadas.

## 4. Recuperação e dados existentes

As correções monetárias atuam nas consultas e não migram documentos. Valores numéricos e texto válido são aceitos, nulos/ausentes contam como zero. Texto monetário inválido provoca erro, em vez de ser descartado silenciosamente.

A prioridade do administrador corrige novos boots, mas não transforma contas demo já persistidas em administradores. Se a colisão ocorreu anteriormente, recupere a conta explicitamente. Desativar o profile ou reverter código não remove contas nem pedidos demo; mantenha uma base exclusiva para apresentação.
