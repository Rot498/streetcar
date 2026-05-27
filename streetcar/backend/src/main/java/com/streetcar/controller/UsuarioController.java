package com.streetcar.controller;

import com.streetcar.model.Usuario;
import com.streetcar.repository.LocacaoRepository;
import com.streetcar.repository.ReservaRepository;
import com.streetcar.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired private UsuarioService service;
    @Autowired private LocacaoRepository locacaoRepo;
    @Autowired private ReservaRepository reservaRepo;

    @GetMapping
    public List<Usuario> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscar(@PathVariable Long id) {
        return service.buscar(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody Usuario u) {
        if (service.existeEmail(u.getEmail()))
            return ResponseEntity.badRequest().body(Map.of("erro", "Email já cadastrado"));
        // FIX 2: service.criar() já encripta a senha
        return ResponseEntity.ok(service.criar(u));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @RequestBody Usuario dados) {
        return service.atualizar(id, dados).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * FIX 3: impede exclusão de cliente com locações ativas ou reservas pendentes/confirmadas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        boolean temLocacaoAtiva = locacaoRepo.findByClienteId(id).stream()
                .anyMatch(l -> "Ativa".equals(l.getStatus()));
        if (temLocacaoAtiva)
            return ResponseEntity.status(409).body(Map.of("erro", "Cliente possui locação ativa. Encerre a locação antes de excluir."));

        boolean temReservaAberta = reservaRepo.findByClienteId(id).stream()
                .anyMatch(r -> "Pendente".equals(r.getStatus()) || "Confirmada".equals(r.getStatus()));
        if (temReservaAberta)
            return ResponseEntity.status(409).body(Map.of("erro", "Cliente possui reserva ativa. Cancele a reserva antes de excluir."));

        if (!service.deletar(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
