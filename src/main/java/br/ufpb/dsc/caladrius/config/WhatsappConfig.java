package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.whatsapp.EvolutionApiProvedor;
import br.ufpb.dsc.caladrius.whatsapp.ProvedorWhatsapp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuração da integração WhatsApp (Evolution API) — SPEC-10/ADR-14.
 *
 * <p>Mesmo padrão do {@link OAuth2ClientConfig} (SPEC-08): o bean da porta
 * {@link ProvedorWhatsapp} só é criado quando {@code EVOLUTION_URL} e
 * {@code EVOLUTION_API_KEY} estão presentes <em>e não-vazias</em> no ambiente.
 * Sem elas, não há provedor: o canal WhatsApp do {@code NotificacaoService}
 * permanece como stub e o painel {@code /whatsapp} informa "integração não
 * configurada" (RN-WPP-02). A expressão trata variável vazia como ausente —
 * o Compose de produção pode injetar {@code EVOLUTION_URL=} em branco.
 *
 * <p>A {@code apikey} circula apenas aqui e no cliente HTTP — nunca aparece na
 * UI nem em logs (RN-WPP-09).
 */
@Configuration
@ConditionalOnExpression("'${EVOLUTION_URL:}' != '' && '${EVOLUTION_API_KEY:}' != ''")
public class WhatsappConfig {

    @Bean
    public ProvedorWhatsapp provedorWhatsapp(
            @Value("${EVOLUTION_URL}") String url,
            @Value("${EVOLUTION_API_KEY}") String apiKey,
            @Value("${EVOLUTION_INSTANCIA:caladrius}") String instancia,
            @Value("${WHATSAPP_WEBHOOK_TOKEN:}") String webhookToken,
            @Value("${APP_URL_PUBLICA:}") String appUrlPublica) {
        RestClient http = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("apikey", apiKey)
                .build();
        return new EvolutionApiProvedor(http, instancia, webhookToken, appUrlPublica);
    }
}
