package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoria> listar(@RequestParam(required = false) String entidade,
                                         @RequestParam(required = false) String entidadeId) {
        if (entidadeId != null && !entidadeId.isBlank()) {
            return auditoriaService.listarPorEntidadeId(entidadeId);
        }
        if (entidade != null && !entidade.isBlank()) {
            return auditoriaService.listarPorEntidade(entidade.toUpperCase());
        }
        return auditoriaService.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistroAuditoria> buscarPorId(@PathVariable String id) {
        return auditoriaService.listarTodos().stream()
                .filter(r -> r.getId() != null && r.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
