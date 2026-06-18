package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Endereco;
import br.ufpb.dsc.caladrius.dto.EnderecoForm;
import br.ufpb.dsc.caladrius.repository.EnderecoRepository;
import br.ufpb.dsc.caladrius.repository.MunicipioRepository;
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
 * Testes unitários (Mockito) de {@link EnderecoService} — SPEC-07.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnderecoService — Testes Unitários")
class EnderecoServiceTest {

    @Mock private EnderecoRepository enderecoRepository;
    @Mock private MunicipioRepository municipioRepository;

    @InjectMocks private EnderecoService enderecoService;

    @Test
    @DisplayName("salvar: cria endereço quando há dados e normaliza o CEP")
    void salvar_novo_normalizaCep() {
        UUID uid = UUID.randomUUID();
        EnderecoForm form = new EnderecoForm(null, "Jatobá", "Rua A", "10", null, null, "58700000");
        when(enderecoRepository.findByUsuarioId(uid)).thenReturn(Optional.empty());
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(inv -> inv.getArgument(0));

        enderecoService.salvar(uid, form);

        ArgumentCaptor<Endereco> cap = ArgumentCaptor.forClass(Endereco.class);
        verify(enderecoRepository).save(cap.capture());
        assertThat(cap.getValue().getUsuarioId()).isEqualTo(uid);
        assertThat(cap.getValue().getBairro()).isEqualTo("Jatobá");
        assertThat(cap.getValue().getCep()).isEqualTo("58700-000");
    }

    @Test
    @DisplayName("salvar: formulário vazio sem endereço existente não persiste nada")
    void salvar_vazio_naoPersiste() {
        UUID uid = UUID.randomUUID();
        EnderecoForm vazio = new EnderecoForm(null, null, null, null, null, null, null);
        when(enderecoRepository.findByUsuarioId(uid)).thenReturn(Optional.empty());

        enderecoService.salvar(uid, vazio);

        verify(enderecoRepository, never()).save(any());
    }

    @Test
    @DisplayName("salvar: atualiza o mesmo endereço quando já existe (não cria outro)")
    void salvar_atualizaExistente() {
        UUID uid = UUID.randomUUID();
        Endereco existente = new Endereco();
        existente.setUsuarioId(uid);
        existente.setBairro("Antigo");
        when(enderecoRepository.findByUsuarioId(uid)).thenReturn(Optional.of(existente));
        when(enderecoRepository.save(any(Endereco.class))).thenAnswer(inv -> inv.getArgument(0));

        enderecoService.salvar(uid, new EnderecoForm(null, "Novo", null, null, null, null, null));

        assertThat(existente.getBairro()).isEqualTo("Novo");
        verify(enderecoRepository).save(existente);
    }

    @Test
    @DisplayName("enderecoCadastrado: delega ao repositório (regra 'sem endereço, sem viagem')")
    void enderecoCadastrado_delega() {
        UUID uid = UUID.randomUUID();
        when(enderecoRepository.existsByUsuarioId(uid)).thenReturn(true);

        assertThat(enderecoService.enderecoCadastrado(uid)).isTrue();
    }
}
