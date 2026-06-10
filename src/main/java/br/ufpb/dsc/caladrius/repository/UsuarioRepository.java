package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link Usuario}.
 *
 * <p>Os métodos filtram {@code removido_em IS NULL} (soft-delete). Inclui as
 * buscas por e-mail e por telefone usadas no login flexível (entrar com e-mail
 * OU telefone) e a checagem de unicidade no cadastro.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** Lista usuários ativos, paginados. */
    Page<Usuario> findByRemovidoEmIsNull(Pageable pageable);

    /** Busca um usuário ativo pelo id. */
    Optional<Usuario> findByIdAndRemovidoEmIsNull(UUID id);

    /** Busca por e-mail (login por e-mail). Único entre ativos. */
    Optional<Usuario> findByEmailIgnoreCaseAndRemovidoEmIsNull(String email);

    /** Busca por telefone (login por telefone). Único entre ativos. */
    Optional<Usuario> findByTelefoneAndRemovidoEmIsNull(String telefone);

    /**
     * Busca usuários ativos por nome, telefone, e-mail ou CPF (case-insensitive).
     */
    @Query("""
            select u from Usuario u
            where u.removidoEm is null
              and (lower(u.nomeCompleto) like lower(concat('%', :busca, '%'))
                or u.telefone like concat('%', :busca, '%')
                or lower(u.email) like lower(concat('%', :busca, '%'))
                or u.cpf like concat('%', :busca, '%'))
            """)
    Page<Usuario> buscar(@Param("busca") String busca, Pageable pageable);

    /** Lista usuários ativos que possuem determinado papel (ex.: motoristas). */
    @Query("""
            select u from Usuario u
            where u.removidoEm is null and :papel member of u.papeis
            order by u.nomeCompleto asc
            """)
    List<Usuario> listarPorPapel(@Param("papel") Papel papel);

    boolean existsByTelefoneAndRemovidoEmIsNull(String telefone);

    boolean existsByEmailIgnoreCaseAndRemovidoEmIsNull(String email);

    boolean existsByCpfAndRemovidoEmIsNull(String cpf);

    /** Conta usuários ativos (para o painel inicial). */
    long countByRemovidoEmIsNull();
}
