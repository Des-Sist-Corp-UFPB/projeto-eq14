package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Solicitação de transporte feita por um passageiro para uma
 * {@link LinhaProgramada} numa data (SPEC-09).
 *
 * <p>Mapeada para {@code solicitacoes_viagem} (migração V11). Quando o gerente
 * designa a viagem da linha naquela data (materializa a {@link Viagem} — SPEC-06),
 * a solicitação é <strong>alocada</strong>: {@link #viagem} é preenchida e o
 * {@link #status} passa a {@code ALOCADA}. A partir daí o passageiro vê apenas os
 * dados da viagem (motorista, veículo, horário, destino) — nunca informações de
 * outros passageiros.
 *
 * <p>O destino e os horários derivam da {@link #linha} (e da {@link #viagem}
 * quando alocada); não há local de partida, pois o carro busca o passageiro no
 * próprio endereço.
 */
@Entity
@Table(name = "solicitacoes_viagem")
public class SolicitacaoViagem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "passageiro", nullable = false)
    private Usuario passageiro;

    @ManyToOne(optional = false)
    @JoinColumn(name = "linha_programada", nullable = false)
    private LinhaProgramada linha;

    @Column(name = "data_desejada", nullable = false)
    private LocalDate dataDesejada;

    /** Viagem materializada da linha+data (preenchida na alocação); nula enquanto pendente. */
    @ManyToOne
    @JoinColumn(name = "viagem")
    private Viagem viagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacao status;

    @Column(name = "observacao", length = 280)
    private String observacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public SolicitacaoViagem() {
    }

    @PrePersist
    protected void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
        if (this.status == null) {
            this.status = StatusSolicitacao.PENDENTE;
        }
    }

    /** {@code true} se a solicitação já tem uma viagem designada (alocada). */
    public boolean isAlocada() {
        return viagem != null && status == StatusSolicitacao.ALOCADA;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Usuario getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Usuario passageiro) {
        this.passageiro = passageiro;
    }

    public LinhaProgramada getLinha() {
        return linha;
    }

    public void setLinha(LinhaProgramada linha) {
        this.linha = linha;
    }

    public LocalDate getDataDesejada() {
        return dataDesejada;
    }

    public void setDataDesejada(LocalDate dataDesejada) {
        this.dataDesejada = dataDesejada;
    }

    public Viagem getViagem() {
        return viagem;
    }

    public void setViagem(Viagem viagem) {
        this.viagem = viagem;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public void setStatus(StatusSolicitacao status) {
        this.status = status;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }
}
