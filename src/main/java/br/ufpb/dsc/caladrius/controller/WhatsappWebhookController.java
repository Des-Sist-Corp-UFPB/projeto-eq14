package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.service.WhatsappService;
import br.ufpb.dsc.caladrius.util.Documentos;
import br.ufpb.dsc.caladrius.whatsapp.MensagemRecebida;
import br.ufpb.dsc.caladrius.whatsapp.ProcessadorMensagemRecebida;
import br.ufpb.dsc.caladrius.whatsapp.StatusConexaoWhatsapp;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Webhook de recebimento de eventos do provedor WhatsApp (SPEC-10 §4.3).
 *
 * <p>Chamado servidor-a-servidor pela Evolution (rota {@code permitAll} e fora
 * do CSRF — ver {@code SecurityConfig}); a autenticação é o header
 * {@code X-Webhook-Token}, validado contra {@code WHATSAPP_WEBHOOK_TOKEN}
 * (RN-WPP-03). Este controller é o único ponto — junto do adaptador — que
 * conhece o payload da Evolution: daqui para dentro só circula a
 * {@link MensagemRecebida} normalizada.
 *
 * <p>Responde 200 imediatamente; o bot processa na própria requisição (volume
 * baixo), atrás da interface {@link ProcessadorMensagemRecebida} — trocar para
 * fila/assíncrono depois não muda este controller.
 */
@RestController
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final WhatsappService whatsappService;
    private final ProcessadorMensagemRecebida processador;
    private final String tokenEsperado;

    public WhatsappWebhookController(WhatsappService whatsappService,
                                     ProcessadorMensagemRecebida processador,
                                     @Value("${WHATSAPP_WEBHOOK_TOKEN:}") String tokenEsperado) {
        this.whatsappService = whatsappService;
        this.processador = processador;
        this.tokenEsperado = tokenEsperado;
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @RequestBody JsonNode payload) {
        // Sem token configurado no ambiente, o webhook não aceita nada (RN-WPP-03).
        if (!tokenValido(token)) {
            return ResponseEntity.status(403).build();
        }
        // A Evolution envia o evento como "messages.upsert"; a configuração usa
        // MESSAGES_UPSERT — normaliza para comparar.
        String evento = payload.path("event").asText("").toUpperCase().replace('.', '_');
        JsonNode dados = payload.path("data");
        switch (evento) {
            case "MESSAGES_UPSERT" -> tratarMensagem(dados);
            case "CONNECTION_UPDATE" -> tratarConexao(dados);
            case "QRCODE_UPDATED" -> tratarQr(dados);
            default -> { /* eventos não assinados são ignorados */ }
        }
        return ResponseEntity.ok().build();
    }

    // ------------------------------------------------------------ eventos

    private void tratarMensagem(JsonNode dados) {
        JsonNode chave = dados.path("key");
        String jid = chave.path("remoteJid").asText("");
        // Ignora grupos e mensagens enviadas pela própria conta (RN-WPP-04).
        if (jid.endsWith("@g.us") || chave.path("fromMe").asBoolean(false)) {
            return;
        }
        String texto = extrairTexto(dados.path("message"));
        if (!StringUtils.hasText(texto)) {
            return;
        }
        MensagemRecebida mensagem = new MensagemRecebida(
                chave.path("id").asText(null),
                Documentos.telefoneDeJid(jid),
                dados.path("pushName").asText(null),
                texto,
                Instant.now());
        // Idempotência (RN-WPP-03): evento repetido não volta ao bot.
        if (!whatsappService.registrarRecebida(mensagem)) {
            return;
        }
        try {
            processador.processar(mensagem);
        } catch (Exception e) {
            // A mensagem já está registrada; falha do bot não vira erro HTTP
            // (o provedor reenviaria um evento que seria descartado como repetido).
            log.error("Falha do bot ao processar mensagem de {}", mensagem.telefone(), e);
        }
    }

    private void tratarConexao(JsonNode dados) {
        StatusConexaoWhatsapp status = switch (dados.path("state").asText("")) {
            case "open" -> StatusConexaoWhatsapp.CONECTADO;
            case "connecting" -> StatusConexaoWhatsapp.AGUARDANDO_QR;
            default -> StatusConexaoWhatsapp.DESCONECTADO;
        };
        whatsappService.registrarEstadoConexao(status, null);
    }

    private void tratarQr(JsonNode dados) {
        String qr = dados.path("qrcode").path("base64").asText(dados.path("base64").asText(null));
        if (StringUtils.hasText(qr)) {
            whatsappService.registrarEstadoConexao(StatusConexaoWhatsapp.AGUARDANDO_QR, qr);
        }
    }

    // ------------------------------------------------------------ apoio

    /** Comparação em tempo constante; token ausente/errado ou não configurado → recusa. */
    private boolean tokenValido(String token) {
        if (!StringUtils.hasText(tokenEsperado) || !StringUtils.hasText(token)) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                tokenEsperado.getBytes(StandardCharsets.UTF_8));
    }

    /** O texto vem em {@code conversation} ou, em respostas/citações, {@code extendedTextMessage.text}. */
    private String extrairTexto(JsonNode mensagem) {
        String texto = mensagem.path("conversation").asText(null);
        if (!StringUtils.hasText(texto)) {
            texto = mensagem.path("extendedTextMessage").path("text").asText(null);
        }
        return texto;
    }
}
