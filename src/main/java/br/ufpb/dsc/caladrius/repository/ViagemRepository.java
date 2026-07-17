package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Viagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link Viagem}.
 */
@Repository
public interface ViagemRepository extends JpaRepository<Viagem, UUID> {

    /**
     * Lista viagens já com veículo, motorista e cidade carregados (JOIN FETCH),
     * evitando o problema de N+1 consultas na tela de listagem.
     *
     * <p>Como todos os relacionamentos são {@code @ManyToOne} (sem coleções), o
     * {@code JOIN FETCH} é compatível com paginação.
     */
    @Query(value = """
            select v from Viagem v
            join fetch v.veiculo
            join fetch v.motorista
            join fetch v.cidadeDestino
            order by v.dataViagem desc, v.horarioSaida desc
            """,
            countQuery = "select count(v) from Viagem v")
    Page<Viagem> listarComRelacionamentos(Pageable pageable);

    /** Conta as viagens cujo destino é a cidade informada (DT-01: aviso de exclusão em cascata). */
    long countByCidadeDestino_Id(UUID cidadeId);

    /** Remove as viagens cujo destino é a cidade informada (DT-01: cascade ao excluir cidade). */
    void deleteByCidadeDestino_Id(UUID cidadeId);

    /** Conta as viagens associadas a um motorista (regras de disponibilidade/usuários). */
    long countByMotorista_Id(UUID motoristaId);

    // ===================== SPEC-06 =====================

    /** Viagens de um intervalo de datas (painel semanal), com relacionamentos carregados. */
    @Query("""
            select v from Viagem v
            join fetch v.veiculo join fetch v.motorista join fetch v.cidadeDestino
            where v.dataViagem between :inicio and :fim
            order by v.dataViagem asc, v.horarioSaida asc
            """)
    List<Viagem> listarDaSemana(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Ocorrência já materializada de uma linha numa data (evita duplicar designação). */
    Optional<Viagem> findByLinhaProgramada_IdAndDataViagem(UUID linhaId, LocalDate data);

    /**
     * Viagens candidatas para alocar uma solicitação sob demanda (SPEC-11): mesmo
     * destino e data, não canceladas, com veículo/motorista carregados.
     */
    @Query("""
            select v from Viagem v
            join fetch v.veiculo join fetch v.motorista join fetch v.cidadeDestino
            where v.cidadeDestino.id = :cidadeDestinoId and v.dataViagem = :data
              and v.status <> br.ufpb.dsc.caladrius.domain.enums.StatusViagem.CANCELADA
            order by v.horarioSaida asc
            """)
    List<Viagem> candidatasParaDemanda(@Param("cidadeDestinoId") UUID cidadeDestinoId,
                                       @Param("data") LocalDate data);

    /** Conta as viagens (ocorrências) originadas de uma linha (proteção na exclusão). */
    long countByLinhaProgramada_Id(UUID linhaId);

    /** Viagens designadas a um motorista numa data (visão do motorista). */
    @Query("""
            select v from Viagem v
            join fetch v.veiculo join fetch v.cidadeDestino
            where v.motorista.id = :motoristaId
            order by v.dataViagem desc, v.horarioSaida desc
            """)
    List<Viagem> listarDoMotorista(@Param("motoristaId") UUID motoristaId);

    /**
     * Conta conflitos de horário do MOTORISTA na mesma data (RN-VIA-08): viagens
     * não canceladas cuja janela [saida, chegada) se sobrepõe à informada.
     */
    @Query("""
            select count(v) from Viagem v
            where v.motorista.id = :motoristaId and v.dataViagem = :data
              and v.status <> br.ufpb.dsc.caladrius.domain.enums.StatusViagem.CANCELADA
              and v.horarioSaida < :chegada and v.horarioChegada > :saida
              and (:ignorarId is null or v.id <> :ignorarId)
            """)
    long contarConflitosMotorista(@Param("motoristaId") UUID motoristaId,
                                  @Param("data") LocalDate data,
                                  @Param("saida") LocalTime saida,
                                  @Param("chegada") LocalTime chegada,
                                  @Param("ignorarId") UUID ignorarId);

    /** Conta conflitos de horário do VEÍCULO na mesma data (RN-VIA-08). */
    @Query("""
            select count(v) from Viagem v
            where v.veiculo.id = :veiculoId and v.dataViagem = :data
              and v.status <> br.ufpb.dsc.caladrius.domain.enums.StatusViagem.CANCELADA
              and v.horarioSaida < :chegada and v.horarioChegada > :saida
              and (:ignorarId is null or v.id <> :ignorarId)
            """)
    long contarConflitosVeiculo(@Param("veiculoId") UUID veiculoId,
                                @Param("data") LocalDate data,
                                @Param("saida") LocalTime saida,
                                @Param("chegada") LocalTime chegada,
                                @Param("ignorarId") UUID ignorarId);
}
