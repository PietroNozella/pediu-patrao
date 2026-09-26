package com.umc.pediupatrao.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MongoInitConfigTest {

    @Mock
    private MongoOperations mongoOperations;

    @Test
    void migraSomenteStatusLegadosEInicializaVersaoAusente() {
        MongoInitConfig config = new MongoInitConfig(mongoOperations);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);

        config.run();

        verify(mongoOperations, times(4)).updateMulti(
                queryCaptor.capture(), updateCaptor.capture(), eq("pedidos"));

        List<Query> queries = queryCaptor.getAllValues();
        List<UpdateDefinition> updates = updateCaptor.getAllValues();

        assertEquals(statusQuery("NOVO"), queries.get(0).getQueryObject());
        assertEquals(statusUpdate("RECEBIDO"), updates.get(0).getUpdateObject());
        assertEquals(statusQuery("EM_PREPARO", "EM_PREPARAÇÃO"), queries.get(1).getQueryObject());
        assertEquals(statusUpdate("EM_PREPARACAO"), updates.get(1).getUpdateObject());
        assertEquals(statusQuery("ENTREGUE"), queries.get(2).getQueryObject());
        assertEquals(statusUpdate("FINALIZADO"), updates.get(2).getUpdateObject());
        assertEquals(new Document("version", null), queries.get(3).getQueryObject());
        assertEquals(new Document("$set", new Document("version", 0L)), updates.get(3).getUpdateObject());
    }

    private Document statusQuery(String... status) {
        return new Document("status", new Document("$in", List.of(status)));
    }

    private Document statusUpdate(String status) {
        return new Document("$set", new Document("status", status));
    }
}
