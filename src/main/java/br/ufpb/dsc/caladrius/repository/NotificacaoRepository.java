package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório das notificações in-app.
 */
@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    /** Não lidas do usuário (para o sino). */
    List<Notificacao> findByUsuarioIdAndLidaFalseOrderByCriadoEmDesc(UUID usuarioId);

    /** Conta de não lidas (badge do sino). */
    long countByUsuarioIdAndLidaFalse(UUID usuarioId);
}
