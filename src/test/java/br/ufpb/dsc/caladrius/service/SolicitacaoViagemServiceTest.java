package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.SolicitacaoViagemRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link SolicitacaoViagemService} — SPEC-09:
 * solicitação, alocação automática, validações e cancelamento.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitacaoViagemService — Testes Unitários (SPEC-09)")
class SolicitacaoViagemServiceTest {

    @Mock private SolicitacaoViagemRepository solicitacaoRepository;
    @Mock private LinhaProgramadaRepository linhaRepository;
    @Mock private ViagemRepository viagemRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private SolicitacaoViagemService service;

    // ------------------------------------------------------------- helpers

    private Usuario passageiro(UUID id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNomeCompleto("Maria");
        return u;
    }

    /** Linha ativa que opera no dia da semana de {@code data}. */
    private LinhaProgramada linhaQueOpera(UUID id, LocalDate data) {
        LinhaProgramada l = new LinhaProgramada();
        ReflectionTestUtils.setField(l, "id", id);
        l.setAtiva(true);
        l.setCidadeDestino(new Cidade("João Pessoa", "PB", null));
        l.setHorarioSaida(LocalTime.of(8, 0));
        l.setHorarioChegada(LocalTime.of(10, 0));
        l.setDias(EnumSet.of(DiaSemana.de(data.getDayOfWeek())));
        return l;
    }

