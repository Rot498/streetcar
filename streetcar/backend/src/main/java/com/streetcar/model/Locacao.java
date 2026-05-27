package com.streetcar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "locacoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Locacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private LocalDate dataFim;

    // "Ativa", "Encerrada", "Cancelada"
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Double valorTotal;

    // --- Devolução ---

    // Data real de encerramento (histórico)
    @Column
    private LocalDate dataEncerramento;

    // Etapa 1: entrega confirmada pelo ADM antes de encerrar
    @Column(nullable = false)
    private Boolean entregaConfirmada = false;

    // --- Vistoria ---

    // Danos encontrados no veículo
    @Column
    private String danos;

    // Nível de combustível na devolução: "Cheio", "3/4", "1/2", "1/4", "Vazio"
    @Column
    private String nivelCombustivel;

    // Valor da multa por atraso (calculado automaticamente)
    @Column
    private Double multaAtraso;

    // Dias de atraso registrados
    @Column
    private Integer diasAtraso;

    // Taxa de avaria: 30% sobre valor base se veículo foi devolvido com danos
    @Column
    private Double taxaAvaria;

    // Taxa de combustível: % sobre valor base conforme nível devolvido
    @Column
    private Double taxaCombustivel;
}
