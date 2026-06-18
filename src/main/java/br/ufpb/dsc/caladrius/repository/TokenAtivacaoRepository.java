package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.TokenAtivacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório dos tokens de ativação (convites).
 */
@Repository
public interface TokenAtivacaoRepository extends JpaRepository<TokenAtivacao, UUID> {

    /** Busca pelo hash do token (o valor cru nunca é persistido). */
    Optional<TokenAtivacao> findByTokenHash(String tokenHash);
}
