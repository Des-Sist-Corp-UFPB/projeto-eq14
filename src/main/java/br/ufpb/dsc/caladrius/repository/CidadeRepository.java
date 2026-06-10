package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link Cidade}.
 *
 * <p>Cidades não usam soft-delete (são dados de referência estáveis).
 */
@Repository
public interface CidadeRepository extends JpaRepository<Cidade, UUID> {

    /**
     * Busca cidades cujo nome contenha o texto informado (case-insensitive).
     *
     * @param busca    trecho do nome
     * @param pageable paginação/ordenação
     * @return página de cidades
     */
    @Query("""
            select c from Cidade c
            where lower(c.nome) like lower(concat('%', :busca, '%'))
            """)
    Page<Cidade> buscar(@Param("busca") String busca, Pageable pageable);

    /** Lista todas as cidades ordenadas por nome (para selects/dropdowns). */
    List<Cidade> findAllByOrderByNomeAsc();

    /** Lista cidades de um tipo específico, ordenadas por nome. */
    List<Cidade> findByTipoOrderByNomeAsc(TipoCidade tipo);
}
