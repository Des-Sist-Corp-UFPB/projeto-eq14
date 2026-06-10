package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.enums.TipoVeiculo;
import br.ufpb.dsc.caladrius.dto.VeiculoForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link VeiculoService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VeiculoService — Testes Unitários")
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @InjectMocks
    private VeiculoService veiculoService;

    private VeiculoForm formValido() {
        return new VeiculoForm("abc1d23", "Fiat", "Ducato", 2022,
                TipoVeiculo.VAN, 15, true, null);
    }

    @Test
    @DisplayName("criar: normaliza a placa e salva quando não há duplicidade")
    void criar_semDuplicidade_salva() {
        when(veiculoRepository.existsByPlacaIgnoreCaseAndRemovidoEmIsNull("ABC1D23")).thenReturn(false);
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo salvo = veiculoService.criar(formValido());

        assertThat(salvo.getPlaca()).isEqualTo("ABC1D23"); // normalizada (maiúsculas)
        assertThat(salvo.getCapacidade()).isEqualTo(15);
        verify(veiculoRepository).save(any(Veiculo.class));
    }

    @Test
    @DisplayName("criar: rejeita placa já cadastrada")
    void criar_placaDuplicada_lancaExcecao() {
        when(veiculoRepository.existsByPlacaIgnoreCaseAndRemovidoEmIsNull("ABC1D23")).thenReturn(true);

        assertThatThrownBy(() -> veiculoService.criar(formValido()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ABC1D23");

        verify(veiculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("buscarPorId: lança exceção quando o veículo não existe")
    void buscarPorId_inexistente_lancaExcecao() {
        UUID id = UUID.randomUUID();
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> veiculoService.buscarPorId(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("excluir: aplica soft-delete (preenche removidoEm)")
    void excluir_softDelete() {
        UUID id = UUID.randomUUID();
        Veiculo veiculo = new Veiculo("ABC1D23", "Fiat", "Ducato", 2022, TipoVeiculo.VAN, 15, true);
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(id)).thenReturn(Optional.of(veiculo));
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        veiculoService.excluir(id);

        assertThat(veiculo.getRemovidoEm()).isNotNull();
        verify(veiculoRepository).save(veiculo);
    }
}
