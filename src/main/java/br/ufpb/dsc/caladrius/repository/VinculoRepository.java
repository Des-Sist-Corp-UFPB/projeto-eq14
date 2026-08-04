package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso aos vínculos pessoa × secretaria × papel (SPEC-PLT-02).
 *
 * <p>As buscas por vínculo de uma pessoa recebem sempre o id do dono
 * ({@link #findByIdAndUsuarioId}) — é o mesmo isolamento por dono já usado nas
 * solicitações do passageiro (SPEC-VIA-03).
 */
public interface VinculoRepository extends JpaRepository<Vinculo, UUID> {

    List<Vinculo> findByUsuarioIdAndStatus(UUID usuarioId, StatusVinculo status);

    Optional<Vinculo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * O vínculo <strong>vivo</strong> daquela pessoa naquela secretaria com aquele papel.
     * Exclui os revogados de propósito: o índice único parcial (V16) garante no máximo
     * um não-revogado, e a pessoa pode ser readmitida sem colidir com o histórico.
     */
    Optional<Vinculo> findByUsuarioIdAndOrganizacaoIdAndPapelAndStatusNot(
            UUID usuarioId, UUID organizacaoId, Papel papel, StatusVinculo statusExcluido);

    List<Vinculo> findByOrganizacaoIdAndStatus(UUID organizacaoId, StatusVinculo status);
}
