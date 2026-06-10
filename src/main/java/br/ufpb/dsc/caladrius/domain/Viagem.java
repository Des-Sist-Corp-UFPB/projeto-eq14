package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Viagem planejada pelo gestor: associa um {@link Veiculo}, um motorista
 * ({@link Usuario} com papel {@code MOTORISTA}) e uma {@link Cidade} de destino
 * a uma data e horários.
 *
 * <p>Mapeada para a tabela {@code viagens}. Os relacionamentos usam
 * {@code @ManyToOne} apontando para as colunas FK ({@code veiculo},
 * {@code motorista}, {@code cidade_destino}, {@code criado_por}).
 *
 * <p>Datas/horários usam tipos próprios do Java ({@link LocalDate}/{@link LocalTime}),
 * mapeados para {@code DATE}/{@code TIME} no PostgreSQL.
 */
@Entity
@Table(name = "viagens")
public class Viagem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veiculo", nullable = false)
    private Veiculo veiculo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "motorista", nullable = false)
    private Usuario motorista;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cidade_destino", nullable = false)
    private Cidade cidadeDestino;

    @Column(name = "data_viagem", nullable = false)
    private LocalDate dataViagem;

    @Column(name = "horario_saida", nullable = false)
    private LocalTime horarioSaida;

    @Column(name = "horario_chegada", nullable = false)
    private LocalTime horarioChegada;

    @Column(name = "retorno_previsto")
    private Instant retornoPrevisto;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusViagem status;

    /** Gestor (gerente) que criou a viagem. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    public Viagem() {
    }

    @PrePersist
    protected void prePersist() {
        if (this.status == null) {
            this.status = StatusViagem.PLANEJADA;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(Veiculo veiculo) {
        this.veiculo = veiculo;
    }

    public Usuario getMotorista() {
        return motorista;
    }

    public void setMotorista(Usuario motorista) {
        this.motorista = motorista;
    }

    public Cidade getCidadeDestino() {
        return cidadeDestino;
    }

    public void setCidadeDestino(Cidade cidadeDestino) {
        this.cidadeDestino = cidadeDestino;
    }

    public LocalDate getDataViagem() {
        return dataViagem;
    }

    public void setDataViagem(LocalDate dataViagem) {
        this.dataViagem = dataViagem;
    }

    public LocalTime getHorarioSaida() {
        return horarioSaida;
    }

    public void setHorarioSaida(LocalTime horarioSaida) {
        this.horarioSaida = horarioSaida;
    }

    public LocalTime getHorarioChegada() {
        return horarioChegada;
    }

    public void setHorarioChegada(LocalTime horarioChegada) {
        this.horarioChegada = horarioChegada;
    }

    public Instant getRetornoPrevisto() {
        return retornoPrevisto;
    }

    public void setRetornoPrevisto(Instant retornoPrevisto) {
        this.retornoPrevisto = retornoPrevisto;
    }

    public StatusViagem getStatus() {
        return status;
    }

    public void setStatus(StatusViagem status) {
        this.status = status;
    }

    public Usuario getCriadoPor() {
        return criadoPor;
    }

    public void setCriadoPor(Usuario criadoPor) {
        this.criadoPor = criadoPor;
    }

    @Override
    public String toString() {
        return "Viagem{id=" + id + ", data=" + dataViagem + ", status=" + status + "}";
    }
}
