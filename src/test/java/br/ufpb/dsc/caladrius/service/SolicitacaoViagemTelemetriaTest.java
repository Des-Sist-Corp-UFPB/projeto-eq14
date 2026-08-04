package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.domain.enums.TipoSolicitacao;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.observabilidade.RastreamentoService;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.SolicitacaoViagemRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes de <strong>cenário real</strong> (SPEC-OPE-01): dirigem os métodos de negócio de
 * {@link SolicitacaoViagemService} de verdade e verificam que a telemetria manual emitida
 * reflete a operação de domínio — nome do span, atributos (destino/tipo) e status
 * (sucesso × ERROR). Cobre os <strong>dois</strong> spans fiados:
 * <ul>
 *   <li>{@code solicitar-sob-demanda} — o passageiro solicita ({@link SolicitacaoViagemService#solicitarSobDemanda});</li>
 *   <li>{@code aprovar-solicitacao} — o gestor aprova/aloca ({@link SolicitacaoViagemService#aprovar}).</li>
 * </ul>
 *
 * <p>Diferente de {@code RastreamentoServiceTest} (que testa a ferramenta isolada), aqui o
 * {@link RastreamentoService} é <strong>real</strong>, ligado a um SDK de teste
 * ({@link OpenTelemetryExtension} + {@code InMemorySpanExporter}); os repositórios são
 * mockados como nos demais testes do serviço.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitacaoViagemService — telemetria de negócio (SPEC-OPE-01, cenário real)")
class SolicitacaoViagemTelemetriaTest {

    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    @Mock private SolicitacaoViagemRepository solicitacaoRepository;
    @Mock private LinhaProgramadaRepository linhaRepository;
    @Mock private ViagemRepository viagemRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CidadeRepository cidadeRepository;
    @Mock private NotificacaoService notificacaoService;
    @Mock private WhatsappService whatsappService;
    @Mock private AuditoriaService auditoriaService;

    private SolicitacaoViagemService service;

    @BeforeEach
    void montarServicoComHelperReal() {
        service = new SolicitacaoViagemService(solicitacaoRepository, linhaRepository,
                viagemRepository, usuarioRepository, cidadeRepository, notificacaoService,
                whatsappService, new RastreamentoService(otel.getOpenTelemetry()), auditoriaService);
    }

    @AfterEach
    void limparSpans() {
        otel.clearSpans();
    }

    private Usuario passageiro(UUID id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNomeCompleto("Maria");
        return u;
    }

    private Cidade cidadeComId(UUID id, String nome) {
        Cidade c = new Cidade(nome, "PB", null);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    /** Localiza o span de negócio pelo nome (falha o teste se não foi emitido). */
    private SpanData span(String nome) {
        return otel.getSpans().stream()
                .filter(s -> s.getName().equals(nome))
                .findFirst()
                .orElseThrow(() -> new AssertionError("span '" + nome + "' não emitido"));
    }

    // ------------------------------------------------------- solicitar-sob-demanda

    @Test
    @DisplayName("CENÁRIO REAL: solicitar sob demanda emite span 'solicitar-sob-demanda' com destino e tipo")
    void solicitarSobDemanda_emiteSpanDeNegocioComAtributos() {
        UUID passageiroId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(5);
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId))
                .thenReturn(Optional.of(passageiro(passageiroId)));
        when(cidadeRepository.findById(cidadeId))
                .thenReturn(Optional.of(cidadeComId(cidadeId, "João Pessoa")));
        when(solicitacaoRepository.existsByPassageiro_IdAndCidadeDestino_IdAndDataDesejadaAndStatusNot(
                passageiroId, cidadeId, data, StatusSolicitacao.CANCELADA)).thenReturn(false);
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.solicitarSobDemanda(passageiroId, cidadeId, data, LocalTime.of(14, 0), "cadeirante");

        SpanData span = span("solicitar-sob-demanda");
        assertThat(span.getAttributes().get(stringKey("solicitacao.cidade_destino"))).isEqualTo("João Pessoa");
        assertThat(span.getAttributes().get(stringKey("solicitacao.tipo"))).isEqualTo("SOB_DEMANDA");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET); // sucesso
    }

    @Test
    @DisplayName("CENÁRIO REAL: solicitação duplicada (regra violada) deixa o span em ERROR")
    void solicitarSobDemanda_duplicada_marcaSpanErro() {
        UUID passageiroId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        LocalDate data = LocalDate.now().plusDays(5);
        when(usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId))
                .thenReturn(Optional.of(passageiro(passageiroId)));
        when(cidadeRepository.findById(cidadeId))
                .thenReturn(Optional.of(cidadeComId(cidadeId, "João Pessoa")));
        when(solicitacaoRepository.existsByPassageiro_IdAndCidadeDestino_IdAndDataDesejadaAndStatusNot(
                passageiroId, cidadeId, data, StatusSolicitacao.CANCELADA)).thenReturn(true);

        assertThatThrownBy(() -> service.solicitarSobDemanda(passageiroId, cidadeId, data, null, null))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(span("solicitar-sob-demanda").getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    // ------------------------------------------------------- aprovar-solicitacao

    @Test
    @DisplayName("CENÁRIO REAL: aprovar emite span 'aprovar-solicitacao' com destino, status de sucesso")
    void aprovar_emiteSpanDeNegocio() {
        UUID solId = UUID.randomUUID();
        UUID viagemId = UUID.randomUUID();
        Cidade destino = cidadeComId(UUID.randomUUID(), "João Pessoa");
        SolicitacaoViagem sol = new SolicitacaoViagem();
        sol.setTipo(TipoSolicitacao.SOB_DEMANDA);
        sol.setPassageiro(passageiro(UUID.randomUUID()));
        sol.setCidadeDestino(destino);
        sol.setDataDesejada(LocalDate.now().plusDays(3));
        sol.setStatus(StatusSolicitacao.PENDENTE);
        Viagem viagem = new Viagem();
        viagem.setCidadeDestino(destino);
        viagem.setHorarioSaida(LocalTime.of(8, 0));
        when(solicitacaoRepository.findById(solId)).thenReturn(Optional.of(sol));
        when(viagemRepository.findById(viagemId)).thenReturn(Optional.of(viagem));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aprovar(solId, viagemId);

        SpanData span = span("aprovar-solicitacao");
        assertThat(span.getAttributes().get(stringKey("solicitacao.cidade_destino"))).isEqualTo("João Pessoa");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    @DisplayName("CENÁRIO REAL: aprovar uma solicitação não-pendente deixa o span em ERROR")
    void aprovar_naoPendente_marcaSpanErro() {
        UUID solId = UUID.randomUUID();
        UUID viagemId = UUID.randomUUID();
        SolicitacaoViagem sol = new SolicitacaoViagem();
        sol.setTipo(TipoSolicitacao.SOB_DEMANDA);
        sol.setCidadeDestino(cidadeComId(UUID.randomUUID(), "João Pessoa"));
        sol.setStatus(StatusSolicitacao.ALOCADA); // já alocada → não pode aprovar de novo
        when(solicitacaoRepository.findById(solId)).thenReturn(Optional.of(sol));

        assertThatThrownBy(() -> service.aprovar(solId, viagemId))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(span("aprovar-solicitacao").getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }
}
