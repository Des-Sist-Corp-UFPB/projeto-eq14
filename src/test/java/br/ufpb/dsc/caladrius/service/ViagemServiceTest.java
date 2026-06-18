package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.dto.DesignacaoForm;
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
}
