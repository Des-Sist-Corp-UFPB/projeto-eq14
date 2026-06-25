package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import br.ufpb.dsc.caladrius.dto.LinhaProgramadaForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link LinhaProgramadaService} — linhas recorrentes
 * (SPEC-06): validação de horários/dias, origem por cidade-sede e proteção na exclusão.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LinhaProgramadaService — Testes Unitários (SPEC-06)")
class LinhaProgramadaServiceTest {

    @Mock private LinhaProgramadaRepository linhaRepository;
    @Mock private CidadeRepository cidadeRepository;
    @Mock private ConfiguracaoService configuracaoService;
    @Mock private ViagemRepository viagemRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private LinhaProgramadaService service;

    private Cidade cidade(String nome) {
        Cidade c = new Cidade(nome, "PB", TipoCidade.METROPOLITANA);
        c.setId(UUID.randomUUID());
        return c;
    }

    /** LinhaProgramada não expõe setId (id gerado pelo JPA); fixamos via reflexão no teste. */
    private LinhaProgramada linhaComId() {
        LinhaProgramada l = new LinhaProgramada();
        ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
        return l;
    }

    private LinhaProgramadaForm form(UUID origemId, UUID destinoId, LocalTime saida,
                                     LocalTime chegada, LocalTime retorno, Set<DiaSemana> dias) {
        return new LinhaProgramadaForm(origemId, destinoId, saida, chegada, retorno, dias, true);
    }

    /** Caminho feliz: horários coerentes e ao menos um dia → persiste e audita. */
    @Test
    @DisplayName("criar: linha válida é persistida e auditada")
    void criar_valida_persiste() {
        Cidade destino = cidade("João Pessoa");
        when(cidadeRepository.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.empty());
        when(linhaRepository.save(any(LinhaProgramada.class))).thenAnswer(inv -> {
            LinhaProgramada l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
            return l;
        });

        LinhaProgramada criada = service.criar(form(null, destino.getId(),
                LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(14, 0),
                Set.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA)));

        assertThat(criada.getCidadeDestino()).isEqualTo(destino);
        assertThat(criada.getDias()).containsExactlyInAnyOrder(DiaSemana.SEGUNDA, DiaSemana.QUARTA);
        verify(auditoriaService).registrarOperacao(eq("LINHA_CRIADA"), eq("LinhaProgramada"), any(), any());
    }

    /** Origem ausente cai na cidade-sede configurada. */
    @Test
    @DisplayName("criar: sem origem usa a cidade-sede configurada")
    void criar_semOrigem_usaCidadeSede() {
        Cidade destino = cidade("Campina Grande");
        Cidade sede = cidade("Solânea");
        when(cidadeRepository.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.of(sede.getId()));
        when(cidadeRepository.findById(sede.getId())).thenReturn(Optional.of(sede));
        when(linhaRepository.save(any(LinhaProgramada.class))).thenAnswer(inv -> {
            LinhaProgramada l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
            return l;
        });

        LinhaProgramada criada = service.criar(form(null, destino.getId(),
                LocalTime.of(7, 0), LocalTime.of(9, 0), null, Set.of(DiaSemana.SEXTA)));

        assertThat(criada.getCidadeOrigem()).isEqualTo(sede);
    }

    /** Chegada deve ser após a saída. */
    @Test
    @DisplayName("criar: chegada não posterior à saída é rejeitada")
    void criar_chegadaInvalida_rejeita() {
        Cidade destino = cidade("Guarabira");
        when(cidadeRepository.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(form(null, destino.getId(),
                LocalTime.of(10, 0), LocalTime.of(10, 0), null, Set.of(DiaSemana.SEGUNDA))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("chegada");
        verify(linhaRepository, never()).save(any());
    }

    /** Retorno, quando informado, deve ser após a chegada. */
    @Test
    @DisplayName("criar: retorno não posterior à chegada é rejeitado")
    void criar_retornoInvalido_rejeita() {
        Cidade destino = cidade("Mamanguape");
        when(cidadeRepository.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(form(null, destino.getId(),
                LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(9, 0), Set.of(DiaSemana.SEGUNDA))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("retorno");
    }

    /** Ao menos um dia da semana é obrigatório. */
    @Test
    @DisplayName("criar: sem dias da semana é rejeitada")
    void criar_semDias_rejeita() {
        Cidade destino = cidade("Sapé");
        when(cidadeRepository.findById(destino.getId())).thenReturn(Optional.of(destino));
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(form(null, destino.getId(),
                LocalTime.of(8, 0), LocalTime.of(10, 0), null, Set.of())))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("dia");
    }

    /** Destino inexistente interrompe a criação. */
    @Test
    @DisplayName("criar: cidade de destino inexistente lança não-encontrado")
    void criar_destinoInexistente_lanca() {
        UUID destinoId = UUID.randomUUID();
        when(cidadeRepository.findById(destinoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(form(null, destinoId,
                LocalTime.of(8, 0), LocalTime.of(10, 0), null, Set.of(DiaSemana.SEGUNDA))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    /** Alternar habilita/desabilita a linha sem apagá-la. */
    @Test
    @DisplayName("alternarAtiva: inverte o estado e audita")
    void alternarAtiva_inverte() {
        LinhaProgramada linha = linhaComId();
        linha.setCidadeDestino(cidade("Bayeux"));
        linha.setHorarioSaida(LocalTime.of(6, 0));
        linha.setAtiva(true);
        when(linhaRepository.findById(linha.getId())).thenReturn(Optional.of(linha));

        service.alternarAtiva(linha.getId());

        assertThat(linha.isAtiva()).isFalse();
        verify(auditoriaService).registrarOperacao(eq("LINHA_DESABILITADA"), eq("LinhaProgramada"), any(), any());
    }

    /** Linha já com viagens materializadas não pode ser excluída. */
    @Test
    @DisplayName("excluir: bloqueia quando há viagens vinculadas")
    void excluir_comViagens_bloqueia() {
        LinhaProgramada linha = linhaComId();
        linha.setCidadeDestino(cidade("Pedras de Fogo"));
        linha.setHorarioSaida(LocalTime.of(6, 0));
        when(linhaRepository.findById(linha.getId())).thenReturn(Optional.of(linha));
        when(viagemRepository.countByLinhaProgramada_Id(linha.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.excluir(linha.getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Desabilite");
        verify(linhaRepository, never()).delete(any());
    }

    /** Sem viagens, a linha é removida e auditada. */
    @Test
    @DisplayName("excluir: sem viagens remove e audita")
    void excluir_semViagens_remove() {
        LinhaProgramada linha = linhaComId();
        linha.setCidadeDestino(cidade("Lucena"));
        linha.setHorarioSaida(LocalTime.of(6, 0));
        when(linhaRepository.findById(linha.getId())).thenReturn(Optional.of(linha));
        when(viagemRepository.countByLinhaProgramada_Id(linha.getId())).thenReturn(0L);

        service.excluir(linha.getId());

        verify(linhaRepository).delete(linha);
        verify(auditoriaService).registrarOperacao(eq("LINHA_EXCLUIDA"), eq("LinhaProgramada"), any(), any());
    }
}
