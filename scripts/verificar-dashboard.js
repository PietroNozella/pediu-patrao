async (page) => {
    const base = page.url().split('/').slice(0, 3).join('/');
    const resultados = [];
    const verificar = (condicao, mensagem) => {
        if (!condicao) throw new Error(mensagem);
    };
    const registrar = (nome) => resultados.push({ cenario: nome, resultado: 'PASSOU' });
    const aguardarCarga = () => page.waitForFunction(() =>
        document.querySelector('#ultimaAtualizacao').textContent.startsWith('Atualizado às') &&
        !document.querySelector('#btnAtualizar').disabled);
    const login = async (alvo, usuario, senha) => {
        await alvo.goto(base + '/login');
        await alvo.getByRole('textbox', { name: 'Usuário' }).fill(usuario);
        await alvo.getByRole('textbox', { name: 'Senha' }).fill(senha);
        await Promise.all([
            alvo.waitForURL(base + '/'),
            alvo.getByRole('button', { name: 'Entrar', exact: true }).click()
        ]);
    };

    await login(page, 'gerente', 'gerente123');
    await page.goto(base + '/dashboard');
    await aguardarCarga();
    verificar(await page.locator('#dashboardErro').isHidden(), 'Carga inicial exibiu erro');
    verificar(await page.locator('canvas').count() === 5, 'Gráficos ausentes');
    verificar(await page.evaluate(() => Object.keys(Chart.instances).length) === 5, 'Gráficos não renderizados');
    registrar('RF01/RF02: gerente acessa indicadores e cinco gráficos');

    for (const recurso of ['resumo', 'vendas', 'produtos', 'pedidos', 'filtros']) {
        const resposta = await page.request.get(base + '/api/dashboard/' + recurso);
        verificar(resposta.status() === 200, 'Endpoint falhou: ' + recurso);
        const dados = await resposta.json();
        if (recurso === 'resumo') verificar(dados.faturamento > 0 && dados.ticketMedio > 0, 'Valores monetários zerados');
        if (recurso === 'produtos') verificar(dados.itens.some(item => item.receita > 0), 'Ranking sem receita');
    }
    registrar('RF06: cinco endpoints reais com JSON e valores monetários positivos');

    const invalida = await page.request.get(base + '/api/dashboard/resumo?inicio=2026-10-31&fim=2026-10-01');
    verificar(invalida.status() === 400 && (await invalida.json()).detail, 'Validação sem ProblemDetail');
    registrar('RF05/RF07: período inválido retorna 400 e ProblemDetail');

    await page.getByRole('button', { name: '7 dias', exact: true }).click();
    await aguardarCarga();
    await page.locator('#agrupamento').selectOption('semana');
    await page.locator('#status').selectOption('FINALIZADO');
    await page.locator('#tipoEntrega').selectOption('ENTREGA');
    await page.locator('#ordenarProdutos').selectOption('receita');
    await page.locator('#limiteProdutos').selectOption('5');
    await page.locator('#filtrosForm button[type="submit"]').click();
    await aguardarCarga();
    verificar(await page.locator('#dashboardErro').isHidden(), 'Filtros exibiram erro');
    verificar(await page.locator('#badgeAgrupamento').textContent() === 'Por semana', 'Agrupamento não aplicado');
    const linhas = await page.locator('#tabelaPedidos tr').allTextContents();
    verificar(linhas.length > 0 && linhas.every(linha => linha.includes('Finalizado') && linha.includes('Entrega')), 'Tabela não respeita filtros');
    registrar('RF03/RF04/RF05: período, semana, status, entrega e ranking por receita');

    await page.locator('#btnLimpar').click();
    await aguardarCarga();
    let liberar;
    const bloqueio = new Promise(resolve => { liberar = resolve; });
    const requisicoes = [];
    const padraoPedidos = /\/api\/dashboard\/pedidos\?/;
    await page.route(padraoPedidos, async rota => {
        requisicoes.push(rota.request().url().match(/[?&]page=(\d+)/)[1]);
        await bloqueio;
        await rota.continue();
    });
    try {
        await page.locator('#btnProximaPagina').click();
        verificar(await page.locator('#btnProximaPagina').isDisabled(), 'Próxima não bloqueou durante carga');
        await page.locator('#btnProximaPagina').dispatchEvent('click');
        liberar();
        await page.waitForFunction(() => document.querySelector('#paginacaoInfo').textContent.startsWith('Página 2 de') && !document.querySelector('#btnAtualizar').disabled);
        verificar(requisicoes.length === 1 && requisicoes[0] === '1', 'Clique repetido disparou avanço extra');
        await page.locator('#btnProximaPagina').click();
        await page.waitForFunction(() => document.querySelector('#paginacaoInfo').textContent.startsWith('Página 3 de') && !document.querySelector('#btnAtualizar').disabled);
        verificar(requisicoes[1] === '2', 'Paginação pulou uma página');
    } finally {
        liberar();
        await page.unroute(padraoPedidos);
    }
    registrar('RF03: cliques repetidos sob rede lenta não pulam páginas');

    const tabelaAntes = await page.locator('#tabelaPedidos').textContent();
    const padraoApi = '**/api/dashboard/**';
    await page.route(padraoApi, rota => rota.abort('failed'));
    await page.locator('#btnAtualizar').click();
    await page.locator('#dashboardErro').waitFor({ state: 'visible' });
    verificar(await page.locator('#tabelaPedidos').textContent() === tabelaAntes, 'Falha de rede apagou dados');
    await page.waitForFunction(() => !document.querySelector('#btnAtualizar').disabled);
    await page.unroute(padraoApi);
    await page.locator('#btnTentarNovamente').click();
    await aguardarCarga();
    verificar(await page.locator('#dashboardErro').isHidden(), 'Nova tentativa não recuperou');
    verificar((await page.locator('#paginacaoInfo').textContent()).startsWith('Página 3 de'), 'Atualização perdeu a página');
    registrar('RF07: erro de rede preserva dados e nova tentativa mantém página');

    await page.clock.install();
    const automatica = page.waitForResponse(resposta => resposta.url().includes('/api/dashboard/resumo') && resposta.status() === 200);
    await page.locator('#autoAtualizar').check();
    await page.clock.fastForward(60000);
    await automatica;
    await aguardarCarga();
    await page.locator('#autoAtualizar').uncheck();
    registrar('RF07: atualização automática dispara após 60 segundos');

    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.screenshot({ path: 'output/playwright/dashboard-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'output/playwright/dashboard-mobile.png', fullPage: true });
    registrar('Interface: capturas em desktop e mobile');

    for (const perfil of ['anonimo', 'atendente', 'admin']) {
        const contexto = await page.context().browser().newContext();
        try {
            const aba = await contexto.newPage();
            if (perfil !== 'anonimo') await login(aba, perfil, perfil === 'admin' ? 'teste123' : 'atendente123');
            const api = await contexto.request.get(base + '/api/dashboard/resumo');
            const esperado = perfil === 'anonimo' ? 401 : perfil === 'atendente' ? 403 : 200;
            verificar(api.status() === esperado, 'API com acesso incorreto para ' + perfil);
            const tela = await aba.goto(base + '/dashboard');
            if (perfil === 'anonimo') verificar(aba.url().endsWith('/login'), 'Página anônima não pediu login');
            else verificar(tela.status() === esperado, 'Página com acesso incorreto para ' + perfil);
            registrar('Acesso: ' + perfil + ' recebe API ' + esperado + ' e página correspondente');
        } finally {
            await contexto.close();
        }
    }
    return { base, resultados, total: resultados.length };
}
