package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusVinculo;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.VinculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Quem pertence a qual secretaria, com qual papel (SPEC-PLT-02 §4 / ADR-22).
 *
 * <p>Dois invariantes governam esta classe:
 *
 * <ul>
 *   <li><strong>RN-MT-08</strong> — o vínculo nasce {@link StatusVinculo#PENDENTE}.
 *       Escolher uma secretaria na tela de cadastro <em>não</em> dá acesso a ela; quem
 *       ativa é um gestor da organização. Sem isso, o auto-cadastro viraria um atalho
 *       para dentro dos dados de qualquer cliente;</li>
 *   <li><strong>isolamento por dono</strong> — toda leitura de um vínculo específico
 *       exige o id do dono, e o que não é seu responde "não encontrado" em vez de
 *       "proibido": negar sem confirmar a existência (mesmo padrão anti-enumeração da
 *       recuperação de senha, SPEC-ACE-03).</li>
 * </ul>
 */
@Service
public class VinculoService {

    private final VinculoRepository vinculoRepository;
    private final AuditoriaService auditoriaService;

    public VinculoService(VinculoRepository vinculoRepository, AuditoriaService auditoriaService) {
        this.vinculoRepository = vinculoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Solicita o vínculo de uma pessoa a uma secretaria. <strong>Idempotente</strong>:
     * pedir de novo devolve o vínculo que já existe — a pessoa clicando duas vezes não
     * gera duas solicitações para o gestor avaliar.
     */
    @Transactional
    public Vinculo solicitar(Usuario usuario, Organizacao organizacao, Papel papel) {
        return vinculoRepository
                .findByUsuarioIdAndOrganizacaoIdAndPapelAndStatusNot(
                        usuario.getId(), organizacao.getId(), papel, StatusVinculo.REVOGADO)
                .orElseGet(() -> vinculoRepository.save(new Vinculo(usuario, organizacao, papel)));
    }

    /** Ativa o vínculo (ação de gestor da organização) e deixa rastro na central de logs. */
    @Transactional
    public Vinculo aprovar(UUID vinculoId, Usuario aprovador) {
        Vinculo vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vínculo não encontrado."));
        vinculo.aprovar(aprovador);
        Vinculo salvo = vinculoRepository.save(vinculo);
        auditoriaService.registrarOperacao("VINCULO_APROVADO", "Vinculo", String.valueOf(vinculoId),
                descrever(salvo));
        return salvo;
    }

    /** Encerra o acesso sem apagar o registro (RN-MT-16). */
    @Transactional
    public void revogar(UUID vinculoId, Usuario responsavel) {
        Vinculo vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vínculo não encontrado."));
        vinculo.revogar();
        vinculoRepository.save(vinculo);
        auditoriaService.registrarOperacao("VINCULO_REVOGADO", "Vinculo", String.valueOf(vinculoId),
                descrever(vinculo));
    }

    /** Os vínculos ativos de uma pessoa — o que alimenta a tela de escolha de contexto. */
    @Transactional(readOnly = true)
    public List<Vinculo> ativosDe(UUID usuarioId) {
        return vinculoRepository.findByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO);
    }

    /**
     * Um vínculo <strong>da própria pessoa</strong>. O de outra responde
     * {@link RecursoNaoEncontradoException} — 404, não 403, para não confirmar que
     * aquele vínculo existe.
     */
    @Transactional(readOnly = true)
    public Vinculo doUsuario(UUID vinculoId, UUID usuarioId) {
        return vinculoRepository.findByIdAndUsuarioId(vinculoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vínculo não encontrado."));
    }

    /** Solicitações pendentes de uma secretaria (fila do gestor). */
    @Transactional(readOnly = true)
    public List<Vinculo> pendentesDe(UUID organizacaoId) {
        return vinculoRepository.findByOrganizacaoIdAndStatus(organizacaoId, StatusVinculo.PENDENTE);
    }

    private String descrever(Vinculo vinculo) {
        return vinculo.getPapel() + " em " + vinculo.getOrganizacao().getNome();
    }
}
