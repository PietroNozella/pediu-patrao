package com.umc.pediupatrao.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

@Configuration
public class MongoInitConfig implements CommandLineRunner {

    private final MongoOperations mongoOperations;

    public MongoInitConfig(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public void run(String... args) {
        for (String collection : List.of("pedidos", "clientes", "usuarios", "produtos", "auditoria")) {
            if (!mongoOperations.collectionExists(collection)) {
                mongoOperations.createCollection(collection);
            }
        }

        criarIndices();
        migrarPedidosLegados();
    }

    private void criarIndices() {
        IndexOperations indicesPedidos = mongoOperations.indexOps("pedidos");
        if (indicesPedidos != null) {
            indicesPedidos.ensureIndex(new Index()
                    .on("dataHoraEntrada", Sort.Direction.DESC));
            indicesPedidos.ensureIndex(new Index()
                    .on("status", Sort.Direction.ASC)
                    .on("dataHoraEntrada", Sort.Direction.DESC));
            indicesPedidos.ensureIndex(new Index()
                    .on("clienteId", Sort.Direction.ASC));
        }

        IndexOperations indicesProdutos = mongoOperations.indexOps("produtos");
        if (indicesProdutos != null) {
            indicesProdutos.ensureIndex(new Index().on("tipo", Sort.Direction.ASC));
        }
    }

    private void migrarPedidosLegados() {
        atualizarStatusLegado("RECEBIDO", "NOVO");
        atualizarStatusLegado("EM_PREPARACAO", "EM_PREPARO", "EM_PREPARAÇÃO");
        atualizarStatusLegado("FINALIZADO", "ENTREGUE");

        mongoOperations.updateMulti(
                Query.query(Criteria.where("version").is(null)),
                new Update().set("version", 0L),
                "pedidos");
    }

    private void atualizarStatusLegado(String novoStatus, String... statusLegados) {
        mongoOperations.updateMulti(
                Query.query(Criteria.where("status").in((Object[]) statusLegados)),
                new Update().set("status", novoStatus),
                "pedidos");
    }
}
