package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
