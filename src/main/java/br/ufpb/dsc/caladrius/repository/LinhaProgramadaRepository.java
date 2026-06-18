package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório das linhas programadas (templates recorrentes — SPEC-06).
 */
@Repository
public interface LinhaProgramadaRepository extends JpaRepository<LinhaProgramada, UUID> {

    /** Todas as linhas (gestão), ordenadas por horário de saída. */
    List<LinhaProgramada> findAllByOrderByHorarioSaidaAsc();

    /** Linhas ativas que operam num dia da semana (painel semanal). */
    @Query("""
            select l from LinhaProgramada l
            where l.ativa = true and :dia member of l.dias
            order by l.horarioSaida asc
            """)
    List<LinhaProgramada> ativasNoDia(@Param("dia") DiaSemana dia);
}
