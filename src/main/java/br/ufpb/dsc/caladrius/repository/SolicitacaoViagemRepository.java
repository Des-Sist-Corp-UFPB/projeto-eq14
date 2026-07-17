package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.domain.enums.TipoSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repositório das solicitações de viagem do passageiro (SPEC-09).
 */
@Repository
public interface SolicitacaoViagemRepository extends JpaRepository<SolicitacaoViagem, UUID> {

    /**
     * Solicitações de um passageiro (aba "Minhas viagens"), com a linha/destino e
     * — quando alocada — a viagem e seus relacionamentos carregados (evita N+1 e
     * {@code LazyInitializationException}). Mais recentes primeiro.
     *
     * <p>Usa {@code left join} na linha porque a solicitação <strong>sob demanda</strong>
     * (SPEC-11) não tem linha; o destino, nesse caso, vem de {@code cidadeDestino}.
     */
    @Query("""
            select s from SolicitacaoViagem s
            left join fetch s.linha li
            left join fetch li.cidadeDestino
            left join fetch s.cidadeDestino
            left join fetch s.viagem v
            left join fetch v.veiculo
            left join fetch v.motorista
            left join fetch v.cidadeDestino
            where s.passageiro.id = :passageiroId
            order by s.criadoEm desc
            """)
    List<SolicitacaoViagem> listarDoPassageiro(@Param("passageiroId") UUID passageiroId);

    /**
     * Existe solicitação ativa (não cancelada) do passageiro para a mesma
     * linha+data? Espelha o índice único parcial {@code ux_solicitacao_viagem_unica}.
     */
    boolean existsByPassageiro_IdAndLinha_IdAndDataDesejadaAndStatusNot(
            UUID passageiroId, UUID linhaId, LocalDate dataDesejada, StatusSolicitacao status);

    /**
     * Existe demanda ativa (não cancelada) do passageiro para o mesmo destino+data?
     * Espelha o índice único parcial {@code ux_solicitacao_demanda_unica} (RN-AVU-07).
     */
    boolean existsByPassageiro_IdAndCidadeDestino_IdAndDataDesejadaAndStatusNot(
            UUID passageiroId, UUID cidadeDestinoId, LocalDate dataDesejada, StatusSolicitacao status);

    /**
     * Fila do gestor: solicitações <strong>sob demanda</strong> num status
     * (ex.: {@code PENDENTE}), com passageiro e destino carregados. Mais antigas
     * primeiro (quem pediu antes aparece antes).
     */
    @Query("""
            select s from SolicitacaoViagem s
            join fetch s.passageiro
            left join fetch s.cidadeDestino
            where s.tipo = :tipo and s.status = :status
            order by s.criadoEm asc
            """)
    List<SolicitacaoViagem> listarPorTipoEStatus(@Param("tipo") TipoSolicitacao tipo,
                                                 @Param("status") StatusSolicitacao status);
}
