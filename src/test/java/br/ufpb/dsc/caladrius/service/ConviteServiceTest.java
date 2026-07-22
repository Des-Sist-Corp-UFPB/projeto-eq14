package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.TokenAtivacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.FinalidadeToken;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.repository.TokenAtivacaoRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link ConviteService} — onboarding por token (#20).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConviteService — Testes Unitários")
class ConviteServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TokenAtivacaoRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificacaoService notificacaoService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private ConviteService conviteService;

    @Test
    @DisplayName("convidar: cria usuário PENDENTE com o papel e gera token; retorna link")
    void convidar_criaPendenteEToken() {
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83988887777")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });

        String link = conviteService.convidar("João Motorista", "(83) 98888-7777", null,
                Papel.MOTORISTA, UUID.randomUUID());

        assertThat(link).startsWith("/ativar?token=");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(captor.getValue().getPapeis()).containsExactly(Papel.MOTORISTA);
        verify(tokenRepository).save(any(TokenAtivacao.class));
    }

    @Test
    @DisplayName("convidar: rejeita telefone já cadastrado")
    void convidar_telefoneDuplicado() {
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83988887777")).thenReturn(true);

        assertThatThrownBy(() -> conviteService.convidar("João", "83988887777", null,
                Papel.MOTORISTA, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Telefone");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("ativar: token válido define a senha, ativa a conta e marca o token usado")
    void ativar_valido_ativaConta() {
        UUID usuarioId = UUID.randomUUID();
        TokenAtivacao token = new TokenAtivacao();
        token.setUsuarioId(usuarioId);
        token.setExpiraEm(Instant.now().plusSeconds(3600));

        Usuario pendente = new Usuario();
        pendente.setStatus(StatusUsuario.PENDENTE);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(pendente));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("HASH");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        conviteService.ativar("um-token-cru", "novaSenha123");

        assertThat(pendente.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(pendente.getHashSenha()).isEqualTo("HASH");
        assertThat(token.getUsadoEm()).isNotNull();
    }

    @Test
    @DisplayName("ativar: token inexistente é rejeitado")
    void ativar_tokenInvalido() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conviteService.ativar("xxx", "novaSenha123"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    @DisplayName("ativar: token expirado é rejeitado")
    void ativar_tokenExpirado() {
        TokenAtivacao token = new TokenAtivacao();
        token.setUsuarioId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> conviteService.ativar("xxx", "novaSenha123"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("ativar: senha curta é rejeitada")
    void ativar_senhaCurta() {
        TokenAtivacao token = new TokenAtivacao();
        token.setUsuarioId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> conviteService.ativar("xxx", "123"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("6 caracteres");

        verify(usuarioRepository, never()).save(any());
    }

    // ---------------------------------------- verificação de e-mail (SPEC-12)

    @Test
    @DisplayName("enviarVerificacaoEmail: cria token VERIFICAR_EMAIL e envia o link por e-mail")
    void enviarVerificacaoEmail_criaTokenEEnviaLink() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto("Fulano");
        u.setEmail("a@b.com");
        when(tokenRepository.save(any(TokenAtivacao.class))).thenAnswer(inv -> inv.getArgument(0));

        conviteService.enviarVerificacaoEmail(u);

        ArgumentCaptor<TokenAtivacao> cap = ArgumentCaptor.forClass(TokenAtivacao.class);
        verify(tokenRepository).save(cap.capture());
        assertThat(cap.getValue().getFinalidade()).isEqualTo(FinalidadeToken.VERIFICAR_EMAIL);
        verify(notificacaoService).enviar(any(NotificacaoDestino.class), anyString(),
                contains("/verificar-email?token="), eq(CanalTipo.EMAIL));
    }

    @Test
    @DisplayName("enviarVerificacaoEmail: sem e-mail é no-op")
    void enviarVerificacaoEmail_semEmail_noop() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());

        conviteService.enviarVerificacaoEmail(u);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("verificarEmail: token válido marca email_verificado_em e consome o token")
    void verificarEmail_valido() {
        UUID uid = UUID.randomUUID();
        TokenAtivacao token = new TokenAtivacao();
        token.setUsuarioId(uid);
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        token.setFinalidade(FinalidadeToken.VERIFICAR_EMAIL);
        Usuario u = new Usuario();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(usuarioRepository.findById(uid)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(TokenAtivacao.class))).thenAnswer(inv -> inv.getArgument(0));

        conviteService.verificarEmail("raw-token");

        assertThat(u.getEmailVerificadoEm()).isNotNull();
        assertThat(token.getUsadoEm()).isNotNull();
    }

    @Test
    @DisplayName("verificarEmail: token de finalidade errada (ATIVACAO) é rejeitado")
    void verificarEmail_finalidadeErrada() {
        TokenAtivacao token = new TokenAtivacao(); // finalidade ATIVACAO (padrão)
        token.setUsuarioId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> conviteService.verificarEmail("raw"))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("ativar: token de verificação de e-mail não pode definir senha")
    void ativar_tokenVerificacaoEmail_rejeita() {
        TokenAtivacao token = new TokenAtivacao();
        token.setUsuarioId(UUID.randomUUID());
        token.setExpiraEm(Instant.now().plusSeconds(3600));
        token.setFinalidade(FinalidadeToken.VERIFICAR_EMAIL);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> conviteService.ativar("raw", "novaSenha123"))
                .isInstanceOf(RegraNegocioException.class);
    }
}
