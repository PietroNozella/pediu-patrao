(function () {
    'use strict';

    const API = '/api/dashboard';
    const TAMANHO_PAGINA = 8;

    const ROTULOS_STATUS = {
        RECEBIDO: 'Recebido',
        EM_PREPARACAO: 'Em preparação',
        PRONTO: 'Pronto',
        SAIU_PARA_ENTREGA: 'Saiu para entrega',
        RETIRADO: 'Retirado',
        FINALIZADO: 'Finalizado',
        CANCELADO: 'Cancelado'
    };

    const ROTULOS_ENTREGA = {
        ENTREGA: 'Entrega',
        RETIRADA: 'Retirada',
        BALCAO: 'Balcão',
        NAO_INFORMADO: 'Não informado'
    };

    const CORES_STATUS = {
        RECEBIDO: '#6c757d',
        EM_PREPARACAO: '#fd7e14',
        PRONTO: '#0d6efd',
        SAIU_PARA_ENTREGA: '#6f42c1',
        RETIRADO: '#20c997',
        FINALIZADO: '#198754',
        CANCELADO: '#dc3545'
    };

    const CORES_ENTREGA = {
        ENTREGA: '#0d6efd',
        RETIRADA: '#20c997',
        BALCAO: '#fd7e14',
        NAO_INFORMADO: '#6c757d'
    };

    const CORES_CATEGORIA = [
        '#0d6efd', '#20c997', '#fd7e14', '#6f42c1',
        '#dc3545', '#ffc107', '#0dcaf0', '#6c757d'
    ];

    const formatoMoeda = new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL'
    });
    const formatoNumero = new Intl.NumberFormat('pt-BR');
    const formatoPercentual = new Intl.NumberFormat('pt-BR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
    const formatoDataHora = new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short'
    });

    const estado = {
        pagina: 0,
        totalPaginas: 0,
        filtros: null,
        graficos: {},
        carregando: false,
        intervalo: null
    };

    function $(id) {
        return document.getElementById(id);
    }

    function iniciar() {
        definirPeriodo(30);
        configurarEventos();
        configurarChartJs();
        carregarFiltros().finally(carregarTudo);
    }

    function configurarChartJs() {
        if (!window.Chart) {
            mostrarErro('A biblioteca de gráficos não foi carregada. Os indicadores continuam disponíveis.');
            return;
        }
        Chart.defaults.global.defaultFontFamily = "'Segoe UI', system-ui, -apple-system, sans-serif";
        Chart.defaults.global.defaultFontColor = '#495057';
    }

    function configurarEventos() {
        $('filtrosForm').addEventListener('submit', function (evento) {
            evento.preventDefault();
            estado.pagina = 0;
            carregarTudo();
        });

        $('btnAtualizar').addEventListener('click', function () {
            carregarTudo({ manterPagina: true });
        });

        $('btnTentarNovamente').addEventListener('click', function () {
            carregarTudo({ manterPagina: true });
        });

        $('btnLimpar').addEventListener('click', function () {
            definirPeriodo(30);
            $('agrupamento').value = 'dia';
            $('status').value = '';
            $('tipoEntrega').value = '';
            $('ordenarProdutos').value = 'quantidade';
            $('limiteProdutos').value = '10';
            estado.pagina = 0;
            carregarTudo();
        });

        document.querySelectorAll('.btn-periodo').forEach(function (botao) {
            botao.addEventListener('click', function () {
                const dias = botao.dataset.dias;
                if (dias === 'mes') {
                    definirMesAtual();
                } else {
                    definirPeriodo(Number(dias));
                }
                estado.pagina = 0;
                carregarTudo();
            });
        });

        $('autoAtualizar').addEventListener('change', function () {
            if (estado.intervalo) {
                clearInterval(estado.intervalo);
                estado.intervalo = null;
            }
            if ($('autoAtualizar').checked) {
                estado.intervalo = setInterval(function () {
                    if (!document.hidden) {
                        carregarTudo({ manterPagina: true, silencioso: true });
                    }
                }, 60000);
            }
        });

        $('btnPaginaAnterior').addEventListener('click', function () {
            if (estado.pagina > 0) {
                carregarPedidos(estado.pagina - 1);
            }
        });

        $('btnProximaPagina').addEventListener('click', function () {
            if (estado.pagina + 1 < estado.totalPaginas) {
                carregarPedidos(estado.pagina + 1);
            }
        });
    }

    async function carregarFiltros() {
        try {
            const dados = await buscarJson(API + '/filtros');
            preencherSelect('status', dados.status || [], 'Todos os status', rotuloStatus);
            preencherSelect('tipoEntrega', dados.tiposEntrega || [], 'Todos os tipos', rotuloEntrega);
        } catch (erro) {
            if (erro.status === 401) {
                return;
            }
            preencherSelect('status', Object.keys(ROTULOS_STATUS), 'Todos os status', rotuloStatus);
            preencherSelect('tipoEntrega', Object.keys(ROTULOS_ENTREGA), 'Todos os tipos', rotuloEntrega);
        }
    }

    function preencherSelect(id, valores, textoTodos, rotulo) {
        const select = $(id);
        select.replaceChildren(new Option(textoTodos, ''));
        valores.forEach(function (valor) {
            select.add(new Option(rotulo(valor), valor));
        });
    }

    function definirPeriodo(dias) {
        const fim = new Date();
        const inicio = new Date();
        inicio.setDate(fim.getDate() - (dias - 1));
        $('inicio').value = dataParaInput(inicio);
        $('fim').value = dataParaInput(fim);
    }

    function definirMesAtual() {
        const hoje = new Date();
        $('inicio').value = dataParaInput(new Date(hoje.getFullYear(), hoje.getMonth(), 1));
        $('fim').value = dataParaInput(hoje);
    }

    function dataParaInput(data) {
        const ano = data.getFullYear();
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');
        return ano + '-' + mes + '-' + dia;
    }

    function coletarFiltros() {
        const inicio = $('inicio').value;
        const fim = $('fim').value;

        if (!inicio || !fim) {
            mostrarErro('Informe as datas de início e fim.');
            return null;
        }
        if (inicio > fim) {
            mostrarErro('A data de início deve ser anterior ou igual à data de fim.');
            return null;
        }

        const dias = Math.round(
            (Date.parse(fim + 'T00:00:00Z') - Date.parse(inicio + 'T00:00:00Z')) / 86400000
        ) + 1;
        if (dias > 366) {
            mostrarErro('O período máximo permitido é de 366 dias.');
            return null;
        }

        return {
            inicio: inicio,
            fim: fim,
            agrupamento: $('agrupamento').value,
            status: $('status').value,
            tipoEntrega: $('tipoEntrega').value,
            ordenarPor: $('ordenarProdutos').value,
            limit: $('limiteProdutos').value
        };
    }

    async function carregarTudo(opcoes) {
        opcoes = opcoes || {};
        if (estado.carregando) {
            return;
        }

        const filtros = coletarFiltros();
        if (!filtros) {
            return;
        }

        estado.filtros = filtros;
        if (!opcoes.manterPagina) {
            estado.pagina = 0;
        }

        esconderErro();
        definirCarregando(true, opcoes.silencioso);

        try {
            const resultados = await Promise.all([
                buscarJson(urlResumo(filtros)),
                buscarJson(urlVendas(filtros)),
                buscarJson(urlProdutos(filtros)),
                buscarJson(urlPedidos(filtros))
            ]);

            const resumo = resultados[0];
            const vendas = resultados[1];
            const produtos = resultados[2];
            const pedidos = resultados[3];

            renderResumo(resumo);
            renderVendas(vendas);
            renderStatus(resumo);
            renderProdutos(produtos);
            renderCategorias(vendas);
            renderEntregas(vendas);
            renderPedidos(pedidos);
            atualizarPeriodo(resumo.periodo, vendas.agrupamento);
            marcarAtualizacao();
        } catch (erro) {
            if (erro.status !== 401) {
                mostrarErro(erro.message || 'Erro inesperado ao carregar o dashboard.');
            }
        } finally {
            definirCarregando(false, opcoes.silencioso);
        }
    }

    async function carregarPedidos(pagina) {
        if (estado.carregando || !estado.filtros) {
            return;
        }

        definirCarregando(true, true);
        $('paginacaoInfo').textContent = 'Carregando pedidos...';
        try {
            const dados = await buscarJson(urlPedidos(estado.filtros, pagina));
            renderPedidos(dados);
            marcarAtualizacao();
        } catch (erro) {
            if (erro.status !== 401) {
                mostrarErro(erro.message || 'Erro ao carregar os pedidos.');
            }
        } finally {
            definirCarregando(false, true);
        }
    }

    function urlResumo(filtros) {
        return API + '/resumo?' + parametrosBase(filtros).toString();
    }

    function urlVendas(filtros) {
        const params = parametrosBase(filtros);
        params.set('agrupamento', filtros.agrupamento);
        adicionarSePreenchido(params, 'status', filtros.status);
        adicionarSePreenchido(params, 'tipoEntrega', filtros.tipoEntrega);
        return API + '/vendas?' + params.toString();
    }

    function urlProdutos(filtros) {
        const params = parametrosBase(filtros);
        params.set('ordenarPor', filtros.ordenarPor);
        params.set('limit', filtros.limit);
        adicionarSePreenchido(params, 'status', filtros.status);
        adicionarSePreenchido(params, 'tipoEntrega', filtros.tipoEntrega);
        return API + '/produtos?' + params.toString();
    }

    function urlPedidos(filtros, pagina = estado.pagina) {
        const params = parametrosBase(filtros);
        adicionarSePreenchido(params, 'status', filtros.status);
        adicionarSePreenchido(params, 'tipoEntrega', filtros.tipoEntrega);
        params.set('page', String(pagina));
        params.set('size', String(TAMANHO_PAGINA));
        return API + '/pedidos?' + params.toString();
    }

    function parametrosBase(filtros) {
        return new URLSearchParams({
            inicio: filtros.inicio,
            fim: filtros.fim
        });
    }

    function adicionarSePreenchido(params, chave, valor) {
        if (valor) {
            params.set(chave, valor);
        }
    }

    async function buscarJson(url) {
        let resposta;
        try {
            resposta = await fetch(url, {
                method: 'GET',
                headers: { Accept: 'application/json' },
                credentials: 'same-origin'
            });
        } catch (erro) {
            throw new ErroApi('Falha de conexão. Verifique sua rede e tente novamente.', 0);
        }

        if (resposta.status === 401) {
            window.location.assign('/login');
            throw new ErroApi('Sessão expirada. Redirecionando para o login.', 401);
        }
        if (resposta.status === 403) {
            throw new ErroApi('Seu perfil não tem permissão para acessar o dashboard.', 403);
        }
        if (!resposta.ok) {
            let detalhe = 'Erro ' + resposta.status + ' ao consultar o dashboard.';
            try {
                const problema = await resposta.json();
                detalhe = problema.detail || problema.title || detalhe;
            } catch (ignorado) {
                // A resposta de erro não veio em JSON.
            }
            throw new ErroApi(detalhe, resposta.status);
        }

        return resposta.json();
    }

    function ErroApi(mensagem, status) {
        this.name = 'ErroApi';
        this.message = mensagem;
        this.status = status;
    }
    ErroApi.prototype = Object.create(Error.prototype);

    function definirCarregando(ativo, silencioso) {
        estado.carregando = ativo;
        $('dashboardCarregando').classList.toggle('d-none', !ativo || Boolean(silencioso));
        $('btnAtualizar').disabled = ativo;
        $('filtrosForm').querySelector('button[type="submit"]').disabled = ativo;
        document.querySelectorAll('.btn-periodo').forEach(function (botao) {
            botao.disabled = ativo;
        });
        $('btnPaginaAnterior').disabled = ativo || estado.pagina <= 0;
        $('btnProximaPagina').disabled = ativo || estado.pagina + 1 >= estado.totalPaginas;
    }

    function mostrarErro(mensagem) {
        $('dashboardErroDetalhe').textContent = mensagem;
        $('dashboardErro').classList.remove('d-none');
    }

    function esconderErro() {
        $('dashboardErro').classList.add('d-none');
        $('dashboardErroDetalhe').textContent = '';
    }

    function marcarAtualizacao() {
        $('ultimaAtualizacao').textContent =
            'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
    }

    function renderResumo(dados) {
        const pedidos = numero(dados.pedidos);
        const finalizados = numero(dados.finalizados);
        const cancelados = numero(dados.cancelados);
        const faturamento = numero(dados.faturamento);
        const descontos = numero(dados.descontos);
        const bruto = faturamento + descontos;

        $('kpiFaturamento').textContent = moeda(faturamento);
        $('kpiPedidos').textContent = formatoNumero.format(pedidos);
        $('kpiTicketMedio').textContent = moeda(dados.ticketMedio);
        $('kpiCancelados').textContent = formatoNumero.format(cancelados);
        $('kpiEmAndamento').textContent = formatoNumero.format(numero(dados.emAndamento));
        $('kpiTempoMedio').textContent = minutos(dados.tempoMedioMinutos);
        $('kpiDescontos').textContent = moeda(descontos);
        $('kpiFinalizados').textContent = formatoNumero.format(finalizados);

        renderVariacao('kpiFaturamentoVariacao', dados.comparacao && dados.comparacao.faturamentoVariacaoPercentual);
        renderVariacao('kpiPedidosVariacao', dados.comparacao && dados.comparacao.pedidosVariacaoPercentual);

        $('kpiTicketMedioExtra').textContent = 'Finalizados: ' + formatoNumero.format(finalizados);
        $('kpiCanceladosExtra').textContent = 'Taxa: ' + percentual(dados.taxaCancelamento);
        $('kpiEmAndamentoExtra').textContent = percentual(participacao(numero(dados.emAndamento), pedidos)) + ' dos pedidos';
        $('kpiTempoMedioExtra').textContent = 'Entrada até a saída';
        $('kpiDescontosExtra').textContent = percentual(participacao(descontos, bruto)) + ' do faturamento bruto';
        $('kpiFinalizadosExtra').textContent = percentual(participacao(finalizados, pedidos)) + ' dos pedidos';
    }

    function renderVariacao(id, valor) {
        const elemento = $(id);
        if (valor === null || valor === undefined) {
            elemento.className = 'kpi-extra text-muted';
            elemento.textContent = 'Sem base no período anterior';
            return;
        }

        const variacao = numero(valor);
        let classe = 'text-muted';
        let icone = 'bi-dash';
        if (variacao > 0) {
            classe = 'text-success';
            icone = 'bi-arrow-up-right';
        } else if (variacao < 0) {
            classe = 'text-danger';
            icone = 'bi-arrow-down-right';
        }

        elemento.className = 'kpi-extra ' + classe;
        elemento.innerHTML = '<i class="bi ' + icone + '"></i> ' +
            formatoPercentual.format(Math.abs(variacao)) + '% vs. período anterior';
    }

    function renderVendas(dados) {
        if (!window.Chart) {
            return;
        }

        destruirGrafico('vendas');
        const serie = dados.serie || [];
        const rotulos = serie.map(function (ponto) {
            return ponto.rotulo;
        });

        estado.graficos.vendas = new Chart($('graficoVendas'), {
            type: 'bar',
            data: {
                labels: rotulos,
                datasets: [
                    {
                        label: 'Faturamento',
                        data: serie.map(function (ponto) {
                            return numero(ponto.faturamento);
                        }),
                        backgroundColor: 'rgba(13, 110, 253, 0.65)',
                        borderColor: '#0d6efd',
                        borderWidth: 1,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Pedidos',
                        data: serie.map(function (ponto) {
                            return numero(ponto.pedidos);
                        }),
                        type: 'line',
                        borderColor: '#20c997',
                        backgroundColor: 'rgba(32, 201, 151, 0.15)',
                        pointBackgroundColor: '#20c997',
                        pointRadius: 2,
                        fill: false,
                        lineTension: 0.3,
                        yAxisID: 'y2'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { position: 'top' },
                tooltips: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label: function (item) {
                            if (item.datasetIndex === 0) {
                                return 'Faturamento: ' + moeda(item.yLabel);
                            }
                            return 'Pedidos: ' + formatoNumero.format(item.yLabel);
                        }
                    }
                },
                scales: {
                    xAxes: [{
                        gridLines: { display: false },
                        ticks: { autoSkip: true, maxRotation: 0, maxTicksLimit: 12 }
                    }],
                    yAxes: [
                        {
                            id: 'y',
                            position: 'left',
                            ticks: {
                                beginAtZero: true,
                                callback: function (valor) {
                                    return moedaCompacta(valor);
                                }
                            },
                            gridLines: { color: 'rgba(0, 0, 0, 0.06)' }
                        },
                        {
                            id: 'y2',
                            position: 'right',
                            ticks: {
                                beginAtZero: true,
                                precision: 0,
                                callback: function (valor) {
                                    return formatoNumero.format(valor);
                                }
                            },
                            gridLines: { display: false }
                        }
                    ]
                }
            }
        });
    }

    function renderStatus(dados) {
        if (!window.Chart) {
            return;
        }

        destruirGrafico('status');
        const itens = (dados.porStatus || []).filter(function (item) {
            return numero(item.quantidade) > 0;
        });

        alternarVazio('graficoStatus', 'statusVazio', itens.length === 0);
        if (itens.length === 0) {
            return;
        }

        estado.graficos.status = new Chart($('graficoStatus'), {
            type: 'doughnut',
            data: {
                labels: itens.map(function (item) {
                    return rotuloStatus(item.status);
                }),
                datasets: [{
                    data: itens.map(function (item) {
                        return numero(item.quantidade);
                    }),
                    backgroundColor: itens.map(function (item) {
                        return CORES_STATUS[item.status] || '#adb5bd';
                    }),
                    borderColor: '#ffffff',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutoutPercentage: 62,
                legend: {
                    position: 'bottom',
                    labels: { boxWidth: 12, fontSize: 11, padding: 12 }
                },
                tooltips: {
                    callbacks: {
                        label: function (item, data) {
                            return data.labels[item.index] + ': ' +
                                formatoNumero.format(item.value) + ' pedido(s)';
                        }
                    }
                }
            }
        });
    }

    function renderProdutos(dados) {
        if (!window.Chart) {
            return;
        }

        destruirGrafico('produtos');
        const itens = dados.itens || [];
        const porReceita = dados.ordenarPor === 'receita';

        $('subtituloProdutos').textContent = porReceita
            ? 'Ranking por receita'
            : 'Ranking por quantidade';
        $('badgeProdutos').textContent = itens.length + ' item(ns)';
        alternarVazio('graficoProdutos', 'produtosVazio', itens.length === 0);

        if (itens.length === 0) {
            return;
        }

        estado.graficos.produtos = new Chart($('graficoProdutos'), {
            type: 'horizontalBar',
            data: {
                labels: itens.map(function (item) {
                    return item.nome;
                }),
                datasets: [{
                    label: porReceita ? 'Receita' : 'Quantidade',
                    data: itens.map(function (item) {
                        return numero(porReceita ? item.receita : item.quantidade);
                    }),
                    backgroundColor: 'rgba(13, 110, 253, 0.7)',
                    borderColor: '#0d6efd',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                tooltips: {
                    callbacks: {
                        label: function (item) {
                            const produto = itens[item.index];
                            return [
                                (porReceita ? 'Receita: ' + moeda(produto.receita)
                                    : 'Quantidade: ' + formatoNumero.format(produto.quantidade)),
                                'Participação: ' + percentual(porReceita
                                    ? produto.participacaoReceitaPercentual
                                    : produto.participacaoQuantidadePercentual)
                            ];
                        }
                    }
                },
                scales: {
                    xAxes: [{
                        ticks: {
                            beginAtZero: true,
                            callback: function (valor) {
                                return porReceita ? moedaCompacta(valor) : formatoNumero.format(valor);
                            }
                        },
                        gridLines: { color: 'rgba(0, 0, 0, 0.06)' }
                    }],
                    yAxes: [{
                        ticks: { fontSize: 11 },
                        gridLines: { display: false }
                    }]
                }
            }
        });
    }

    function renderCategorias(vendas) {
        if (!window.Chart) {
            return;
        }

        destruirGrafico('categorias');
        const itens = (vendas.porCategoria || []).filter(function (item) {
            return numero(item.receita) > 0 || numero(item.quantidade) > 0;
        });

        alternarVazio('graficoCategorias', 'categoriasVazio', itens.length === 0);
        if (itens.length === 0) {
            return;
        }

        estado.graficos.categorias = new Chart($('graficoCategorias'), {
            type: 'doughnut',
            data: {
                labels: itens.map(function (item) {
                    return item.categoria;
                }),
                datasets: [{
                    data: itens.map(function (item) {
                        return numero(item.receita);
                    }),
                    backgroundColor: itens.map(function (item, indice) {
                        return CORES_CATEGORIA[indice % CORES_CATEGORIA.length];
                    }),
                    borderColor: '#ffffff',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutoutPercentage: 62,
                legend: {
                    position: 'bottom',
                    labels: { boxWidth: 12, fontSize: 11, padding: 10 }
                },
                tooltips: {
                    callbacks: {
                        label: function (item) {
                            const categoria = itens[item.index];
                            return categoria.categoria + ': ' + moeda(categoria.receita) +
                                ' (' + formatoNumero.format(categoria.quantidade) + ' itens)';
                        }
                    }
                }
            }
        });
    }

    function renderEntregas(vendas) {
        if (!window.Chart) {
            return;
        }

        destruirGrafico('entregas');
        const itens = (vendas.porTipoEntrega || []).filter(function (item) {
            return numero(item.pedidos) > 0;
        });

        alternarVazio('graficoEntregas', 'entregasVazio', itens.length === 0);
        if (itens.length === 0) {
            return;
        }

        estado.graficos.entregas = new Chart($('graficoEntregas'), {
            type: 'doughnut',
            data: {
                labels: itens.map(function (item) {
                    return rotuloEntrega(item.tipo);
                }),
                datasets: [{
                    data: itens.map(function (item) {
                        return numero(item.pedidos);
                    }),
                    backgroundColor: itens.map(function (item) {
                        return CORES_ENTREGA[item.tipo] || '#adb5bd';
                    }),
                    borderColor: '#ffffff',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutoutPercentage: 62,
                legend: {
                    position: 'bottom',
                    labels: { boxWidth: 12, fontSize: 11, padding: 10 }
                },
                tooltips: {
                    callbacks: {
                        label: function (item) {
                            const entrega = itens[item.index];
                            return rotuloEntrega(entrega.tipo) + ': ' +
                                formatoNumero.format(entrega.pedidos) + ' pedido(s) • ' +
                                moeda(entrega.faturamento);
                        }
                    }
                }
            }
        });
    }

    function renderPedidos(pagina) {
        const tbody = $('tabelaPedidos');
        tbody.replaceChildren();

        const pedidos = pagina.content || [];
        const totalElementos = numero(pagina.totalElements);
        const totalPaginas = numero(pagina.totalPages);

        estado.pagina = numero(pagina.page);
        estado.totalPaginas = totalPaginas;
        $('badgePedidos').textContent = formatoNumero.format(totalElementos) + ' pedido(s)';
        $('paginacaoInfo').textContent = totalElementos === 0
            ? 'Nenhum pedido encontrado'
            : 'Página ' + (numero(pagina.page) + 1) + ' de ' + totalPaginas +
                ' • ' + formatoNumero.format(totalElementos) + ' pedido(s)';

        $('btnPaginaAnterior').disabled = numero(pagina.page) <= 0;
        $('btnProximaPagina').disabled = totalPaginas === 0 || numero(pagina.page) + 1 >= totalPaginas;

        if (pedidos.length === 0) {
            const linha = document.createElement('tr');
            const celula = document.createElement('td');
            celula.colSpan = 7;
            celula.className = 'text-center text-muted py-4';
            celula.innerHTML = '<i class="bi bi-inbox fs-4 d-block mb-2"></i>' +
                'Nenhum pedido encontrado para os filtros aplicados.';
            linha.appendChild(celula);
            tbody.appendChild(linha);
            return;
        }

        pedidos.forEach(function (pedido) {
            const linha = document.createElement('tr');

            const id = document.createElement('td');
            id.className = 'ps-3';
            const idTexto = document.createElement('span');
            idTexto.className = 'pedido-id';
            idTexto.title = pedido.id || '';
            idTexto.textContent = pedido.id ? '#' + String(pedido.id).slice(-8) : '—';
            id.appendChild(idTexto);
            linha.appendChild(id);

            linha.appendChild(celulaTexto(pedido.clienteNome || 'Cliente não encontrado'));

            const status = document.createElement('td');
            const badgeStatus = document.createElement('span');
            badgeStatus.className = 'badge ' + classeStatus(pedido.status);
            badgeStatus.textContent = rotuloStatus(pedido.status);
            status.appendChild(badgeStatus);
            linha.appendChild(status);

            const entrega = document.createElement('td');
            const badgeEntrega = document.createElement('span');
            badgeEntrega.className = 'badge badge-entrega';
            badgeEntrega.textContent = rotuloEntrega(pedido.tipoEntrega);
            entrega.appendChild(badgeEntrega);
            linha.appendChild(entrega);

            const total = celulaTexto(moeda(pedido.valorTotal));
            total.className = 'text-end fw-medium';
            linha.appendChild(total);

            const entrada = celulaTexto(formatarDataHora(pedido.dataHoraEntrada));
            entrada.className = 'text-muted small';
            linha.appendChild(entrada);

            const itens = celulaTexto(formatoNumero.format(numero(pedido.quantidadeItens)));
            itens.className = 'text-end pe-3';
            linha.appendChild(itens);

            tbody.appendChild(linha);
        });
    }

    function celulaTexto(texto) {
        const celula = document.createElement('td');
        celula.textContent = texto;
        return celula;
    }

    function atualizarPeriodo(periodo, agrupamento) {
        const rotulos = { dia: 'Por dia', semana: 'Por semana', mes: 'Por mês' };
        const rotulo = rotulos[agrupamento] || agrupamento;
        const inicio = formatarDataISO(periodo && periodo.inicio);
        const fim = formatarDataISO(periodo && periodo.fim);

        $('badgeAgrupamento').textContent = rotulo;
        $('subtituloVendas').textContent = rotulo + ' • ' + inicio + ' a ' + fim;
        $('periodoSelecionado').textContent = 'Período: ' + inicio + ' a ' + fim;
    }

    function formatarDataISO(iso) {
        if (!iso) {
            return '—';
        }
        const partes = String(iso).split('-');
        return partes.length === 3 ? partes[2] + '/' + partes[1] + '/' + partes[0] : iso;
    }

    function formatarDataHora(instant) {
        if (!instant) {
            return '—';
        }
        const data = new Date(instant);
        return Number.isNaN(data.getTime()) ? '—' : formatoDataHora.format(data);
    }

    function rotuloStatus(status) {
        return ROTULOS_STATUS[status] || String(status || 'Não informado').replace(/_/g, ' ');
    }

    function rotuloEntrega(tipo) {
        return ROTULOS_ENTREGA[tipo] || String(tipo || 'Não informado').replace(/_/g, ' ');
    }

    function classeStatus(status) {
        switch (status) {
            case 'FINALIZADO':
                return 'bg-success';
            case 'CANCELADO':
                return 'bg-danger';
            case 'EM_PREPARACAO':
            case 'PRONTO':
                return 'bg-warning text-dark';
            case 'SAIU_PARA_ENTREGA':
                return 'bg-primary';
            case 'RETIRADO':
                return 'bg-info text-dark';
            default:
                return 'bg-secondary';
        }
    }

    function destruirGrafico(chave) {
        if (estado.graficos[chave]) {
            estado.graficos[chave].destroy();
            delete estado.graficos[chave];
        }
    }

    function alternarVazio(idCanvas, idVazio, vazio) {
        $(idCanvas).classList.toggle('d-none', vazio);
        $(idVazio).classList.toggle('d-none', !vazio);
    }

    function numero(valor) {
        const convertido = Number(valor);
        return Number.isFinite(convertido) ? convertido : 0;
    }

    function moeda(valor) {
        return formatoMoeda.format(numero(valor));
    }

    function moedaCompacta(valor) {
        const convertido = numero(valor);
        if (Math.abs(convertido) >= 1000) {
            return 'R$ ' + (convertido / 1000).toLocaleString('pt-BR', {
                maximumFractionDigits: 1
            }) + ' mil';
        }
        return 'R$ ' + convertido.toLocaleString('pt-BR', {
            maximumFractionDigits: 0
        });
    }

    function percentual(valor) {
        return formatoPercentual.format(numero(valor)) + '%';
    }

    function participacao(parte, total) {
        if (numero(total) === 0) {
            return 0;
        }
        return (numero(parte) / numero(total)) * 100;
    }

    function minutos(valor) {
        const total = Math.round(numero(valor));
        if (total < 60) {
            return total + ' min';
        }
        const horas = Math.floor(total / 60);
        const restantes = total % 60;
        return restantes === 0 ? horas + 'h' : horas + 'h ' + restantes + 'min';
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();
