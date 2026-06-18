package br.ufpb.dsc.caladrius.domain;

import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Linha programada (template recorrente) das viagens rotineiras (SPEC-06).
 *
 * <p>Define origem→destino, horários e os <strong>dias da semana</strong>
 * (tabela filha {@code linha_dias}, via {@link ElementCollection} — ADR-12) em
 * que opera. As ocorrências ({@link Viagem}) são materializadas sob demanda.
 */
@Entity
@Table(name = "linhas_programadas")
public class LinhaProgramada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "cidade_origem")
    private Cidade cidadeOrigem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cidade_destino", nullable = false)
    private Cidade cidadeDestino;

    @Column(name = "horario_saida", nullable = false)
    private LocalTime horarioSaida;

    @Column(name = "horario_chegada", nullable = false)
    private LocalTime horarioChegada;

    @Column(name = "horario_retorno")
    private LocalTime horarioRetorno;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "linha_dias", joinColumns = @JoinColumn(name = "linha"))
    @Column(name = "dia")
    @Enumerated(EnumType.STRING)
    private Set<DiaSemana> dias = EnumSet.noneOf(DiaSemana.class);

    public LinhaProgramada() {
    }

    public boolean operaEm(DiaSemana dia) {
        return dias.contains(dia);
    }

    public UUID getId() {
        return id;
    }

    public Cidade getCidadeOrigem() {
        return cidadeOrigem;
    }

    public void setCidadeOrigem(Cidade cidadeOrigem) {
        this.cidadeOrigem = cidadeOrigem;
    }

    public Cidade getCidadeDestino() {
        return cidadeDestino;
    }

    public void setCidadeDestino(Cidade cidadeDestino) {
        this.cidadeDestino = cidadeDestino;
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

    public LocalTime getHorarioRetorno() {
        return horarioRetorno;
    }

    public void setHorarioRetorno(LocalTime horarioRetorno) {
        this.horarioRetorno = horarioRetorno;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public Set<DiaSemana> getDias() {
        return dias;
    }

    public void setDias(Set<DiaSemana> dias) {
        this.dias = dias;
    }
}
