package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.IdentidadeOauth;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.ProvedorOauth;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.IdentidadeOauthRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link IdentidadeOauthService} — núcleo da resolução
 * do login social Google (SPEC-08): a sequência identidade → e-mail verificado →
 * auto-provisão. Estas são as regras de negócio mais sensíveis da feature.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdentidadeOauthService — Testes Unitários (SPEC-08)")
class IdentidadeOauthServiceTest {

    @Mock private IdentidadeOauthRepository identidadeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private IdentidadeOauthService service;

    private Usuario ativo() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNomeCompleto("Maria Passageira");
        u.setStatus(StatusUsuario.ATIVO);
        return u;
    }

    /** Passo 1: já existindo o vínculo (provedor, sub), apenas retorna a conta — não cria nada. */
    @Test
    @DisplayName("resolverLogin: identidade já vinculada retorna a conta sem criar vínculo/usuário")
    void resolverLogin_identidadeExistente_retornaConta() {
        Usuario usuario = ativo();
        IdentidadeOauth vinculo = new IdentidadeOauth(usuario, ProvedorOauth.GOOGLE, "sub-1", "m@gmail.com");
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(vinculo));

        Usuario resultado = service.resolverLogin(ProvedorOauth.GOOGLE, "sub-1", "m@gmail.com", true, "Maria");

        assertThat(resultado).isSameAs(usuario);
        verify(identidadeRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    /** A conta vinculada precisa estar autenticável: suspensa/removida é barrada. */
    @Test
    @DisplayName("resolverLogin: identidade vinculada a conta inativa é bloqueada")
    void resolverLogin_contaInativa_bloqueia() {
        Usuario suspenso = ativo();
        suspenso.setStatus(StatusUsuario.SUSPENSO);
        IdentidadeOauth vinculo = new IdentidadeOauth(suspenso, ProvedorOauth.GOOGLE, "sub-2", null);
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-2"))
                .thenReturn(Optional.of(vinculo));

        assertThatThrownBy(() ->
                service.resolverLogin(ProvedorOauth.GOOGLE, "sub-2", "x@gmail.com", true, "X"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está ativa");
    }

    /** Passo 2: sem vínculo, mas há conta ativa com o mesmo e-mail verificado → adota a conta. */
    @Test
    @DisplayName("resolverLogin: e-mail verificado adota conta existente e cria o vínculo")
    void resolverLogin_emailVerificado_adotaConta() {
        Usuario existente = ativo();
        existente.setEmail("maria@gmail.com");
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-3"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull("maria@gmail.com"))
                .thenReturn(Optional.of(existente));

        Usuario resultado = service.resolverLogin(ProvedorOauth.GOOGLE, "sub-3", "Maria@Gmail.com", true, "Maria");

        assertThat(resultado).isSameAs(existente);
        // vínculo criado para a conta existente; nenhum usuário novo é persistido
        verify(identidadeRepository).save(any(IdentidadeOauth.class));
        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService).registrarSeguranca(eq("LOGIN_GOOGLE_VINCULO"), eq("SUCESSO"),
                eq(existente.getId()), any(), any());
    }

    /** A adoção por e-mail só vale para e-mail VERIFICADO; caso contrário cai na auto-provisão. */
    @Test
    @DisplayName("resolverLogin: e-mail NÃO verificado não adota conta; auto-provisiona sem gravar e-mail")
    void resolverLogin_emailNaoVerificado_autoProvisionaSemEmail() {
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-4"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        service.resolverLogin(ProvedorOauth.GOOGLE, "sub-4", "naoverificado@gmail.com", false, "Sem Verif");

        // não consulta por e-mail (não verificado) e cria a conta sem e-mail
        verify(usuarioRepository, never()).findByEmailIgnoreCaseAndRemovidoEmIsNull(any());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    /** Passo 3: sem vínculo e sem conta → cria PASSAGEIRO com perfil incompleto e grava o e-mail verificado. */
    @Test
    @DisplayName("resolverLogin: sem conta auto-provisiona PASSAGEIRO com perfil incompleto")
    void resolverLogin_semConta_autoProvisionaPassageiro() {
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-5"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull("novo@gmail.com"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        Usuario novo = service.resolverLogin(ProvedorOauth.GOOGLE, "sub-5", "novo@gmail.com", true, "Novo Usuário");

        assertThat(novo.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(novo.isPerfilIncompleto()).isTrue();
        assertThat(novo.getTelefone()).isNull();
        assertThat(novo.getEmail()).isEqualTo("novo@gmail.com");
        assertThat(novo.getPapeis()).containsExactly(Papel.PASSAGEIRO);
        verify(identidadeRepository).save(any(IdentidadeOauth.class));
        verify(auditoriaService).registrarSeguranca(eq("LOGIN_GOOGLE_AUTOPROVISAO"), eq("SUCESSO"),
                any(), any(), any());
    }

    /** Sem nome no provedor, usa a parte local do e-mail como nome inicial. */
    @Test
    @DisplayName("resolverLogin: sem nome do provedor usa a parte local do e-mail")
    void resolverLogin_semNome_usaLocalPartDoEmail() {
        when(identidadeRepository.findByProvedorAndSubjectId(ProvedorOauth.GOOGLE, "sub-6"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario novo = service.resolverLogin(ProvedorOauth.GOOGLE, "sub-6", "joao.silva@gmail.com", true, "  ");

        assertThat(novo.getNomeCompleto()).isEqualTo("joao.silva");
    }

    /** Defesa: o provedor precisa devolver um 'sub' não-vazio. */
    @Test
    @DisplayName("resolverLogin: subject vazio é rejeitado")
    void resolverLogin_subjectVazio_rejeita() {
        assertThatThrownBy(() ->
                service.resolverLogin(ProvedorOauth.GOOGLE, "  ", "x@gmail.com", true, "X"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("sub");
        verifyNoInteractions(usuarioRepository);
    }
}
