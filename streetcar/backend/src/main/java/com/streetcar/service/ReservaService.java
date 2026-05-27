package com.streetcar.service;

import com.streetcar.model.Locacao;
import com.streetcar.model.Reserva;
import com.streetcar.model.Veiculo;
import com.streetcar.repository.LocacaoRepository;
import com.streetcar.repository.ReservaRepository;
import com.streetcar.repository.VeiculoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReservaService {

    @Autowired private ReservaRepository repo;
    @Autowired private VeiculoRepository veiculoRepo;
    @Autowired private LocacaoRepository locacaoRepo;

    public List<Reserva> listar()                        { return repo.findAll(); }
    public List<Reserva> porCliente(Long clienteId)      { return repo.findByClienteId(clienteId); }
    public Optional<Reserva> buscar(Long id)             { return repo.findById(id); }

    public Reserva criar(Reserva r) {
        Long veiculoId = r.getVeiculo().getId();

        Veiculo veiculo = veiculoRepo.findById(veiculoId)
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado"));

        if (!"Disponível".equals(veiculo.getStatus()))
            throw new IllegalStateException("Veículo indisponível. Status atual: " + veiculo.getStatus());

        if (repo.existeConflito(veiculoId, r.getDataInicio(), r.getDataFim()))
            throw new IllegalStateException("Já existe uma reserva para este veículo neste período");

        r.setStatus("Pendente");
        veiculo.setStatus("Reservado");
        veiculoRepo.save(veiculo);
        return repo.save(r);
    }

    /**
     * Confirmar reserva → cria Locação automaticamente com dados completos do banco.
     * Cancelar reserva  → libera veículo para Disponível.
     *
     * O método recarrega a reserva do banco (com cliente e veículo completos)
     * antes de criar a locação, evitando NullPointerException por lazy-loading.
     */
    public Optional<Reserva> confirmar(Long id) {
        return repo.findById(id).map(r -> {
            if (!"Pendente".equals(r.getStatus()))
                throw new IllegalStateException("Apenas reservas pendentes podem ser confirmadas");

            // Recarrega veículo completo do banco (garante diária e todos os campos)
            Veiculo veiculo = veiculoRepo.findById(r.getVeiculo().getId())
                    .orElseThrow(() -> new IllegalStateException("Veículo não encontrado"));

            veiculo.setStatus("Alugado");
            veiculoRepo.save(veiculo);

            // Calcula valor total
            long dias = java.time.temporal.ChronoUnit.DAYS.between(r.getDataInicio(), r.getDataFim());
            if (dias <= 0) dias = 1;
            double valorTotal = dias * veiculo.getDiaria();

            // Cria locação com dados completos do banco
            Locacao locacao = new Locacao();
            locacao.setCliente(r.getCliente());   // cliente já está carregado pelo JPA
            locacao.setVeiculo(veiculo);           // usa o veiculo recarregado
            locacao.setDataInicio(r.getDataInicio());
            locacao.setDataFim(r.getDataFim());
            locacao.setStatus("Ativa");
            locacao.setValorTotal(valorTotal);
            locacao.setEntregaConfirmada(false);
            locacao.setDataEncerramento(null);
            locacao.setDanos(null);
            locacao.setNivelCombustivel(null);
            locacao.setMultaAtraso(null);
            locacao.setDiasAtraso(null);

            locacaoRepo.save(locacao);

            r.setStatus("Confirmada");
            return repo.save(r);
        });
    }

    public Optional<Reserva> cancelar(Long id) {
        return repo.findById(id).map(r -> {
            if ("Cancelada".equals(r.getStatus()))
                throw new IllegalStateException("Reserva já está cancelada");

            Veiculo veiculo = veiculoRepo.findById(r.getVeiculo().getId())
                    .orElseThrow(() -> new IllegalStateException("Veículo não encontrado"));
            veiculo.setStatus("Disponível");
            veiculoRepo.save(veiculo);

            r.setStatus("Cancelada");
            return repo.save(r);
        });
    }

    // Mantido para compatibilidade, mas confirmar/cancelar são preferíveis
    public Optional<Reserva> atualizar(Long id, Reserva dados) {
        String novoStatus = dados.getStatus();
        if ("Confirmada".equals(novoStatus)) return confirmar(id);
        if ("Cancelada".equals(novoStatus))  return cancelar(id);
        return repo.findById(id).map(r -> {
            r.setDataInicio(dados.getDataInicio());
            r.setDataFim(dados.getDataFim());
            r.setStatus(novoStatus);
            return repo.save(r);
        });
    }

    public boolean deletar(Long id) {
        if (!repo.existsById(id)) return false;
        repo.findById(id).ifPresent(r -> {
            if ("Pendente".equals(r.getStatus()) || "Reservado".equals(r.getStatus())) {
                Veiculo v = veiculoRepo.findById(r.getVeiculo().getId()).orElse(null);
                if (v != null) { v.setStatus("Disponível"); veiculoRepo.save(v); }
            }
        });
        repo.deleteById(id);
        return true;
    }
}
