package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private AuditoriaService auditoriaService;
    private UsuarioRepository usuarioRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Autowired(required = false)
    public void setAuditoriaDependencias(AuditoriaService auditoriaService, UsuarioRepository usuarioRepository) {
        this.auditoriaService = auditoriaService;
        this.usuarioRepository = usuarioRepository;
    }

    public Cliente novoCliente(Cliente cliente) {
        return novoCliente(cliente, null);
    }

    public Cliente novoCliente(Cliente cliente, String username) {
        cliente.setId(null);
        Cliente salvo = clienteRepository.save(cliente);
        auditar(username, "CRIACAO_CLIENTE", salvo.getId(), camposCriacao(salvo), null);
        return salvo;
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public void excluir(String id) {
        excluir(id, null);
    }

    public void excluir(String id, String username) {
        Optional<Cliente> atual = clienteRepository.findById(id);
        if (atual.isEmpty()) {
            if (!clienteRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente nao encontrado");
            }
            clienteRepository.deleteById(id);
            auditar(username, "EXCLUSAO_CLIENTE", id, List.of(), null);
            return;
        }
        clienteRepository.deleteById(id);
        auditar(username, "EXCLUSAO_CLIENTE", id, List.of(
                new AlteracaoCampo("nome", AuditoriaService.texto(atual.get().getNome()), null)
        ), null);
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    public Cliente salvar(Cliente cliente) {
        return salvar(cliente, null);
    }

    public Cliente salvar(Cliente cliente, String username) {
        if (cliente.getId() == null) {
            return novoCliente(cliente, username);
        }

        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Cliente nao encontrado para atualizacao"));
        List<AlteracaoCampo> diff = diffClientes(anterior, cliente);
        Cliente salvo = clienteRepository.save(cliente);
        if (!diff.isEmpty()) {
            auditar(username, "ALTERACAO_CLIENTE", salvo.getId(), diff, null);
        }
        return salvo;
    }

    private List<AlteracaoCampo> camposCriacao(Cliente cliente) {
        return List.of(
                new AlteracaoCampo("nome", null, AuditoriaService.texto(cliente.getNome())),
                new AlteracaoCampo("telefone", null, AuditoriaService.texto(cliente.getTelefone())),
                new AlteracaoCampo("cep", null, AuditoriaService.texto(cliente.getCep())),
                new AlteracaoCampo("logradouro", null, AuditoriaService.texto(cliente.getLogradouro())),
                new AlteracaoCampo("numero", null, AuditoriaService.texto(cliente.getNumero())),
                new AlteracaoCampo("complemento", null, AuditoriaService.texto(cliente.getComplemento())),
                new AlteracaoCampo("bairro", null, AuditoriaService.texto(cliente.getBairro())),
                new AlteracaoCampo("cidade", null, AuditoriaService.texto(cliente.getCidade())),
                new AlteracaoCampo("estado", null, AuditoriaService.texto(cliente.getEstado())),
                new AlteracaoCampo("endereco", null, AuditoriaService.texto(cliente.getEndereco()))
        );
    }

    private List<AlteracaoCampo> diffClientes(Cliente anterior, Cliente novo) {
        List<AlteracaoCampo> diff = new ArrayList<>();
        comparar(diff, "nome", anterior.getNome(), novo.getNome());
        comparar(diff, "telefone", anterior.getTelefone(), novo.getTelefone());
        comparar(diff, "cep", anterior.getCep(), novo.getCep());
        comparar(diff, "logradouro", anterior.getLogradouro(), novo.getLogradouro());
        comparar(diff, "numero", anterior.getNumero(), novo.getNumero());
        comparar(diff, "complemento", anterior.getComplemento(), novo.getComplemento());
        comparar(diff, "bairro", anterior.getBairro(), novo.getBairro());
        comparar(diff, "cidade", anterior.getCidade(), novo.getCidade());
        comparar(diff, "estado", anterior.getEstado(), novo.getEstado());
        comparar(diff, "endereco", anterior.getEndereco(), novo.getEndereco());
        return diff;
    }

    private void comparar(List<AlteracaoCampo> diff, String campo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            diff.add(new AlteracaoCampo(campo, AuditoriaService.texto(antes), AuditoriaService.texto(depois)));
        }
    }

    private void auditar(String username, String tipo, String entidadeId,
                         List<AlteracaoCampo> alteracoes, String justificativa) {
        if (auditoriaService == null || username == null || username.isBlank()) {
            return;
        }
        String perfil = "DESCONHECIDO";
        if (usuarioRepository != null) {
            perfil = usuarioRepository.findByUsername(username)
                    .map(u -> u.getRole() == null ? "DESCONHECIDO" : u.getRole())
                    .orElse("DESCONHECIDO");
        }
        auditoriaService.registrar(username, perfil, tipo, "CLIENTE", entidadeId, alteracoes, justificativa);
    }
}
