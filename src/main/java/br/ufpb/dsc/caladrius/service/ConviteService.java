package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.TokenAtivacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.TokenAtivacaoRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Onboarding por convite (#20). Cria o usuário como {@code PENDENTE} (sem senha
 * utilizável) e gera um <strong>token de ativação</strong> de uso único; o
 * convidado define a senha pelo token e a conta vira {@code ATIVO}.
 *
 * <p>Mesma engine para SYSADMIN→GERENTE e GERENTE→MOTORISTA. A entrega usa o
 * {@link NotificacaoService} (e-mail/WhatsApp), mantendo os meios desacoplados.
 */
@Service
@Transactional(readOnly = true)
public class ConviteService {

    private static final int VALIDADE_DIAS = 7;
    private static final int SENHA_MIN = 6;

    private final UsuarioRepository usuarioRepository;
    private final TokenAtivacaoRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificacaoService notificacaoService;
    private final AuditoriaService auditoriaService;

    public ConviteService(UsuarioRepository usuarioRepository,
                          TokenAtivacaoRepository tokenRepository,
                          PasswordEncoder passwordEncoder,
                          NotificacaoService notificacaoService,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificacaoService = notificacaoService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Convida alguém para um papel: cria o usuário PENDENTE e dispara o token.
     *
     * @return o link de ativação (relativo) — útil para exibir a quem convidou
     */
    @Transactional
    public String convidar(String nome, String telefone, String email, Papel papel, UUID criadoPorId) {
        String tel = Documentos.apenasDigitos(telefone);
        if (!StringUtils.hasText(tel)) {
            throw new RegraNegocioException("O telefone é obrigatório");
        }
        if (usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(tel)) {
            throw new RegraNegocioException("Telefone já cadastrado: " + tel);
        }
        String em = StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
        if (em != null && usuarioRepository.existsByEmailIgnoreCaseAndRemovidoEmIsNull(em)) {
            throw new RegraNegocioException("E-mail já cadastrado: " + em);
        }

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(nome.trim());
        usuario.setTelefone(tel);
        usuario.setEmail(em);
        // Senha inutilizável até a ativação (hash de valor aleatório).
        usuario.setHashSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setStatus(StatusUsuario.PENDENTE);
        usuario.setPapeis(EnumSet.of(papel));
        usuario = usuarioRepository.save(usuario);

        String raw = gerarTokenCru();
        TokenAtivacao token = new TokenAtivacao();
        token.setTokenHash(hash(raw));
        token.setUsuarioId(usuario.getId());
        token.setCriadoPorId(criadoPorId);
        token.setExpiraEm(Instant.now().plus(VALIDADE_DIAS, ChronoUnit.DAYS));
        tokenRepository.save(token);

        String link = "/ativar?token=" + raw;
        notificacaoService.enviar(
                new NotificacaoDestino(usuario.getId(), em, tel),
                "Convite de acesso — CALADRIUS",
                "Você foi convidado(a) como " + papel.getRotulo()
                        + ". Defina sua senha em: " + link,
                CanalTipo.EMAIL, CanalTipo.WHATSAPP);

        if (criadoPorId != null) {
            notificacaoService.notificarInApp(criadoPorId, "Convite enviado",
                    "Convite de " + papel.getRotulo() + " para " + nome.trim() + ".");
        }
        auditoriaService.registrarOperacao("CONVITE_GERADO", "Usuario",
                usuario.getId().toString(), papel.name() + " — " + nome.trim());
        return link;
    }

    /** Redime o token: valida, define a senha e ativa a conta. */
    @Transactional
    public void ativar(String rawToken, String novaSenha) {
        if (!StringUtils.hasText(rawToken)) {
            throw new RegraNegocioException("Convite inválido.");
        }
        TokenAtivacao token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new RegraNegocioException("Convite inválido."));
        if (!token.valido()) {
            throw new RegraNegocioException("Convite expirado ou já utilizado.");
        }
        if (novaSenha == null || novaSenha.length() < SENHA_MIN) {
            throw new RegraNegocioException("A senha deve ter ao menos " + SENHA_MIN + " caracteres.");
        }

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", token.getUsuarioId()));
        usuario.setHashSenha(passwordEncoder.encode(novaSenha));
        usuario.setStatus(StatusUsuario.ATIVO);
        usuarioRepository.save(usuario);

        token.setUsadoEm(Instant.now());
        tokenRepository.save(token);

        notificacaoService.notificarInApp(usuario.getId(), "Conta ativada",
                "Sua conta foi ativada com sucesso. Bem-vindo(a) ao CALADRIUS!");
        auditoriaService.registrarSeguranca("CONTA_ATIVADA", "SUCESSO",
                usuario.getId(), usuario.getNomeCompleto(), AuditoriaService.ipDaRequisicao());
    }

    // ----------------------------------------------------------- helpers

    private String gerarTokenCru() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** SHA-256 (hex) do token cru — só o hash é persistido. */
    private String hash(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
