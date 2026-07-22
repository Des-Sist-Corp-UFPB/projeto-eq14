package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeCodigo;
import br.ufpb.dsc.caladrius.domain.enums.MetodoVerificacao;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) de {@link RecuperacaoSenhaService} — "esqueci a senha"
 * (SPEC-12), com foco no seletor de método, na anti-enumeração e no reset.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecuperacaoSenhaService — Testes Unitários (esqueci a senha)")
class RecuperacaoSenhaServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VerificacaoService verificacaoService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private RecuperacaoSenhaService service;

    // ------------------------------------------------------------- solicitarReset

    @Test
    @DisplayName("solicitarReset (Telefone): usuário ativo recebe OTP pelo WhatsApp")
    void solicitarReset_telefone_enviaWhatsapp() {
        Usuario u = ativo();
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));

        service.solicitarReset(MetodoVerificacao.TELEFONE, "(83) 99999-8888", "1.1.1.1");

        verify(verificacaoService).enviarCodigo(u, FinalidadeCodigo.RESET_SENHA, CanalTipo.WHATSAPP, "1.1.1.1");
    }

    @Test
    @DisplayName("solicitarReset (E-mail): usuário ativo recebe OTP pelo e-mail")
    void solicitarReset_email_enviaEmail() {
        Usuario u = ativo();
        when(usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull("fulano@exemplo.com"))
                .thenReturn(Optional.of(u));

        service.solicitarReset(MetodoVerificacao.EMAIL, "Fulano@Exemplo.com", "1.1.1.1");

        verify(verificacaoService).enviarCodigo(u, FinalidadeCodigo.RESET_SENHA, CanalTipo.EMAIL, "1.1.1.1");
    }

    @Test
    @DisplayName("solicitarReset: identificador desconhecido NÃO emite código nem lança (anti-enumeração)")
    void solicitarReset_desconhecido_naoEmite() {
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83900000000")).thenReturn(Optional.empty());

        service.solicitarReset(MetodoVerificacao.TELEFONE, "83900000000", "1.1.1.1");

        verifyNoInteractions(verificacaoService);
    }

    @Test
    @DisplayName("solicitarReset: usuário inativo não recebe código")
    void solicitarReset_inativo_naoEmite() {
        Usuario u = ativo();
        u.setStatus(StatusUsuario.SUSPENSO);
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));

        service.solicitarReset(MetodoVerificacao.TELEFONE, "83999998888", "1.1.1.1");

        verify(verificacaoService, never()).enviarCodigo(any(), any(), any(), any());
    }

    // ------------------------------------------------------------- redefinir

    @Test
    @DisplayName("redefinir: valida o código e grava a nova senha (BCrypt)")
    void redefinir_sucesso() {
        Usuario u = ativo();
        u.setHashSenha("HASH_ANTIGO");
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("novaSenha1")).thenReturn("HASH_NOVO");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.redefinir(MetodoVerificacao.TELEFONE, "83999998888", "123456", "novaSenha1");

        verify(verificacaoService).validar(u.getId(), FinalidadeCodigo.RESET_SENHA, "123456");
        assertThat(u.getHashSenha()).isEqualTo("HASH_NOVO");
    }

    @Test
    @DisplayName("redefinir: conta ativa sem senha (bot) define a primeira senha — RN-REC-05")
    void redefinir_contaSemSenha_definePrimeira() {
        Usuario u = ativo();
        u.setHashSenha(null); // passageiro auto-cadastrado pelo WhatsApp
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("primeira9")).thenReturn("HASH_NOVO");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.redefinir(MetodoVerificacao.TELEFONE, "83999998888", "123456", "primeira9");

        assertThat(u.getHashSenha()).isEqualTo("HASH_NOVO");
    }

    @Test
    @DisplayName("redefinir: senha curta é rejeitada antes de validar o código")
    void redefinir_senhaCurta() {
        Usuario u = ativo();
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.redefinir(MetodoVerificacao.TELEFONE, "83999998888", "123456", "123"))
                .isInstanceOf(RegraNegocioException.class);

        verify(verificacaoService, never()).validar(any(), any(), anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("redefinir: identificador desconhecido lança (genérico) e não valida código")
    void redefinir_desconhecido() {
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83900000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir(MetodoVerificacao.TELEFONE, "83900000000", "123456", "novaSenha1"))
                .isInstanceOf(RegraNegocioException.class);

        verify(verificacaoService, never()).validar(any(), any(), anyString());
    }

    @Test
    @DisplayName("redefinir: código inválido propaga e não grava a senha")
    void redefinir_codigoInvalido() {
        Usuario u = ativo();
        when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999998888")).thenReturn(Optional.of(u));
        doThrow(new RegraNegocioException("Código inválido ou expirado."))
                .when(verificacaoService).validar(u.getId(), FinalidadeCodigo.RESET_SENHA, "000000");

        assertThatThrownBy(() -> service.redefinir(MetodoVerificacao.TELEFONE, "83999998888", "000000", "novaSenha1"))
                .isInstanceOf(RegraNegocioException.class);

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ------------------------------------------------------------- helper

    private Usuario ativo() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto("Fulano de Tal");
        u.setTelefone("83999998888");
        u.setEmail("fulano@exemplo.com");
        u.setStatus(StatusUsuario.ATIVO);
        u.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        return u;
    }
}
