package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.IdentidadeOauth;
import br.ufpb.dsc.caladrius.domain.enums.ProvedorOauth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link IdentidadeOauth} (SPEC-08).
 */
@Repository
public interface IdentidadeOauthRepository extends JpaRepository<IdentidadeOauth, UUID> {

    /** Localiza o vínculo pela chave de identidade ({@code provedor}, {@code sub}). */
    Optional<IdentidadeOauth> findByProvedorAndSubjectId(ProvedorOauth provedor, String subjectId);

    /** {@code true} se já existe vínculo para a chave de identidade. */
    boolean existsByProvedorAndSubjectId(ProvedorOauth provedor, String subjectId);
}
