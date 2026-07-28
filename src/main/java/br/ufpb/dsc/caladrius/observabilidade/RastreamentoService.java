package br.ufpb.dsc.caladrius.observabilidade;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Instrumentação <strong>manual</strong> de traces (SPEC-14 / ADR-18). Fina camada
 * sobre a API do OpenTelemetry: abre um <em>span</em> de negócio ao redor de uma
 * ação, anexa atributos de domínio e — se a ação falhar — grava a exceção e marca o
 * span como {@link StatusCode#ERROR}, sem engolir o erro (a exceção segue propagando).
 *
 * <p>Complementa a instrumentação <em>automática</em> do agente Java (HTTP, JDBC, JVM,
 * logs): aqui nomeamos operações do nosso domínio (ex.: {@code solicitar-sob-demanda})
 * e as enriquecemos com atributos que só o negócio conhece (destino, tipo).
 *
 * <p><strong>Desligado por padrão, sem quebrar (RN-OBS-01/06):</strong> em produção o
 * {@link OpenTelemetry} injetado vem do agente (via {@code GlobalOpenTelemetry}); sem o
 * agente é um <em>no-op</em> — o span é descartado, a ação roda igual e nenhuma exceção
 * de telemetria chega ao chamador. Por isso pode ser fiado em serviços sem risco de
 * derrubar a aplicação quando a observabilidade não está configurada.
 */
@Service
public class RastreamentoService {

    /** Nome do <em>instrumentation scope</em> — aparece nos spans no Grafana/Tempo. */
    private static final String ESCOPO = "br.ufpb.dsc.caladrius";

    private final Tracer tracer;

    public RastreamentoService(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(ESCOPO);
    }

    /**
     * Executa {@code acao} dentro de um span chamado {@code nome}, com os
     * {@code atributos} de negócio anexados. No caminho feliz devolve o resultado da
     * ação e o span termina com status implícito de sucesso. Se a ação lançar uma
     * {@link RuntimeException}, o span registra a exceção, recebe {@link StatusCode#ERROR}
     * e a exceção é <strong>repropagada</strong> — a telemetria observa a falha sem
     * alterar o comportamento de negócio.
     *
     * @param nome      nome do span (operação de negócio; use kebab-case, ex.: {@code solicitar-sob-demanda})
     * @param atributos atributos de domínio (chave→valor); pode ser vazio, não deve conter segredos/PII (RN-OBS-08)
     * @param acao      a ação a executar e medir
     * @param <T>       tipo do resultado
     * @return o valor devolvido por {@code acao}
     */
    public <T> T rastrear(String nome, Map<String, String> atributos, Supplier<T> acao) {
        Span span = tracer.spanBuilder(nome).startSpan();
        if (atributos != null) {
            atributos.forEach(span::setAttribute);
        }
        try (Scope escopo = span.makeCurrent()) {
            return acao.get();
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
}
