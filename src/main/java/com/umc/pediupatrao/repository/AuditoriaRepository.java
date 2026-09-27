package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditoriaRepository extends MongoRepository<RegistroAuditoria, String> {
    List<RegistroAuditoria> findByEntidadeOrderByDataHoraDesc(String entidade);

    List<RegistroAuditoria> findByEntidadeIdOrderByDataHoraDesc(String entidadeId);

    List<RegistroAuditoria> findByUsuarioOrderByDataHoraDesc(String usuario);
}
