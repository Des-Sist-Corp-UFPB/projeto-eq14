package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.ConversaBot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link ConversaBot} (SPEC-10).
 */
@Repository
public interface ConversaBotRepository extends JpaRepository<ConversaBot, UUID> {

    /** Conversa do número (única — índice {@code ux_conversa_bot_telefone}). */
    Optional<ConversaBot> findByTelefone(String telefone);
}
