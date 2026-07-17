package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.MensagemWhatsapp;
import br.ufpb.dsc.caladrius.domain.enums.DirecaoMensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link MensagemWhatsapp} (SPEC-10).
 */
@Repository
public interface MensagemWhatsappRepository extends JpaRepository<MensagemWhatsapp, UUID> {

    /** Últimas mensagens para o painel do gerente (RN-WPP-10). */
    List<MensagemWhatsapp> findByOrderByCriadoEmDesc(Pageable pageable);

    /** Idempotência do webhook: a mensagem recebida já foi processada? (RN-WPP-03) */
    boolean existsByIdProvedorAndDirecao(String idProvedor, DirecaoMensagem direcao);
}
