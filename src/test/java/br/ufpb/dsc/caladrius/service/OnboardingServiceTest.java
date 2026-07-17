package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.dto.EnderecoForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários (Mockito) do {@link OnboardingService} — SPEC-11: cria o
 * passageiro ATIVO sem senha a partir dos dados mínimos, valida CPF e unicidade,
 * e registra o endereço de embarque.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingService — Testes Unitários (SPEC-11)")
class OnboardingServiceTest {

    private static final String TELEFONE = "83911112222";
    private static final String CPF_VALIDO = "529.982.247-25";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EnderecoService enderecoService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private OnboardingService service;

    @Test
    @DisplayName("cadastra PASSAGEIRO ATIVO sem senha e salva o endereço (RN-ONB-02)")
    void cadastraPassageiro() {
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(TELEFONE)).thenReturn(false);
        when(usuarioRepository.existsByCpfAndRemovidoEmIsNull("52998224725")).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        Usuario novo = service.cadastrarPassageiro(TELEFONE, "Maria da Silva", "Rua A, 123", CPF_VALIDO);

        assertThat(novo.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(novo.getHashSenha()).isNull();
        assertThat(novo.getPapeis()).containsExactly(Papel.PASSAGEIRO);
        assertThat(novo.getTelefone()).isEqualTo(TELEFONE);
        assertThat(novo.getCpf()).isEqualTo("52998224725");

        ArgumentCaptor<EnderecoForm> endereco = ArgumentCaptor.forClass(EnderecoForm.class);
        verify(enderecoService).salvar(eq(novo.getId()), endereco.capture());
        assertThat(endereco.getValue().logradouro()).isEqualTo("Rua A, 123");
    }

    @Test
    @DisplayName("CPF inválido é rejeitado (não cria conta)")
    void cpfInvalido() {
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(TELEFONE)).thenReturn(false);

        assertThatThrownBy(() -> service.cadastrarPassageiro(TELEFONE, "Maria", "Rua A", "123"))
                .isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("telefone já cadastrado é rejeitado")
    void telefoneDuplicado() {
        when(usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(TELEFONE)).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrarPassageiro(TELEFONE, "Maria", "Rua A", CPF_VALIDO))
                .isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }
}
