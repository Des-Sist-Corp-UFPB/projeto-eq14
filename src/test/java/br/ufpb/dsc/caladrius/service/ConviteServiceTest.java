package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.TokenAtivacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
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
}
