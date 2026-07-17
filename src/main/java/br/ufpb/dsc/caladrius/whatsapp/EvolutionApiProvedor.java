package br.ufpb.dsc.caladrius.whatsapp;

import br.ufpb.dsc.caladrius.util.Documentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Adaptador da porta {@link ProvedorWhatsapp} para a <strong>Evolution API v2</strong>
 * (SPEC-10/ADR-14) — self-hosted na VPS da equipe, autenticada pelo header
 * {@code apikey}.
 *
 * <p>Todo o vocabulário da Evolution (paths, payloads, estados {@code open}/
 * {@code connecting}/{@code close}) fica <strong>encapsulado aqui</strong>;
 * qualquer divergência de versão da API se resolve neste arquivo, sem tocar em
 * bot, notificações ou telas. Falhas de comunicação viram {@link WhatsappException}
 * (exceção da porta).
 *
 * <p>Não é anotado como {@code @Component}: o bean é criado condicionalmente
 * pelo {@code WhatsappConfig} quando {@code EVOLUTION_URL}/{@code EVOLUTION_API_KEY}
 * estão presentes (RN-WPP-02).
 */
public class EvolutionApiProvedor implements ProvedorWhatsapp {

    private final RestClient http;
    private final String instancia;
    private final String webhookToken;
    private final String appUrlPublica;

    /**
     * @param http          cliente já configurado com a base URL da Evolution e o header {@code apikey}
     * @param instancia     nome da instância (ex.: {@code caladrius})
     * @param webhookToken  segredo enviado pela Evolution no header {@code X-Webhook-Token}
     * @param appUrlPublica URL pública do CALADRIUS, destino do webhook (vazia = não registra webhook)
     */
    public EvolutionApiProvedor(RestClient http, String instancia,
                                String webhookToken, String appUrlPublica) {
        this.http = http;
        this.instancia = instancia;
        this.webhookToken = webhookToken;
        this.appUrlPublica = appUrlPublica;
    }

    @Override
    public StatusConexaoWhatsapp statusConexao() {
        try {
            JsonNode resposta = http.get()
                    .uri("/instance/connectionState/{instancia}", instancia)
                    .retrieve()
                    .body(JsonNode.class);
            return traduzirEstado(resposta == null ? "" : resposta.path("instance").path("state").asText());
        } catch (HttpClientErrorException.NotFound e) {
            // Instância ainda não criada no provedor.
            return StatusConexaoWhatsapp.DESCONECTADO;
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao consultar o estado da conexão WhatsApp", e);
        }
    }

    @Override
    public ConexaoWhatsapp iniciarConexao() {
        try {
            if (statusConexao() == StatusConexaoWhatsapp.DESCONECTADO) {
                criarInstanciaSeNaoExistir();
            }
            registrarWebhook();
            JsonNode resposta = http.get()
                    .uri("/instance/connect/{instancia}", instancia)
                    .retrieve()
                    .body(JsonNode.class);
            String qr = extrairQr(resposta);
            if (StringUtils.hasText(qr)) {
                return ConexaoWhatsapp.aguardandoQr(qr);
            }
            // Sem QR na resposta: ou já está pareada (conectada), ou o QR virá
            // pelo evento QRCODE_UPDATED do webhook.
            return new ConexaoWhatsapp(statusConexao(), null);
        } catch (WhatsappException e) {
            throw e;
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao iniciar a conexão WhatsApp", e);
        }
    }

    @Override
    public void desconectar() {
        try {
            http.delete()
                    .uri("/instance/logout/{instancia}", instancia)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao desconectar a sessão WhatsApp", e);
        }
    }

    @Override
    public Optional<ContaWhatsapp> contaConectada() {
        try {
            JsonNode resposta = http.get()
                    .uri("/instance/fetchInstances?instanceName={instancia}", instancia)
                    .retrieve()
                    .body(JsonNode.class);
            return extrairConta(resposta);
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao consultar a conta WhatsApp conectada", e);
        }
    }

    @Override
    public void enviarTexto(String telefone, String texto) {
        try {
            http.post()
                    .uri("/message/sendText/{instancia}", instancia)
                    .contentType(MediaType.APPLICATION_JSON)
                    // A Evolution espera o número com DDI (55 + DDD + número).
                    .body(Map.of("number", "55" + Documentos.apenasDigitos(telefone), "text", texto))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao enviar mensagem WhatsApp", e);
        }
    }

    // ------------------------------------------------------------ internos

    /** {@code open} → CONECTADO, {@code connecting} → AGUARDANDO_QR, demais → DESCONECTADO. */
    private StatusConexaoWhatsapp traduzirEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "open" -> StatusConexaoWhatsapp.CONECTADO;
            case "connecting" -> StatusConexaoWhatsapp.AGUARDANDO_QR;
            default -> StatusConexaoWhatsapp.DESCONECTADO;
        };
    }

    private void criarInstanciaSeNaoExistir() {
        try {
            http.post()
                    .uri("/instance/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("instanceName", instancia,
                            "integration", "WHATSAPP-BAILEYS",
                            "qrcode", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // 403/409: a instância já existe — segue para o connect.
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao criar a instância WhatsApp", e);
        }
    }

    /**
     * Aponta o webhook da instância para o CALADRIUS, com o token secreto no
     * header (RN-WPP-03). Sem {@code APP_URL_PUBLICA} (ex.: dev local sem URL
     * pública), o registro é pulado — só o envio funciona.
     */
    @Override
    public void registrarWebhook() {
        if (!StringUtils.hasText(appUrlPublica)) {
            return;
        }
        try {
            http.post()
                    .uri("/webhook/set/{instancia}", instancia)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("webhook", Map.of(
                            "enabled", true,
                            "url", appUrlPublica + "/webhooks/whatsapp",
                            "headers", Map.of("X-Webhook-Token", webhookToken),
                            "events", new String[]{"MESSAGES_UPSERT", "CONNECTION_UPDATE", "QRCODE_UPDATED"})))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new WhatsappException("Falha ao registrar o webhook na Evolution", e);
        }
    }

    /** O QR vem como {@code base64} na raiz (v2) ou aninhado em {@code qrcode.base64}. */
    private String extrairQr(JsonNode resposta) {
        if (resposta == null) {
            return null;
        }
        String qr = resposta.path("base64").asText(null);
        if (qr == null) {
            qr = resposta.path("qrcode").path("base64").asText(null);
        }
        return qr;
    }

    /**
     * O {@code fetchInstances} devolve uma lista; procura a nossa instância e
     * extrai dono ({@code ownerJid}) e nome do perfil. Tolerante aos dois
     * formatos conhecidos (objeto direto ou aninhado em {@code instance}).
     */
    private Optional<ContaWhatsapp> extrairConta(JsonNode resposta) {
        if (resposta == null || !resposta.isArray()) {
            return Optional.empty();
        }
        for (JsonNode item : resposta) {
            JsonNode dados = item.has("instance") ? item.path("instance") : item;
            String nome = dados.path("name").asText(dados.path("instanceName").asText(""));
            if (!instancia.equals(nome)) {
                continue;
            }
            String jid = dados.path("ownerJid").asText(dados.path("owner").asText(null));
            if (!StringUtils.hasText(jid)) {
                return Optional.empty();
            }
            return Optional.of(new ContaWhatsapp(
                    Documentos.telefoneDeJid(jid),
                    dados.path("profileName").asText(null)));
        }
        return Optional.empty();
    }
}
