package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
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

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    @DisplayName("registrarPassageiro: normaliza telefone, faz hash da senha e concede papel PASSAGEIRO")
    void registrarPassageiro_valido() {
        RegistroForm form = new RegistroForm("João Silva", "(83) 99999-9999", null, null, "secret123");
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
        RegistroForm form = new RegistroForm("João Silva", "83999999999", null, null, "secret123");
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull("83999999999")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.registrarPassageiro(form))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Telefone");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarPassageiro: rejeita CPF inválido")
    void registrarPassageiro_cpfInvalido() {
        RegistroForm form = new RegistroForm("João Silva", "83999999999", "11111111111", null, "secret123");
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
}
