package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import br.ufpb.dsc.caladrius.domain.enums.MetodoVerificacao;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Recuperação de senha ("esqueci a senha") — SPEC-12, área {@code REC}.
 *
 * <p>A pessoa escolhe o <strong>método</strong> (E-mail ou Telefone); ele define
 * o campo de busca e o canal do OTP. A solicitação é <strong>anti-enumeração</strong>
 * (RN-REC-02): responde igual exista ou não a conta. A redefinição valida o código
 * (via {@link VerificacaoService}) e grava a nova senha em BCrypt.
 */
@Service
@Transactional(readOnly = true)
public class RecuperacaoSenhaService {

    static final int SENHA_MIN = 6;
    static final int SENHA_MAX = 72;

    private final UsuarioRepository usuarioRepository;
    private final VerificacaoService verificacaoService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository,
                                   VerificacaoService verificacaoService,
                                   PasswordEncoder passwordEncoder,
                                   AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.verificacaoService = verificacaoService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Emite um OTP de {@code RESET_SENHA} para o usuário do método/valor informado,
     * <strong>se</strong> ele existir e estiver ativo. Sempre retorna normalmente
     * (anti-enumeração — RN-REC-02); o chamador exibe uma mensagem genérica.
     */
    @Transactional
    public void solicitarReset(MetodoVerificacao metodo, String valor, String ip) {
        resolver(metodo, valor)
                .filter(Usuario::isAtivo)
                .ifPresent(u -> verificacaoService.enviarCodigo(
                        u, FinalidadeCodigo.RESET_SENHA, metodo.getCanal(), ip));
    }

    /**
     * Redefine a senha: valida o código {@code RESET_SENHA} e grava a nova senha
     * (BCrypt). Conta ativa <strong>sem senha</strong> (passageiro do bot — SPEC-11)
     * define aqui a primeira senha (RN-REC-05).
     */
    @Transactional
    public void redefinir(MetodoVerificacao metodo, String valor, String codigo, String novaSenha) {
        Usuario usuario = resolver(metodo, valor)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new RegraNegocioException(
                        "Não foi possível redefinir a senha. Confira os dados e o código."));

        if (novaSenha == null || novaSenha.length() < SENHA_MIN || novaSenha.length() > SENHA_MAX) {
            throw new RegraNegocioException(
                    "A senha deve ter entre " + SENHA_MIN + " e " + SENHA_MAX + " caracteres.");
        }

        verificacaoService.validar(usuario.getId(), FinalidadeCodigo.RESET_SENHA, codigo);

        usuario.setHashSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        auditoriaService.registrarSeguranca("SENHA_REDEFINIDA", "SUCESSO",
                usuario.getId(), usuario.getNomeCompleto(), AuditoriaService.ipDaRequisicao());
    }

    /** Resolve o usuário pelo campo do método escolhido (não usa a heurística "@"). */
    private Optional<Usuario> resolver(MetodoVerificacao metodo, String valor) {
        if (metodo == null || !StringUtils.hasText(valor)) {
            return Optional.empty();
        }
        return switch (metodo) {
            case EMAIL -> usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull(valor.trim().toLowerCase());
            case TELEFONE -> usuarioRepository.findByTelefoneAndRemovidoEmIsNull(Documentos.apenasDigitos(valor));
        };
    }
}