    private void stubPassageiroELinha(UUID passageiroId, UUID linhaId, LinhaProgramada linha) {
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId))
                .thenReturn(Optional.of(passageiro(passageiroId)));
        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));
    }

    // ------------------------------------------------------------- solicitar

    @Test
    @DisplayName("solicitar: sem viagem designada → fica PENDENTE")
    void solicitar_semViagem_pendente() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(7);
        stubPassageiroELinha(passageiroId, linhaId, linhaQueOpera(linhaId, data));
        when(solicitacaoRepository.existsByPassageiro_IdAndLinha_IdAndDataDesejadaAndStatusNot(
                eq(passageiroId), eq(linhaId), eq(data), eq(StatusSolicitacao.CANCELADA))).thenReturn(false);
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(linhaId, data)).thenReturn(Optional.empty());
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitacaoViagem s = service.solicitar(passageiroId, linhaId, data, "  ");

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(s.getViagem()).isNull();
        assertThat(s.getObservacao()).isNull(); // observação em branco vira null
        verify(solicitacaoRepository).save(s);
    }

    @Test
    @DisplayName("solicitar: viagem da linha+data já designada → fica ALOCADA")
    void solicitar_comViagem_alocada() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(7);
        stubPassageiroELinha(passageiroId, linhaId, linhaQueOpera(linhaId, data));
        when(solicitacaoRepository.existsByPassageiro_IdAndLinha_IdAndDataDesejadaAndStatusNot(
                any(), any(), any(), any())).thenReturn(false);
        Viagem viagem = new Viagem();
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(linhaId, data)).thenReturn(Optional.of(viagem));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitacaoViagem s = service.solicitar(passageiroId, linhaId, data, "cadeirante");

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.ALOCADA);
        assertThat(s.getViagem()).isSameAs(viagem);
        assertThat(s.getObservacao()).isEqualTo("cadeirante");
    }

    @Test
    @DisplayName("solicitar: linha inativa é rejeitada")
    void solicitar_linhaInativa_bloqueia() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(7);
        LinhaProgramada inativa = linhaQueOpera(linhaId, data);
        inativa.setAtiva(false);
        stubPassageiroELinha(passageiroId, linhaId, inativa);

        assertThatThrownBy(() -> service.solicitar(passageiroId, linhaId, data, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está disponível");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitar: data no passado é rejeitada")
    void solicitar_dataPassada_bloqueia() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate ontem = LocalDate.now().minusDays(1);
        stubPassageiroELinha(passageiroId, linhaId, linhaQueOpera(linhaId, ontem));

        assertThatThrownBy(() -> service.solicitar(passageiroId, linhaId, ontem, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("posterior a hoje");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitar: dia em que a linha não opera é rejeitado")
    void solicitar_diaNaoOpera_bloqueia() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(7);
        LinhaProgramada linha = linhaQueOpera(linhaId, data);
        linha.setDias(EnumSet.noneOf(DiaSemana.class)); // não opera em nenhum dia
        stubPassageiroELinha(passageiroId, linhaId, linha);

        assertThatThrownBy(() -> service.solicitar(passageiroId, linhaId, data, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não opera");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitar: duplicada (mesma linha+data) é rejeitada")
    void solicitar_duplicada_bloqueia() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(7);
        stubPassageiroELinha(passageiroId, linhaId, linhaQueOpera(linhaId, data));
        when(solicitacaoRepository.existsByPassageiro_IdAndLinha_IdAndDataDesejadaAndStatusNot(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.solicitar(passageiroId, linhaId, data, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já tem uma solicitação");
        verify(solicitacaoRepository, never()).save(any());
    }

    // ------------------------------------------------------------- cancelar

    @Test
    @DisplayName("cancelar: passageiro não cancela solicitação de outro")
    void cancelar_deOutro_bloqueia() {
        UUID solicitacaoId = UUID.randomUUID();
        SolicitacaoViagem s = new SolicitacaoViagem();
        s.setPassageiro(passageiro(UUID.randomUUID()));
        s.setStatus(StatusSolicitacao.PENDENTE);
        when(solicitacaoRepository.findById(solicitacaoId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.cancelar(solicitacaoId, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("suas próprias");
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelar: o próprio dono cancela (status → CANCELADA, viagem solta)")
    void cancelar_propria_ok() {
        UUID solicitacaoId = UUID.randomUUID();
        UUID passageiroId = UUID.randomUUID();
        SolicitacaoViagem s = new SolicitacaoViagem();
        s.setPassageiro(passageiro(passageiroId));
        s.setStatus(StatusSolicitacao.ALOCADA);
        s.setViagem(new Viagem());
        when(solicitacaoRepository.findById(solicitacaoId)).thenReturn(Optional.of(s));

        service.cancelar(solicitacaoId, passageiroId);

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.CANCELADA);
        assertThat(s.getViagem()).isNull();
        verify(solicitacaoRepository).save(s);
    }

    // ------------------------------------------------------------- listar (alocação tardia)

    @Test
    @DisplayName("listar: pendente vira ALOCADA quando a viagem passa a existir")
    void listar_pendenteViraAlocada() {
        UUID passageiroId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(3);
        UUID linhaId = UUID.randomUUID();
        SolicitacaoViagem s = new SolicitacaoViagem();
        s.setStatus(StatusSolicitacao.PENDENTE);
        s.setDataDesejada(data);
        s.setLinha(linhaQueOpera(linhaId, data));
        when(solicitacaoRepository.listarDoPassageiro(passageiroId)).thenReturn(List.of(s));
        Viagem viagem = new Viagem();
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(linhaId, data)).thenReturn(Optional.of(viagem));

        List<SolicitacaoViagem> resultado = service.listarDoPassageiro(passageiroId);

        assertThat(resultado).hasSize(1);
        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.ALOCADA);
        assertThat(s.getViagem()).isSameAs(viagem);
        verify(solicitacaoRepository).save(s);
    }

    // ------------------------------------------------------------- grade / calendário

    /** Linha que opera somente nos dias informados. */
    private LinhaProgramada linhaNosDias(EnumSet<DiaSemana> dias) {
        LinhaProgramada l = new LinhaProgramada();
        l.setAtiva(true);
        l.setCidadeDestino(new Cidade("Destino", "PB", null));
        l.setHorarioSaida(LocalTime.of(8, 0));
        l.setHorarioChegada(LocalTime.of(10, 0));
        l.setDias(dias);
        return l;
    }

    @Test
    @DisplayName("linhasQueOperamEm: filtra pelas linhas do dia da semana da data")
    void linhasQueOperamEm_filtraPorDia() {
        LinhaProgramada soSegunda = linhaNosDias(EnumSet.of(DiaSemana.SEGUNDA));
        LinhaProgramada todosOsDias = linhaNosDias(EnumSet.allOf(DiaSemana.class));
        when(linhaRepository.findByAtivaTrueOrderByHorarioSaidaAsc())
                .thenReturn(List.of(soSegunda, todosOsDias));

        LocalDate segunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate terca = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

        assertThat(service.linhasQueOperamEm(segunda)).containsExactlyInAnyOrder(soSegunda, todosOsDias);
        assertThat(service.linhasQueOperamEm(terca)).containsExactly(todosOsDias);
    }

    @Test
    @DisplayName("gradeSemanal: 7 dias (dom→sáb), com a linha em cada dia que opera")
    void gradeSemanal_montaSeteDias() {
        LinhaProgramada soSegunda = linhaNosDias(EnumSet.of(DiaSemana.SEGUNDA));
        LinhaProgramada todosOsDias = linhaNosDias(EnumSet.allOf(DiaSemana.class));
        when(linhaRepository.findByAtivaTrueOrderByHorarioSaidaAsc())
                .thenReturn(List.of(soSegunda, todosOsDias));

        var grade = service.gradeSemanal();

        assertThat(grade).hasSize(7);
        assertThat(grade).extracting(g -> g.dia()).containsExactly(DiaSemana.values());
        var segunda = grade.stream().filter(g -> g.dia() == DiaSemana.SEGUNDA).findFirst().orElseThrow();
        var domingo = grade.stream().filter(g -> g.dia() == DiaSemana.DOMINGO).findFirst().orElseThrow();
        assertThat(segunda.linhas()).containsExactlyInAnyOrder(soSegunda, todosOsDias);
        assertThat(domingo.linhas()).containsExactly(todosOsDias);
    }

    // ------------------------------------------------------------- bordas

    @Test
    @DisplayName("cancelar: solicitação já cancelada é no-op (não salva de novo)")
    void cancelar_jaCancelada_noOp() {
        UUID solicitacaoId = UUID.randomUUID();
        UUID passageiroId = UUID.randomUUID();
        SolicitacaoViagem s = new SolicitacaoViagem();
        s.setPassageiro(passageiro(passageiroId));
        s.setStatus(StatusSolicitacao.CANCELADA);
        when(solicitacaoRepository.findById(solicitacaoId)).thenReturn(Optional.of(s));

        service.cancelar(solicitacaoId, passageiroId);

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitar: passageiro inexistente lança RecursoNaoEncontradoException")
    void solicitar_passageiroInexistente_lanca() {
        UUID passageiroId = UUID.randomUUID();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.solicitar(passageiroId, UUID.randomUUID(), LocalDate.now().plusDays(1), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("solicitar: linha inexistente lança RecursoNaoEncontradoException")
    void solicitar_linhaInexistente_lanca() {
        UUID passageiroId = UUID.randomUUID();
        UUID linhaId = UUID.randomUUID();
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId))
                .thenReturn(Optional.of(passageiro(passageiroId)));
        when(linhaRepository.findById(linhaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.solicitar(passageiroId, linhaId, LocalDate.now().plusDays(1), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(solicitacaoRepository, never()).save(any());
    }
}
