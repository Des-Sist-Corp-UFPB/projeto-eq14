package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Repositório da trilha de auditoria.
 */
@Repository
public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID> {

    /** Trilha completa, mais recentes primeiro (visão do SYSADMIN). */
    Page<LogAuditoria> findAllByOrderByInstanteDesc(Pageable pageable);

    /** Trilha filtrada por categoria (ex.: só OPERAÇÃO para o GERENTE). */
    Page<LogAuditoria> findByCategoriaInOrderByInstanteDesc(
            Collection<CategoriaAuditoria> categorias, Pageable pageable);

    /** Trilha filtrada por ação (usada pela central de logs: área e histórico). */
    Page<LogAuditoria> findByAcaoInOrderByInstanteDesc(
            Collection<String> acoes, Pageable pageable);

    /**
     * Eventos que <strong>não</strong> caem em nenhuma área conhecida — é assim que a
     * área "Sistema" é montada, sem precisar listar o que ainda não existe.
     */
    Page<LogAuditoria> findByAcaoNotInOrderByInstanteDesc(
            Collection<String> acoes, Pageable pageable);

    /** Busca livre em ação, autor e detalhe (a caixa de busca da central de logs). */
    @Query("""
            select l from LogAuditoria l
            where lower(l.acao) like %:termo%
               or lower(coalesce(l.usuarioNome, '')) like %:termo%
               or lower(coalesce(l.detalhe, '')) like %:termo%
               or lower(coalesce(l.entidade, '')) like %:termo%
            order by l.instante desc
            """)
    Page<LogAuditoria> buscar(String termo, Pageable pageable);
}
