package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
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
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link CidadeService}, focados no DT-01:
 * exclusão de cidade remove em cascata as viagens com aquele destino.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CidadeService — Testes Unitários (DT-01)")
class CidadeServiceTest {

    @Mock
    private CidadeRepository cidadeRepository;

    @Mock
    private ViagemRepository viagemRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private CidadeService cidadeService;

    @Test
    @DisplayName("excluir: remove as viagens vinculadas antes da cidade e retorna a contagem")
    void excluir_comViagensVinculadas_removeEmCascata() {
        UUID id = UUID.randomUUID();
        Cidade cidade = new Cidade("João Pessoa", "PB", TipoCidade.METROPOLITANA);
        when(cidadeRepository.findById(id)).thenReturn(Optional.of(cidade));
        when(viagemRepository.countByCidadeDestino_Id(id)).thenReturn(3L);

        long removidas = cidadeService.excluir(id);

        assertThat(removidas).isEqualTo(3L);
        verify(viagemRepository).deleteByCidadeDestino_Id(id);
        verify(cidadeRepository).delete(cidade);
    }

    @Test
    @DisplayName("excluir: sem viagens vinculadas não chama deleteBy e apaga só a cidade")
    void excluir_semViagens_apagaSomenteCidade() {
        UUID id = UUID.randomUUID();
        Cidade cidade = new Cidade("Patos", "PB", TipoCidade.ORIGEM);
        when(cidadeRepository.findById(id)).thenReturn(Optional.of(cidade));
        when(viagemRepository.countByCidadeDestino_Id(id)).thenReturn(0L);

        long removidas = cidadeService.excluir(id);

        assertThat(removidas).isZero();
        verify(viagemRepository, never()).deleteByCidadeDestino_Id(any());
        verify(cidadeRepository).delete(cidade);
    }

    @Test
    @DisplayName("excluir: cidade inexistente lança RecursoNaoEncontradoException")
    void excluir_cidadeInexistente_lancaExcecao() {
        UUID id = UUID.randomUUID();
        when(cidadeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cidadeService.excluir(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(cidadeRepository, never()).delete(any());
        verify(viagemRepository, never()).deleteByCidadeDestino_Id(any());
    }

    @Test
    @DisplayName("contarViagensVinculadas: delega a contagem ao ViagemRepository")
    void contarViagensVinculadas_delega() {
        UUID id = UUID.randomUUID();
        when(viagemRepository.countByCidadeDestino_Id(id)).thenReturn(5L);

        assertThat(cidadeService.contarViagensVinculadas(id)).isEqualTo(5L);
    }
}
