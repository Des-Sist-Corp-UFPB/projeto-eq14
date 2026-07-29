package br.ufpb.dsc.caladrius.security;

import br.ufpb.dsc.caladrius.config.AuditoriaSecurityListener;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.service.AuditoriaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários da camada de segurança: a resolução do login flexível
 * (e-mail <em>ou</em> telefone — SPEC-01), o principal {@link UsuarioAutenticado}
 * (papéis → authorities, conta habilitada) e o listener que audita acessos (#19).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Segurança — login flexível, principal e auditoria de acesso")
class SegurancaUnitTest {

    private static Usuario usuario(String telefone, String email, StatusUsuario status, Papel... papeis) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto("Fulana de Tal");
        u.setTelefone(telefone);
        u.setEmail(email);
        u.setHashSenha("$2a$10$hash");
        u.setStatus(status);
        u.setPapeis(EnumSet.copyOf(java.util.List.of(papeis)));
        return u;
    }

    // ------------------------------------------------- CaladriusUserDetailsService

    @Nested
    @DisplayName("CaladriusUserDetailsService (SPEC-01)")
    class CarregarUsuario {

        @Mock private UsuarioRepository usuarioRepository;
        @InjectMocks private CaladriusUserDetailsService service;

        @Test
        @DisplayName("identificador com '@' é tratado como e-mail")
        void porEmail() {
            when(usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull("ana@exemplo.test"))
                    .thenReturn(Optional.of(usuario("83999990000", "ana@exemplo.test",
                            StatusUsuario.ATIVO, Papel.GERENTE)));

            UserDetails detalhes = service.loadUserByUsername("  ana@exemplo.test  ");

            // Encontrado pelo e-mail; o username canônico do principal é o telefone.
            assertThat(detalhes.getUsername()).isEqualTo("83999990000");
            assertThat(detalhes.getAuthorities())
                    .extracting("authority").containsExactly("ROLE_GERENTE");
        }

        @Test
        @DisplayName("identificador sem '@' vira telefone, normalizado para dígitos")
        void porTelefone() {
            when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull("83999990000"))
                    .thenReturn(Optional.of(usuario("83999990000", null,
                            StatusUsuario.ATIVO, Papel.PASSAGEIRO)));

            UserDetails detalhes = service.loadUserByUsername("(83) 99999-0000");

            assertThat(detalhes.getUsername()).isEqualTo("83999990000");
        }

        @Test
        @DisplayName("sem correspondência, a mensagem é genérica (não revela o que falhou)")
        void naoEncontrado() {
            when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByUsername("83900000000"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("Credenciais inválidas");
        }

        @Test
        @DisplayName("identificador nulo não quebra — vira busca vazia e credencial inválida")
        void identificadorNulo() {
            when(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByUsername(null))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    // -------------------------------------------------------- UsuarioAutenticado

    @Nested
    @DisplayName("UsuarioAutenticado (principal único)")
    class Principal {

        @Test
        @DisplayName("papéis viram authorities ROLE_*; só conta ATIVA está habilitada")
        void papeisEStatus() {
            UsuarioAutenticado ativo = new UsuarioAutenticado(
                    usuario("83999990001", null, StatusUsuario.ATIVO, Papel.GERENTE, Papel.MOTORISTA));
            assertThat(ativo.getAuthorities()).extracting("authority")
                    .containsExactlyInAnyOrder("ROLE_GERENTE", "ROLE_MOTORISTA");
            assertThat(ativo.isEnabled()).isTrue();
            assertThat(ativo.isAccountNonExpired()).isTrue();
            assertThat(ativo.isAccountNonLocked()).isTrue();
            assertThat(ativo.isCredentialsNonExpired()).isTrue();

            UsuarioAutenticado suspenso = new UsuarioAutenticado(
                    usuario("83999990002", null, StatusUsuario.SUSPENSO, Papel.PASSAGEIRO));
            assertThat(suspenso.isEnabled()).isFalse();

            UsuarioAutenticado pendente = new UsuarioAutenticado(
                    usuario("83999990003", null, StatusUsuario.PENDENTE, Papel.PASSAGEIRO));
            assertThat(pendente.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("username é o telefone; sem telefone (login social) recai no e-mail e, por fim, no id")
        void username() {
            assertThat(new UsuarioAutenticado(
                    usuario("83999990004", "b@exemplo.test", StatusUsuario.ATIVO, Papel.PASSAGEIRO))
                    .getUsername()).isEqualTo("83999990004");

            Usuario semTelefone = usuario(null, "c@exemplo.test", StatusUsuario.ATIVO, Papel.PASSAGEIRO);
            assertThat(new UsuarioAutenticado(semTelefone).getUsername()).isEqualTo("c@exemplo.test");

            Usuario semContato = usuario(null, null, StatusUsuario.ATIVO, Papel.PASSAGEIRO);
            assertThat(new UsuarioAutenticado(semContato).getUsername())
                    .isEqualTo(semContato.getId().toString());
        }

        @Test
        @DisplayName("expõe os dados de exibição sem vazar o hash em getName()")
        void dadosDeExibicao() {
            Usuario u = usuario("83999990006", "c@exemplo.test", StatusUsuario.ATIVO, Papel.PASSAGEIRO);
            UsuarioAutenticado principal = new UsuarioAutenticado(u);

            assertThat(principal.getId()).isEqualTo(u.getId());
            assertThat(principal.getNomeCompleto()).isEqualTo("Fulana de Tal");
            assertThat(principal.getTelefone()).isEqualTo("83999990006");
            assertThat(principal.getEmail()).isEqualTo("c@exemplo.test");
            assertThat(principal.isPerfilIncompleto()).isFalse();
            assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
            assertThat(principal.getName()).doesNotContain("$2a$10$hash");
        }
    }

    // --------------------------------------------------- AuditoriaSecurityListener

    @Nested
    @DisplayName("AuditoriaSecurityListener (#19)")
    class AuditoriaDeAcesso {

        @Mock private AuditoriaService auditoriaService;
        @InjectMocks private AuditoriaSecurityListener listener;

        private InteractiveAuthenticationSuccessEvent sucessoDe(Object principal) {
            return new InteractiveAuthenticationSuccessEvent(
                    new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()),
                    getClass());
        }

        @Test
        @DisplayName("login bem-sucedido registra LOGIN_SUCESSO com o id e o nome do usuário")
        void loginSucesso() {
            Usuario u = usuario("83999990007", null, StatusUsuario.ATIVO, Papel.GERENTE);

            listener.onLoginSucesso(sucessoDe(new UsuarioAutenticado(u)));

            verify(auditoriaService).registrarSeguranca(eq("LOGIN_SUCESSO"), eq("SUCESSO"),
                    eq(u.getId()), eq("Fulana de Tal"), any());
        }

        @Test
        @DisplayName("falha de login registra LOGIN_FALHA sem id, guardando o identificador tentado")
        void loginFalha() {
            listener.onLoginFalha(new AuthenticationFailureBadCredentialsEvent(
                    new UsernamePasswordAuthenticationToken("83999990008", "errada"),
                    new BadCredentialsException("credenciais inválidas")));

            verify(auditoriaService).registrarSeguranca(eq("LOGIN_FALHA"), eq("FALHA"),
                    isNull(), eq("83999990008"), any());
        }

        @Test
        @DisplayName("falha ao auditar NÃO impede a autenticação (o erro é só logado)")
        void auditoriaQuebradaNaoDerrubaLogin() {
            doThrow(new IllegalStateException("banco fora"))
                    .when(auditoriaService).registrarSeguranca(anyString(), anyString(), any(), any(), any());

            Usuario u = usuario("83999990009", null, StatusUsuario.ATIVO, Papel.PASSAGEIRO);
            listener.onLoginSucesso(sucessoDe(new UsuarioAutenticado(u)));
            listener.onLoginFalha(new AuthenticationFailureBadCredentialsEvent(
                    new UsernamePasswordAuthenticationToken("x", "y"),
                    new BadCredentialsException("erro")));
            // Nenhuma exceção escapou.
        }
    }
}
