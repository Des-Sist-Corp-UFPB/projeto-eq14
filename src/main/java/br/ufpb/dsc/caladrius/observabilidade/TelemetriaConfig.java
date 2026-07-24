package br.ufpb.dsc.caladrius.observabilidade;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fornece o {@link OpenTelemetry} usado pela instrumentação manual (SPEC-14).
 *
 * <p>Com o agente Java anexado (dev/prod configurados via {@code JAVA_TOOL_OPTIONS}),
 * {@link GlobalOpenTelemetry#get()} devolve a instância do agente e os spans de negócio
 * fluem pelo mesmo pipeline OTLP dos sinais automáticos (traces→Tempo, logs→Loki).
 *
 * <p>Sem o agente, {@code GlobalOpenTelemetry.get()} devolve um <em>no-op</em>: a
 * aplicação sobe idêntica à de hoje (RN-OBS-01) e o {@link RastreamentoService} apenas
 * executa a ação e descarta o span (RN-OBS-06). Nenhuma dependência nova em runtime
 * além da <em>API</em> do OpenTelemetry — o SDK vem do agente.
 *
 * <p>O {@link ConditionalOnMissingBean} deixa o bean substituível (ex.: nos testes de
 * integração que queiram um SDK próprio); os testes de unidade injetam o SDK de teste
 * diretamente no {@link RastreamentoService}, sem passar por este bean.
 */
@Configuration
public class TelemetriaConfig {

    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
