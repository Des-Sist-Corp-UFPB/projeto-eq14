package br.ufpb.dsc.caladrius.observabilidade;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de {@link RastreamentoService} — a <em>ferramenta</em> de
 * instrumentação manual (SPEC-OPE-01), em isolamento e sem agente/rede.
 *
 * <p>O {@link OpenTelemetryExtension} instala um SDK de teste com um
 * {@code InMemorySpanExporter}; os spans criados pelo helper ficam disponíveis em
 * {@link OpenTelemetryExtension#getSpans()} para asserção. É a lib oficial do
 * OpenTelemetry para este fim.
 */
@DisplayName("RastreamentoService — instrumentação manual de traces (SPEC-OPE-01)")
class RastreamentoServiceTest {

    @RegisterExtension
    final OpenTelemetryExtension otel = OpenTelemetryExtension.create();

    private final RastreamentoService rastreamento = new RastreamentoService(otel.getOpenTelemetry());

    @AfterEach
    void limparSpans() {
        otel.clearSpans();
    }

    @Test
    @DisplayName("caminho feliz: cria um span com nome e atributo, status de sucesso, e devolve o resultado")
    void rastrear_caminhoFeliz_criaSpanEDevolveResultado() {
        String resultado = rastreamento.rastrear(
                "op-teste", Map.of("solicitacao.tipo", "SOB_DEMANDA"), () -> "ok");

        assertThat(resultado).isEqualTo("ok");
        assertThat(otel.getSpans()).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("op-teste");
            assertThat(span.getAttributes().get(stringKey("solicitacao.tipo"))).isEqualTo("SOB_DEMANDA");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET); // sucesso implícito
            assertThat(span.getEndEpochNanos()).isPositive();                          // span encerrado
        });
    }

    @Test
    @DisplayName("erro: repropaga a exceção, marca o span ERROR e grava o evento 'exception'")
    void rastrear_quandoAcaoLanca_marcaErroERepropaga() {
        IllegalStateException falha = new IllegalStateException("regra violada");

        assertThatThrownBy(() -> rastreamento.rastrear("op-erro", Map.of(), () -> {
            throw falha;
        })).isSameAs(falha);

        SpanData span = otel.getSpans().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).extracting(EventData::getName).contains("exception");
    }

    @Test
    @DisplayName("sem agente (OpenTelemetry no-op): executa a ação e não quebra — RN-OBS-06")
    void rastrear_semAgente_ehNoopSeguro() {
        RastreamentoService semAgente = new RastreamentoService(OpenTelemetry.noop());

        Integer resultado = semAgente.rastrear("op-noop", Map.of("chave", "valor"), () -> 42);

        assertThat(resultado).isEqualTo(42);
    }
}
