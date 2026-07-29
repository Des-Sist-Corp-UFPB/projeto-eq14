package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.CodigoVerificacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.CodigoVerificacaoRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Engine de códigos OTP de verificação (SPEC-12): gera, envia, valida (com
 * expiração, uso único e <em>lockout</em> de tentativas) e confirma o telefone.
 *
 * <p>Guarda apenas o <strong>hash</strong> do código (nunca os 6 dígitos), como
 * o {@code TokenAtivacao} (Art. XI). Falha de validação sempre devolve mensagem
 * <strong>genérica</strong> (RN-VER-04). A entrega usa o {@link NotificacaoService},
 * mantendo o canal desacoplado.
 */
@Service
@Transactional(readOnly = true)
public class VerificacaoService {

    static final int TAMANHO_CODIGO = 6;

    private static final String ERRO_GENERICO = "Código inválido ou expirado.";

    private final CodigoVerificacaoRepository codigoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final AuditoriaService auditoriaService;
    private final FeatureFlagService featureFlags;
    private final SecureRandom random = new SecureRandom();

    public VerificacaoService(CodigoVerificacaoRepository codigoRepository,
                              UsuarioRepository usuarioRepository,
                              NotificacaoService notificacaoService,
                              AuditoriaService auditoriaService,
                              FeatureFlagService featureFlags) {
        this.codigoRepository = codigoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
        this.auditoriaService = auditoriaService;
        this.featureFlags = featureFlags;
    }

    // Parâmetros de negócio configuráveis em runtime (SPEC-13, FR-FLG-06). Os valores
    // de fábrica (10 min / 5 tentativas / 60 s) continuam sendo os defaults do código:
    // sem configuração no banco, o comportamento é exatamente o de antes (RN-FLG-02).

    private int validadeMinutos() {
        return featureFlags.parametro(ParametroSistema.OTP_VALIDADE_MINUTOS);
    }

    private int maxTentativas() {
        return featureFlags.parametro(ParametroSistema.OTP_MAX_TENTATIVAS);
    }

    private int cooldownSegundos() {
        return featureFlags.parametro(ParametroSistema.OTP_COOLDOWN_SEGUNDOS);
    }

