package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.CodigoVerificacao;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório dos códigos OTP de verificação (SPEC-ACE-03).
 */
@Repository
public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacao, UUID> {

    /** Códigos ainda ativos (não usados) do usuário para uma finalidade — para invalidar ao reemitir. */
    List<CodigoVerificacao> findByUsuarioIdAndFinalidadeAndUsadoEmIsNull(UUID usuarioId, FinalidadeCodigo finalidade);

    /** Código ativo mais recente do usuário para a finalidade — o alvo da validação. */
    Optional<CodigoVerificacao> findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(
            UUID usuarioId, FinalidadeCodigo finalidade);

    /** Código mais recente (usado ou não) do usuário para a finalidade — para o cooldown de reenvio. */
    Optional<CodigoVerificacao> findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(
            UUID usuarioId, FinalidadeCodigo finalidade);
}
