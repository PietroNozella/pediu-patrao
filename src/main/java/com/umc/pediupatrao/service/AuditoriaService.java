package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public RegistroAuditoria registrar(String usuario, String perfil, String tipoOperacao,
                                       String entidade, String entidadeId,
                                       List<AlteracaoCampo> camposAlterados,
                                       String justificativa) {
        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setUsuario(usuario);
        registro.setPerfil(perfil);
        registro.setDataHora(Instant.now());
        registro.setTipoOperacao(tipoOperacao);
        registro.setEntidade(entidade);
        registro.setEntidadeId(entidadeId);
        registro.setCamposAlterados(camposAlterados == null ? List.of() : List.copyOf(camposAlterados));
        registro.setJustificativa(justificativa);
        return auditoriaRepository.save(registro);
    }

    public List<RegistroAuditoria> listarTodos() {
        return auditoriaRepository.findAll();
    }

    public List<RegistroAuditoria> listarPorEntidade(String entidade) {
        return auditoriaRepository.findByEntidadeOrderByDataHoraDesc(entidade);
    }

    public List<RegistroAuditoria> listarPorEntidadeId(String entidadeId) {
        return auditoriaRepository.findByEntidadeIdOrderByDataHoraDesc(entidadeId);
    }

    public static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        return Objects.toString(valor);
    }
}
