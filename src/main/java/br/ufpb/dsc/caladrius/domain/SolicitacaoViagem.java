package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.domain.enums.TipoSolicitacao;
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
import java.time.LocalTime;
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

    /** Tipo da solicitação: por linha (SPEC-09) ou sob demanda (SPEC-11). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoSolicitacao tipo = TipoSolicitacao.POR_LINHA;

    /** Linha escolhida — obrigatória em {@code POR_LINHA}, nula em {@code SOB_DEMANDA}. */
    @ManyToOne
    @JoinColumn(name = "linha_programada")
    private LinhaProgramada linha;

    /** Destino informado — obrigatório em {@code SOB_DEMANDA}, nulo em {@code POR_LINHA}. */
    @ManyToOne
    @JoinColumn(name = "cidade_destino")
    private Cidade cidadeDestino;

    @Column(name = "data_desejada", nullable = false)
    private LocalDate dataDesejada;

    /** Horário desejado da viagem/consulta (sob demanda). */
    @Column(name = "horario_desejado")
    private LocalTime horarioDesejado;

    /** Condições de saúde (comorbidade/deficiência) que informam a prioridade. */
    @Column(name = "condicoes", length = 280)
    private String condicoes;

    /** Motivo preenchido quando o gestor recusa a solicitação sob demanda. */
    @Column(name = "motivo_recusa", length = 280)
    private String motivoRecusa;

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
        if (this.tipo == null) {
            this.tipo = TipoSolicitacao.POR_LINHA;
        }
    }

    /** {@code true} se a solicitação já tem uma viagem designada (alocada). */
    public boolean isAlocada() {
        return viagem != null && status == StatusSolicitacao.ALOCADA;
    }

    /**
     * Cidade de destino da solicitação, resolvida pelo tipo: em {@code POR_LINHA}
     * vem da linha; em {@code SOB_DEMANDA} é a {@link #cidadeDestino} informada.
     */
    public Cidade getDestino() {
        if (cidadeDestino != null) {
            return cidadeDestino;
        }
        return linha != null ? linha.getCidadeDestino() : null;
    }

    public TipoSolicitacao getTipo() {
        return tipo;
    }

    public void setTipo(TipoSolicitacao tipo) {
        this.tipo = tipo;
    }

    public Cidade getCidadeDestino() {
        return cidadeDestino;
    }

    public void setCidadeDestino(Cidade cidadeDestino) {
        this.cidadeDestino = cidadeDestino;
    }

    public LocalTime getHorarioDesejado() {
        return horarioDesejado;
    }

    public void setHorarioDesejado(LocalTime horarioDesejado) {
        this.horarioDesejado = horarioDesejado;
    }

    public String getCondicoes() {
        return condicoes;
    }

    public void setCondicoes(String condicoes) {
        this.condicoes = condicoes;
    }

    public String getMotivoRecusa() {
        return motivoRecusa;
    }

    public void setMotivoRecusa(String motivoRecusa) {
        this.motivoRecusa = motivoRecusa;
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
