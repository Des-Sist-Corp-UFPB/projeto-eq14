package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.domain.enums.TipoViagem;
import br.ufpb.dsc.caladrius.dto.DesignacaoForm;
import br.ufpb.dsc.caladrius.dto.PainelSemana;
import br.ufpb.dsc.caladrius.dto.ViagemForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários (Mockito) de {@link ViagemService} — SPEC-06: status,
 * conflito de disponibilidade e designação.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ViagemService — Testes Unitários (SPEC-06)")
class ViagemServiceTest {

    @Mock private ViagemRepository viagemRepository;
    @Mock private VeiculoRepository veiculoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CidadeRepository cidadeRepository;
    @Mock private LinhaProgramadaRepository linhaRepository;
    @Mock private ConfiguracaoService configuracaoService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private ViagemService viagemService;

    private Usuario motorista(UUID id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNomeCompleto("João");
        u.setPapeis(EnumSet.of(Papel.MOTORISTA));
        return u;
    }

    // -------------------------------------------------- alterarStatus

    @Test
    @DisplayName("alterarStatus: motorista não altera viagem de outro")
    void alterarStatus_motoristaOutro_bloqueia() {
        UUID viagemId = UUID.randomUUID();
        Viagem v = new Viagem();
        v.setMotorista(motorista(UUID.randomUUID()));
        when(viagemRepository.findById(viagemId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> viagemService.alterarStatus(
                viagemId, StatusViagem.EM_ANDAMENTO, UUID.randomUUID(), false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprias viagens");

        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("alterarStatus: motorista altera a própria viagem")
    void alterarStatus_motoristaPropria_ok() {
        UUID viagemId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Viagem v = new Viagem();
        v.setMotorista(motorista(motoristaId));
        when(viagemRepository.findById(viagemId)).thenReturn(Optional.of(v));

        viagemService.alterarStatus(viagemId, StatusViagem.CONCLUIDA, motoristaId, false);

        assertThat(v.getStatus()).isEqualTo(StatusViagem.CONCLUIDA);
        verify(viagemRepository).save(v);
    }

    // -------------------------------------------------- designar

    @Test
    @DisplayName("designar: linha desabilitada é rejeitada")
    void designar_linhaDesabilitada_bloqueia() {
        UUID linhaId = UUID.randomUUID();
        LinhaProgramada linha = new LinhaProgramada();
        linha.setAtiva(false);
        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));

        DesignacaoForm form = new DesignacaoForm(linhaId, LocalDate.of(2026, 6, 23),
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> viagemService.designar(form, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desabilitada");
    }

    @Test
    @DisplayName("designar: conflito de horário do motorista é rejeitado (RN-VIA-08)")
    void designar_conflitoMotorista_bloqueia() {
        LocalDate data = LocalDate.of(2026, 6, 23);
        UUID linhaId = UUID.randomUUID();
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID criadoPorId = UUID.randomUUID();

        Cidade destino = new Cidade("João Pessoa", "PB", null);
        LinhaProgramada linha = new LinhaProgramada();
        linha.setAtiva(true);
        linha.setCidadeDestino(destino);
        linha.setHorarioSaida(LocalTime.of(8, 0));
        linha.setHorarioChegada(LocalTime.of(10, 0));
        linha.setDias(EnumSet.of(DiaSemana.de(data.getDayOfWeek())));

        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);

        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(any(), eq(data))).thenReturn(Optional.empty());
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(motorista(motoristaId)));
        when(usuarioRepository.findById(criadoPorId)).thenReturn(Optional.of(new Usuario()));
        when(viagemRepository.contarConflitosMotorista(eq(motoristaId), eq(data), any(), any(), any()))
                .thenReturn(1L);

        DesignacaoForm form = new DesignacaoForm(linhaId, data, veiculoId, motoristaId);

        assertThatThrownBy(() -> viagemService.designar(form, criadoPorId))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Motorista indisponível");

        verify(viagemRepository, never()).save(any());
    }

    // -------------------------------------------------- criar (imprevista)

    @Test
    @DisplayName("criar: conflito de veículo na imprevista é rejeitado")
    void criar_conflitoVeiculo_bloqueia() {
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID criadoPorId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 6, 23);

        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);
        Cidade destino = new Cidade("João Pessoa", "PB", null);

        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(motorista(motoristaId)));
        when(cidadeRepository.findById(cidadeId)).thenReturn(Optional.of(destino));
        when(usuarioRepository.findById(criadoPorId)).thenReturn(Optional.of(new Usuario()));
        when(viagemRepository.contarConflitosMotorista(any(), any(), any(), any(), any())).thenReturn(0L);
        when(viagemRepository.contarConflitosVeiculo(eq(veiculoId), eq(data), any(), any(), any())).thenReturn(1L);

        ViagemForm form = new ViagemForm(veiculoId, motoristaId, cidadeId, data,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThatThrownBy(() -> viagemService.criar(form, criadoPorId))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Veículo indisponível");

        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: caminho feliz salva uma viagem IMPREVISTA PLANEJADA")
    void criar_valido_salvaImprevista() {
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID criadoPorId = UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 6, 23);

        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(motorista(motoristaId)));
        when(cidadeRepository.findById(cidadeId)).thenReturn(Optional.of(new Cidade("João Pessoa", "PB", null)));
        when(usuarioRepository.findById(criadoPorId)).thenReturn(Optional.of(new Usuario()));
        when(viagemRepository.contarConflitosMotorista(any(), any(), any(), any(), any())).thenReturn(0L);
        when(viagemRepository.contarConflitosVeiculo(any(), any(), any(), any(), any())).thenReturn(0L);
        when(configuracaoService.getCidadeSedeId()).thenReturn(Optional.empty());
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(inv -> {
            Viagem v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        ViagemForm form = new ViagemForm(veiculoId, motoristaId, cidadeId, data,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        Viagem v = viagemService.criar(form, criadoPorId);

        assertThat(v.getTipo()).isEqualTo(TipoViagem.IMPREVISTA);
        assertThat(v.getStatus()).isEqualTo(StatusViagem.PLANEJADA);
        verify(viagemRepository).save(any(Viagem.class));
    }

    @Test
    @DisplayName("criar: motorista sem o papel MOTORISTA é rejeitado")
    void criar_motoristaSemPapel_bloqueia() {
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);
        Usuario semPapel = new Usuario();
        semPapel.setId(motoristaId);
        semPapel.setPapeis(EnumSet.noneOf(Papel.class));
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(semPapel));

        ViagemForm form = new ViagemForm(veiculoId, motoristaId, UUID.randomUUID(),
                LocalDate.of(2026, 6, 23), LocalTime.of(8, 0), LocalTime.of(10, 0));

        assertThatThrownBy(() -> viagemService.criar(form, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("papel de motorista");
        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("criar: horário de chegada não posterior ao de saída é rejeitado")
    void criar_chegadaAntesDaSaida_bloqueia() {
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID criadoPorId = UUID.randomUUID();
        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(motorista(motoristaId)));
        when(cidadeRepository.findById(cidadeId)).thenReturn(Optional.of(new Cidade("João Pessoa", "PB", null)));
        when(usuarioRepository.findById(criadoPorId)).thenReturn(Optional.of(new Usuario()));

        ViagemForm form = new ViagemForm(veiculoId, motoristaId, cidadeId,
                LocalDate.of(2026, 6, 23), LocalTime.of(10, 0), LocalTime.of(8, 0));

        assertThatThrownBy(() -> viagemService.criar(form, criadoPorId))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("chegada deve ser após");
        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("designar: caminho feliz materializa viagem ROTINEIRA com origem da linha")
    void designar_valido_salvaRotineira() {
        LocalDate data = LocalDate.of(2026, 6, 23);
        UUID linhaId = UUID.randomUUID();
        UUID veiculoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID criadoPorId = UUID.randomUUID();

        LinhaProgramada linha = new LinhaProgramada();
        linha.setAtiva(true);
        linha.setCidadeOrigem(new Cidade("Patos", "PB", null));
        linha.setCidadeDestino(new Cidade("João Pessoa", "PB", null));
        linha.setHorarioSaida(LocalTime.of(8, 0));
        linha.setHorarioChegada(LocalTime.of(10, 0));
        linha.setDias(EnumSet.of(DiaSemana.de(data.getDayOfWeek())));

        Veiculo veiculo = new Veiculo();
        veiculo.setId(veiculoId);

        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(any(), eq(data))).thenReturn(Optional.empty());
        when(veiculoRepository.findByIdAndRemovidoEmIsNull(veiculoId)).thenReturn(Optional.of(veiculo));
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(motoristaId)).thenReturn(Optional.of(motorista(motoristaId)));
        when(usuarioRepository.findById(criadoPorId)).thenReturn(Optional.of(new Usuario()));
        when(viagemRepository.contarConflitosMotorista(any(), any(), any(), any(), any())).thenReturn(0L);
        when(viagemRepository.contarConflitosVeiculo(any(), any(), any(), any(), any())).thenReturn(0L);
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(inv -> {
            Viagem v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        DesignacaoForm form = new DesignacaoForm(linhaId, data, veiculoId, motoristaId);

        Viagem v = viagemService.designar(form, criadoPorId);

        assertThat(v.getTipo()).isEqualTo(TipoViagem.ROTINEIRA);
        assertThat(v.getCidadeOrigem().getNome()).isEqualTo("Patos");
        assertThat(v.getCidadeDestino().getNome()).isEqualTo("João Pessoa");
        verify(viagemRepository).save(any(Viagem.class));
    }

    @Test
    @DisplayName("designar: linha que não opera no dia da semana é rejeitada")
    void designar_naoOperaNoDia_bloqueia() {
        LocalDate data = LocalDate.of(2026, 6, 23);
        UUID linhaId = UUID.randomUUID();
        LinhaProgramada linha = new LinhaProgramada();
        linha.setAtiva(true);
        linha.setDias(EnumSet.noneOf(DiaSemana.class)); // não opera em nenhum dia
        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));

        DesignacaoForm form = new DesignacaoForm(linhaId, data, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> viagemService.designar(form, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não opera neste dia");
        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("designar: linha já designada nesta data é rejeitada")
    void designar_jaDesignada_bloqueia() {
        LocalDate data = LocalDate.of(2026, 6, 23);
        UUID linhaId = UUID.randomUUID();
        LinhaProgramada linha = new LinhaProgramada();
        linha.setAtiva(true);
        linha.setDias(EnumSet.of(DiaSemana.de(data.getDayOfWeek())));
        when(linhaRepository.findById(linhaId)).thenReturn(Optional.of(linha));
        when(viagemRepository.findByLinhaProgramada_IdAndDataViagem(any(), eq(data)))
                .thenReturn(Optional.of(new Viagem()));

        DesignacaoForm form = new DesignacaoForm(linhaId, data, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> viagemService.designar(form, UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi designada");
        verify(viagemRepository, never()).save(any());
    }

    @Test
    @DisplayName("alterarStatus: o gerente pode alterar o status de qualquer viagem")
    void alterarStatus_gerente_alteraQualquer() {
        UUID viagemId = UUID.randomUUID();
        Viagem v = new Viagem();
        v.setMotorista(motorista(UUID.randomUUID()));
        when(viagemRepository.findById(viagemId)).thenReturn(Optional.of(v));

        viagemService.alterarStatus(viagemId, StatusViagem.CONFIRMADA, UUID.randomUUID(), true);

        assertThat(v.getStatus()).isEqualTo(StatusViagem.CONFIRMADA);
        verify(viagemRepository).save(v);
    }

    @Test
    @DisplayName("excluir: remove a viagem e registra auditoria")
    void excluir_removeViagem() {
        UUID id = UUID.randomUUID();
        Viagem v = new Viagem();
        v.setCidadeDestino(new Cidade("João Pessoa", "PB", null));
        v.setDataViagem(LocalDate.of(2026, 6, 23));
        when(viagemRepository.findById(id)).thenReturn(Optional.of(v));

        viagemService.excluir(id);

        verify(viagemRepository).delete(v);
        verify(auditoriaService).registrarOperacao(eq("VIAGEM_EXCLUIDA"), eq("Viagem"), eq(id.toString()), any());
    }

    @Test
    @DisplayName("listarDoMotorista: delega ao repositório")
    void listarDoMotorista_delega() {
        UUID motoristaId = UUID.randomUUID();
        when(viagemRepository.listarDoMotorista(motoristaId)).thenReturn(List.of(new Viagem()));

        assertThat(viagemService.listarDoMotorista(motoristaId)).hasSize(1);
    }

    @Test
    @DisplayName("painelSemana: monta os 7 dias (domingo→sábado)")
    void painelSemana_seteDias() {
        when(viagemRepository.listarDaSemana(any(), any())).thenReturn(List.of());
        when(linhaRepository.ativasNoDia(any())).thenReturn(List.of());

        PainelSemana painel = viagemService.painelSemana(LocalDate.of(2026, 6, 24));

        assertThat(painel.dias()).hasSize(7);
        assertThat(painel.inicio().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
    }
}
