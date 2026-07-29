package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.whatsapp.EvolutionApiProvedor;
import br.ufpb.dsc.caladrius.whatsapp.ProvedorWhatsapp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.regex.Pattern;

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

    private static final Logger log = LoggerFactory.getLogger(WhatsappConfig.class);

    /** Detecta um esquema já presente na URL (`https://`, `http://`, …). */
    private static final Pattern ESQUEMA = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://");

    @Bean
    public ProvedorWhatsapp provedorWhatsapp(
            @Value("${EVOLUTION_URL}") String url,
            @Value("${EVOLUTION_API_KEY}") String apiKey,
            @Value("${EVOLUTION_INSTANCIA:caladrius}") String instancia,
            @Value("${WHATSAPP_WEBHOOK_TOKEN:}") String webhookToken,
            @Value("${APP_URL_PUBLICA:}") String appUrlPublica) {
        String base = normalizarUrl(url);
        if (!base.equals(url)) {
            log.info("EVOLUTION_URL normalizada para {} (informada: {})", base, url);
        }
        RestClient http = RestClient.builder()
                .baseUrl(base)
                .defaultHeader("apikey", apiKey)
                .build();
        return new EvolutionApiProvedor(http, instancia, webhookToken, normalizarUrl(appUrlPublica));
    }

    /**
     * Torna a URL utilizável como <em>base</em> do {@link RestClient}.
     *
     * <p>Existe por um motivo concreto: os painéis de hospedagem (Railway, Portainer)
     * mostram o endereço <strong>sem o esquema</strong> — e é isso que a pessoa cola no
     * {@code .env}. Sem {@code https://}, o JDK HttpClient recusa a URI relativa com
     * {@code IllegalArgumentException: URI with undefined scheme}. Barra no final também
     * é removida, porque o adaptador concatena os paths da Evolution.
     *
     * <p>Só o esquema é presumido: se a pessoa escreveu {@code http://}, respeita-se
     * (é o caso do dev local).
     */
    static String normalizarUrl(String bruta) {
        String url = bruta == null ? "" : bruta.trim();
        if (url.isEmpty()) {
            return url;
        }
        if (!ESQUEMA.matcher(url).find()) {
            url = "https://" + url;
        }
        // Remove as barras finais sem invadir o "://" do esquema.
        int inicioHost = url.indexOf("://") + 3;
        int fim = url.length();
        while (fim > inicioHost && url.charAt(fim - 1) == '/') {
            fim--;
        }
        return url.substring(0, fim);
    }
}
