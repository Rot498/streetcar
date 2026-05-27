package com.streetcar.service;

import com.streetcar.model.Locacao;
import com.streetcar.model.Veiculo;
import com.streetcar.repository.LocacaoRepository;
import com.streetcar.repository.ReservaRepository;
import com.streetcar.repository.VeiculoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class LocacaoService {

    @Autowired
    private LocacaoRepository repo;

    @Autowired
    private VeiculoRepository veiculoRepo;

    @Autowired
    private ReservaRepository reservaRepo;

    public List<Locacao> listar() { return repo.findAll(); }
    public List<Locacao> porCliente(Long clienteId) { return repo.findByClienteId(clienteId); }
    public Optional<Locacao> buscar(Long id) { return repo.findById(id); }

    public Locacao criar(Locacao loc) {
        Long veiculoId = loc.getVeiculo().getId();

        Veiculo veiculo = veiculoRepo.findById(veiculoId)
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado"));

        if (!"Disponível".equals(veiculo.getStatus())) {
            throw new IllegalStateException(
                "Veículo indisponível para locação. Status atual: " + veiculo.getStatus()
            );
        }

        if (repo.existeConflito(veiculoId, loc.getDataInicio(), loc.getDataFim())) {
            throw new IllegalStateException("Já existe uma locação ativa para este veículo neste período");
        }

        loc.setStatus("Ativa");
        loc.setEntregaConfirmada(false);
        loc.setDataEncerramento(null);
        loc.setDanos(null);
        loc.setNivelCombustivel(null);
        loc.setMultaAtraso(null);
        loc.setDiasAtraso(null);

        veiculo.setStatus("Alugado");
        veiculoRepo.save(veiculo);

        return repo.save(loc);
    }

    /**
     * Percentual de taxa de combustível com base no nível devolvido.
     * Quanto menos combustível, maior a taxa (aplicada sobre o valor base).
     */
    private double taxaCombustivel(String nivel) {
        if (nivel == null) return 0.0;
        return switch (nivel) {
            case "Cheio" -> 0.0;   // sem taxa
            case "3/4"   -> 0.05;  // 5%
            case "1/2"   -> 0.10;  // 10%
            case "1/4"   -> 0.20;  // 20%
            case "Vazio" -> 0.30;  // 30%
            default      -> 0.0;
        };
    }

    /**
     * Registra a vistoria e encerra a locação em uma única operação.
     * Calcula automaticamente:
     *   - Multa por atraso (diária × dias extras)
     *   - Taxa de avaria: +30% se houver danos (batido/arranhado)
     *   - Taxa de combustível: 0–30% conforme nível devolvido
     * Define status do veículo: "Manutenção" se houver danos, "Disponível" caso contrário.
     */
    public Optional<Locacao> registrarDevolucao(Long id, String danos, String nivelCombustivel, LocalDate dataDevReal) {
        return repo.findById(id).map(loc -> {
            if (!"Ativa".equals(loc.getStatus())) {
                throw new IllegalStateException("Apenas locações ativas podem ser devolvidas");
            }

            double valorBase = loc.getValorTotal();

            // ── 1. Multa por atraso ────────────────────────────────────────
            LocalDate dataFimPrevista = loc.getDataFim();
            int diasAtraso = 0;
            double multa = 0.0;

            if (dataDevReal.isAfter(dataFimPrevista)) {
                diasAtraso = (int) ChronoUnit.DAYS.between(dataFimPrevista, dataDevReal);
                double diaria = loc.getVeiculo().getDiaria();
                multa = diasAtraso * diaria;
                valorBase += multa;
            }

            // ── 2. Taxa de avaria (30% sobre valor base se houver danos) ──
            boolean temDanos = danos != null && !danos.isBlank();
            double taxaAvaria = temDanos ? valorBase * 0.30 : 0.0;

            // ── 3. Taxa de combustível (% sobre valor base) ───────────────
            double percComb   = taxaCombustivel(nivelCombustivel);
            double taxaComb   = valorBase * percComb;

            // ── 4. Valor total final ───────────────────────────────────────
            double valorFinal = valorBase + taxaAvaria + taxaComb;

            // Campos de vistoria
            loc.setDanos(temDanos ? danos : "Nenhum");
            loc.setNivelCombustivel(nivelCombustivel);
            loc.setDiasAtraso(diasAtraso);
            loc.setMultaAtraso(multa);
            loc.setTaxaAvaria(taxaAvaria);
            loc.setTaxaCombustivel(taxaComb);
            loc.setValorTotal(valorFinal);

            // Encerra
            loc.setStatus("Encerrada");
            loc.setDataEncerramento(dataDevReal);
            loc.setEntregaConfirmada(true);

            // ── 6. Encerra a reserva vinculada (se existir) ───────────────
            reservaRepo.findByClienteIdAndVeiculoIdAndStatus(
                loc.getCliente().getId(),
                loc.getVeiculo().getId(),
                "Confirmada"
            ).ifPresent(r -> {
                r.setStatus("Encerrada");
                reservaRepo.save(r);
            });

            // ── 5. Status do veículo ───────────────────────────────────────
            Veiculo v = loc.getVeiculo();
            v.setStatus(temDanos ? "Manutenção" : "Disponível");
            veiculoRepo.save(v);

            return repo.save(loc);
        });
    }

    public Optional<Locacao> atualizar(Long id, Locacao dados) {
        return repo.findById(id).map(loc -> {
            loc.setDataInicio(dados.getDataInicio());
            loc.setDataFim(dados.getDataFim());
            loc.setStatus(dados.getStatus());
            loc.setValorTotal(dados.getValorTotal());

            if ("Encerrada".equals(dados.getStatus()) || "Cancelada".equals(dados.getStatus())) {
                loc.setDataEncerramento(LocalDate.now());
                Veiculo v = loc.getVeiculo();
                v.setStatus("Disponível");
                veiculoRepo.save(v);
            }

            return repo.save(loc);
        });
    }

    public boolean deletar(Long id) {
        if (!repo.existsById(id)) return false;
        repo.findById(id).ifPresent(loc -> {
            if ("Ativa".equals(loc.getStatus())) {
                Veiculo v = loc.getVeiculo();
                v.setStatus("Disponível");
                veiculoRepo.save(v);
            }
        });
        repo.deleteById(id);
        return true;
    }
}
