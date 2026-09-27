package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AplicarDescontoRequest;
import com.umc.pediupatrao.dto.CancelarPedidoRequest;
import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ATENDENTE')")
    public ResponseEntity<Pedido> criarPedido(@Valid @RequestBody CriarPedidoRequest request,
                                              Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(request, username));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public ResponseEntity<Pedido> atualizarStatus(@PathVariable String id,
                                                   @RequestParam StatusPedido status,
                                                   Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(pedidoService.atualizarStatus(id, status, username));
    }

    @PutMapping("/{id}/desconto")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Pedido> aplicarDesconto(@PathVariable String id,
                                                  @Valid @RequestBody AplicarDescontoRequest request,
                                                  Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(pedidoService.aplicarDesconto(id, request.getPercentual(), username));
    }

    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Pedido> cancelarPedido(@PathVariable String id,
                                                 @Valid @RequestBody CancelarPedidoRequest request,
                                                 Principal principal) {
        String username = principal == null ? null : principal.getName();
        return ResponseEntity.ok(pedidoService.cancelarPedido(id, request.getJustificativa(), username));
    }
}