    /**
     * Gera um código OTP, invalida os anteriores da mesma finalidade (RN-VER-09)
     * e o envia pelo canal indicado. Só o hash é persistido; o valor cru vai na
     * mensagem entregue à pessoa.
     */
    @Transactional
    public void enviarCodigo(Usuario usuario, FinalidadeCodigo finalidade, CanalTipo canal, String ip) {
        // Cooldown de reenvio (RN-VER-06): evita disparo em rajada.
        int cooldownSegundos = cooldownSegundos();
        codigoRepository.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), finalidade)
                .filter(c -> c.getCriadoEm() != null
                        && c.getCriadoEm().isAfter(Instant.now().minusSeconds(cooldownSegundos)))
                .ifPresent(c -> {
                    throw new RegraNegocioException("Aguarde alguns segundos para pedir um novo código.");
                });

        // Invalida códigos ativos anteriores da mesma finalidade (um código vigente por vez).
        List<CodigoVerificacao> ativos =
                codigoRepository.findByUsuarioIdAndFinalidadeAndUsadoEmIsNull(usuario.getId(), finalidade);
        if (!ativos.isEmpty()) {
            Instant agora = Instant.now();
            ativos.forEach(c -> c.setUsadoEm(agora));
            codigoRepository.saveAll(ativos);
        }

        int validadeMinutos = validadeMinutos();
        String codigoCru = gerarCodigo();
        CodigoVerificacao codigo = new CodigoVerificacao();
        codigo.setUsuarioId(usuario.getId());
        codigo.setCodigoHash(hash(codigoCru));
        codigo.setFinalidade(finalidade);
        codigo.setCanal(canal);
        codigo.setTentativas(0);
        codigo.setExpiraEm(Instant.now().plus(validadeMinutos, ChronoUnit.MINUTES));
        codigo.setCriadoIp(ip);
        codigoRepository.save(codigo);

        String titulo = "Código de verificação — CALADRIUS";
        String mensagem = "Seu código para " + finalidade.getRotulo() + " é: " + codigoCru
                + "\nEle expira em " + validadeMinutos + " minutos. Não compartilhe este código.";
        notificacaoService.enviar(
                new NotificacaoDestino(usuario.getId(), usuario.getEmail(), usuario.getTelefone()),
                titulo, mensagem, canal);

        auditoriaService.registrarSeguranca("CODIGO_ENVIADO", "SUCESSO",
                usuario.getId(), usuario.getNomeCompleto(), ip);
    }

    /**
     * Valida o código informado para a finalidade. Em erro, incrementa as
     * tentativas e faz o <em>lockout</em> ao atingir o teto (RN-VER-05),
     * lançando sempre uma mensagem genérica. Em sucesso, marca o código usado.
     */
    @Transactional
    public void validar(UUID usuarioId, FinalidadeCodigo finalidade, String codigoInformado) {
        CodigoVerificacao codigo = codigoRepository
                .findFirstByUsuarioIdAndFinalidadeAndUsadoEmIsNullOrderByCriadoEmDesc(usuarioId, finalidade)
                .orElseThrow(() -> new RegraNegocioException(ERRO_GENERICO));

        int maxTentativas = maxTentativas();
        // Expirado ou já bloqueado: não conta como nova tentativa.
        if (!codigo.valido(maxTentativas)) {
            throw new RegraNegocioException(ERRO_GENERICO);
        }

        boolean confere = codigoInformado != null
                && MessageDigest.isEqual(
                        hash(codigoInformado).getBytes(StandardCharsets.UTF_8),
                        codigo.getCodigoHash().getBytes(StandardCharsets.UTF_8));

        if (!confere) {
            codigo.setTentativas(codigo.getTentativas() + 1);
            if (codigo.getTentativas() >= maxTentativas) {
                codigo.setUsadoEm(Instant.now()); // lockout: invalida o código
            }
            codigoRepository.save(codigo);
            throw new RegraNegocioException(ERRO_GENERICO);
        }

        codigo.setUsadoEm(Instant.now());
        codigoRepository.save(codigo);
    }

    /**
     * Confirma o telefone: valida o OTP {@code VERIFICAR_TELEFONE}, grava
     * {@code telefone_verificado_em} e, se o cadastro estava PENDENTE, promove
     * para ATIVO (RN-VER-02).
     */
    @Transactional
    public void confirmarTelefone(UUID usuarioId, String codigoInformado) {
        validar(usuarioId, FinalidadeCodigo.VERIFICAR_TELEFONE, codigoInformado);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        usuario.setTelefoneVerificadoEm(Instant.now());
        if (usuario.getStatus() == StatusUsuario.PENDENTE) {
            usuario.setStatus(StatusUsuario.ATIVO);
        }
        usuarioRepository.save(usuario);

        auditoriaService.registrarSeguranca("TELEFONE_VERIFICADO", "SUCESSO",
                usuario.getId(), usuario.getNomeCompleto(), AuditoriaService.ipDaRequisicao());
    }

    // -------------------------------------------------- fluxos públicos (por telefone)

    /** Atalho: dispara um OTP de verificação de telefone pelo WhatsApp. */
    @Transactional
    public void enviarVerificacaoTelefone(Usuario usuario, String ip) {
        enviarCodigo(usuario, FinalidadeCodigo.VERIFICAR_TELEFONE, CanalTipo.WHATSAPP, ip);
    }

    /** Reenvia o OTP de telefone resolvendo o usuário pelo próprio telefone (tela pública). */
    @Transactional
    public void reenviarPorTelefone(String telefoneRaw, String ip) {
        enviarVerificacaoTelefone(resolverPorTelefone(telefoneRaw), ip);
    }

    /** Confirma o telefone resolvendo o usuário pelo próprio telefone (tela pública). */
    @Transactional
    public void confirmarTelefonePorTelefone(String telefoneRaw, String codigo) {
        confirmarTelefone(resolverPorTelefone(telefoneRaw).getId(), codigo);
    }

    private Usuario resolverPorTelefone(String telefoneRaw) {
        return usuarioRepository.findByTelefoneAndRemovidoEmIsNull(Documentos.apenasDigitos(telefoneRaw))
                .orElseThrow(() -> new RegraNegocioException("Não encontramos um cadastro com esse telefone."));
    }

    // ----------------------------------------------------------- helpers

    /** Código numérico de 6 dígitos (com zeros à esquerda), via {@link SecureRandom}. */
    private String gerarCodigo() {
        int n = random.nextInt(1_000_000); // 0..999999
        return String.format("%0" + TAMANHO_CODIGO + "d", n);
    }

    /** SHA-256 (hex) — só o hash é persistido (mesma política do TokenAtivacao). */
    private String hash(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
