package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.IdentidadeOauth;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.ProvedorOauth;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.IdentidadeOauthRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

/**
 * Resolve o login social (OAuth2/OIDC) em um {@link Usuario} do sistema — SPEC-08.
 *
 * <p>É o <strong>núcleo da regra de negócio</strong> da SPEC-08: dada a identidade
 * devolvida pelo provedor (Google), aplica a resolução em três passos
 * (identidade → e-mail verificado → auto-provisão). Mantida separada do
 * {@code CaladriusOidcUserService} (camada web/segurança) para ser testável sem
 * subir o contexto OAuth.
 */
@Service
@Transactional(readOnly = true)
public class IdentidadeOauthService {

    private final IdentidadeOauthRepository identidadeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public IdentidadeOauthService(IdentidadeOauthRepository identidadeRepository,
                                  UsuarioRepository usuarioRepository,
                                  AuditoriaService auditoriaService) {
        this.identidadeRepository = identidadeRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Resolve um login social no {@link Usuario} correspondente, vinculando ou
     * criando a conta conforme a §2.1 da SPEC-08.
     *
     * @param provedor         provedor externo (ex.: {@link ProvedorOauth#GOOGLE})
     * @param subject          {@code sub} imutável do provedor
     * @param email            e-mail informado pelo provedor (pode ser nulo)
     * @param emailVerificado  {@code true} se o provedor confirmou o e-mail
     * @param nomeCompleto     nome do usuário no provedor (pode ser nulo)
     * @return o usuário autenticável (ATIVO)
     * @throws RegraNegocioException se a conta vinculada não puder autenticar
     */
    @Transactional
    public Usuario resolverLogin(ProvedorOauth provedor, String subject, String email,
                                 boolean emailVerificado, String nomeCompleto) {
        if (!StringUtils.hasText(subject)) {
            throw new RegraNegocioException("Provedor não retornou um identificador (sub) válido.");
        }
        String emailNorm = normalizarEmail(email);

        // (1) Por identidade já vinculada — caminho mais comum após o 1º login.
        var existenteVinculo = identidadeRepository.findByProvedorAndSubjectId(provedor, subject);
        if (existenteVinculo.isPresent()) {
            Usuario usuario = existenteVinculo.get().getUsuario();
            garantirAutenticavel(usuario);
            return usuario;
        }

        // (2) Por e-mail VERIFICADO — adota a conta existente (sem duplicar).
        if (emailVerificado && emailNorm != null) {
            var porEmail = usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull(emailNorm);
            if (porEmail.isPresent()) {
                Usuario usuario = porEmail.get();
                garantirAutenticavel(usuario);
                vincular(usuario, provedor, subject, emailNorm);
                auditoriaService.registrarSeguranca("LOGIN_GOOGLE_VINCULO", "SUCESSO",
                        usuario.getId(), usuario.getNomeCompleto(), null);
                return usuario;
            }
        }

        // (3) Auto-provisão de PASSAGEIRO (perfil incompleto: falta telefone).
        Usuario novo = autoProvisionar(nomeCompleto, emailVerificado ? emailNorm : null);
        vincular(novo, provedor, subject, emailNorm);
        auditoriaService.registrarSeguranca("LOGIN_GOOGLE_AUTOPROVISAO", "SUCESSO",
                novo.getId(), novo.getNomeCompleto(), null);
        return novo;
    }

    // ===================== Helpers =====================

    /** Garante que a conta vinculada pode autenticar (ativa e não removida). */
    private void garantirAutenticavel(Usuario usuario) {
        if (!usuario.isAtivo()) {
            throw new RegraNegocioException(
                    "Sua conta não está ativa. Procure o gestor do sistema.");
        }
    }

    /** Cria o vínculo {@code identidades_oauth} para a conta. */
    private void vincular(Usuario usuario, ProvedorOauth provedor, String subject, String emailProvedor) {
        identidadeRepository.save(new IdentidadeOauth(usuario, provedor, subject, emailProvedor));
    }

    /**
     * Cria um PASSAGEIRO com perfil incompleto. O e-mail só é gravado quando
     * verificado (RN-AUT-11); o telefone fica nulo até {@code /conta/completar}.
     */
    private Usuario autoProvisionar(String nomeCompleto, String emailVerificado) {
        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(nomeOuPadrao(nomeCompleto, emailVerificado));
        usuario.setEmail(emailVerificado); // pode ser nulo
        usuario.setStatus(StatusUsuario.ATIVO);
        usuario.setPerfilIncompleto(true);
        usuario.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        return usuarioRepository.save(usuario);
    }

    private String nomeOuPadrao(String nome, String email) {
        if (StringUtils.hasText(nome)) {
            return nome.trim();
        }
        if (StringUtils.hasText(email)) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Usuário Google";
    }

    private String normalizarEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
