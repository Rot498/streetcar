package com.streetcar.controller;

import com.streetcar.model.Veiculo;
import com.streetcar.repository.LocacaoRepository;
import com.streetcar.repository.ReservaRepository;
import com.streetcar.repository.VeiculoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/veiculos")
@CrossOrigin(origins = "*")
public class VeiculoController {

    @Autowired private VeiculoRepository repo;
    @Autowired private LocacaoRepository locacaoRepo;
    @Autowired private ReservaRepository reservaRepo;

    @GetMapping
    public List<Veiculo> listar() { return repo.findAll(); }

    @GetMapping("/disponiveis")
    public List<Veiculo> disponiveis() { return repo.findByStatus("Disponível"); }

    @GetMapping("/reservados")
    public List<Veiculo> reservados() { return repo.findByStatus("Reservado"); }

    @GetMapping("/{id}")
    public ResponseEntity<Veiculo> buscar(@PathVariable Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Veiculo> criar(@RequestBody Veiculo v) {
        if (v.getStatus() == null || v.getStatus().isBlank()) v.setStatus("Disponível");
        return ResponseEntity.ok(repo.save(v));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Veiculo> atualizar(@PathVariable Long id, @RequestBody Veiculo dados) {
        return repo.findById(id).map(v -> {
            v.setModelo(dados.getModelo());
            v.setPlaca(dados.getPlaca());
            v.setAno(dados.getAno());
            v.setDiaria(dados.getDiaria());
            v.setStatus(dados.getStatus());
            return ResponseEntity.ok(repo.save(v));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * FIX 4: impede exclusão de veículo com locação ativa ou reserva aberta.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        Veiculo v = repo.findById(id).orElse(null);
        if (v == null) return ResponseEntity.notFound().build();

        if ("Alugado".equals(v.getStatus()))
            return ResponseEntity.status(409).body(Map.of("erro", "Veículo está alugado. Registre a devolução antes de excluir."));

        if ("Reservado".equals(v.getStatus()))
            return ResponseEntity.status(409).body(Map.of("erro", "Veículo possui reserva ativa. Cancele a reserva antes de excluir."));

        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
