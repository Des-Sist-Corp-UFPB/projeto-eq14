package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.dto.RegistroForm;
import br.ufpb.dsc.caladrius.dto.UsuarioForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link UsuarioService}, focados nas regras de
 * cadastro: normalização, validação de CPF, unicidade e papel padrão.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService — Testes Unitários")
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    @DisplayName("registrarPassageiro: normaliza telefone, faz hash da senha e concede papel PASSAGEIRO")
    void registrarPassageiro_valido() {
        RegistroForm form = new RegistroForm("João Silva", "(83) 99999-9999", null, null, "secret123",
                null, null, null, null, null, null, null);
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83999999999")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASH");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario salvo = usuarioService.registrarPassageiro(form);

        assertThat(salvo.getTelefone()).isEqualTo("83999999999");
        assertThat(salvo.getHashSenha()).isEqualTo("HASH");
        assertThat(salvo.getPapeis()).containsExactly(Papel.PASSAGEIRO);
    }

    @Test
    @DisplayName("registrarPassageiro: rejeita telefone já cadastrado")
    void registrarPassageiro_telefoneEmUso() {
        RegistroForm form = new RegistroForm("João Silva", "83999999999", null, null, "secret123",
                null, null, null, null, null, null, null);
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83999999999")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.registrarPassageiro(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Telefone");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarPassageiro: rejeita CPF inválido")
    void registrarPassageiro_cpfInvalido() {
        RegistroForm form = new RegistroForm("João Silva", "83999999999", "11111111111", null, "secret123",
                null, null, null, null, null, null, null);
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83999999999")).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.registrarPassageiro(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CPF");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: exige senha ao criar usuário pelo gerente")
    void criar_semSenha_lancaExcecao() {
        UsuarioForm form = new UsuarioForm("Maria", "83988887777", null, null, "", null, null);

        assertThatThrownBy(() -> usuarioService.criar(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("senha");

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ===================== DT-02: travas de RBAC / conta =====================

    /** Fixture: um gerente ativo (não removido). */
    private Usuario gerenteAtivo() {
        Usuario u = new Usuario();
        u.setNomeCompleto("Gestor");
        u.setTelefone("83999999999");
        u.setStatus(StatusUsuario.ATIVO);
        u.setPapeis(EnumSet.of(Papel.GERENTE));
        return u;
    }

    @Test
    @DisplayName("excluir: bloqueia a exclusão da própria conta (auto-deleção)")
    void excluir_propriaConta_bloqueia() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> usuarioService.excluir(id, id))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("própria conta");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("excluir: bloqueia a remoção do último gerente ativo")
    void excluir_ultimoGerente_bloqueia() {
        UUID id = UUID.randomUUID();
        UUID solicitante = UUID.randomUUID();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(gerenteAtivo()));
        when(usuarioRepository.contarPorStatusEPapel(StatusUsuario.ATIVO, Papel.GERENTE)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.excluir(id, solicitante))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último gerente");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("excluir: permite remover gerente quando há outro gerente ativo")
    void excluir_gerenteComOutroAtivo_permite() {
        UUID id = UUID.randomUUID();
        UUID solicitante = UUID.randomUUID();
        Usuario gerente = gerenteAtivo();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(gerente));
        when(usuarioRepository.contarPorStatusEPapel(StatusUsuario.ATIVO, Papel.GERENTE)).thenReturn(2L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.excluir(id, solicitante);

        assertThat(gerente.getRemovidoEm()).isNotNull();
        verify(usuarioRepository).save(gerente);
    }

    @Test
    @DisplayName("atualizar: bloqueia inativar o último gerente ativo")
    void atualizar_inativaUltimoGerente_bloqueia() {
        UUID id = UUID.randomUUID();
        Usuario gerente = gerenteAtivo();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(gerente));
        when(usuarioRepository.contarPorStatusEPapel(StatusUsuario.ATIVO, Papel.GERENTE)).thenReturn(1L);

        // Mesmo telefone do atual → validação de unicidade não dispara consulta.
        UsuarioForm form = new UsuarioForm("Gestor", "83999999999", null, null, "",
                EnumSet.of(Papel.GERENTE), StatusUsuario.INATIVO);

        assertThatThrownBy(() -> usuarioService.atualizar(id, form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último gerente");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitarSuspensao: bloqueia quando é o último gerente ativo")
    void solicitarSuspensao_ultimoGerente_bloqueia() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(gerenteAtivo()));
        when(usuarioRepository.contarPorStatusEPapel(StatusUsuario.ATIVO, Papel.GERENTE)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.solicitarSuspensao(id))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último gerente");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitarSuspensao: passageiro suspende a própria conta com sucesso")
    void solicitarSuspensao_passageiro_suspende() {
        UUID id = UUID.randomUUID();
        Usuario passageiro = new Usuario();
        passageiro.setStatus(StatusUsuario.ATIVO);
        passageiro.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(passageiro));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.solicitarSuspensao(id);

        assertThat(passageiro.getStatus()).isEqualTo(StatusUsuario.SUSPENSO);
        verify(usuarioRepository).save(passageiro);
    }
}
