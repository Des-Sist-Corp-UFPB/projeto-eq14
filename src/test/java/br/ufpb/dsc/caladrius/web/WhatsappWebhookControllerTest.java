package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.DirecaoMensagem;
import br.ufpb.dsc.caladrius.domain.enums.EtapaConversa;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.repository.ConversaBotRepository;
import br.ufpb.dsc.caladrius.repository.MensagemWhatsappRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do {@code POST /webhooks/whatsapp} (SPEC-10 §10):
 * token secreto (RN-WPP-03), mensagens de grupo/{@code fromMe} ignoradas
 * (RN-WPP-04), idempotência e a entrega ao bot (que responde e persiste a
 * conversa).
 */
@DisplayName("WhatsappWebhookController — Integração (SPEC-10)")
class WhatsappWebhookControllerTest extends IntegracaoWebTestBase {

    private static final String TOKEN = "token-de-teste"; // de application-test.yml

    @Autowired private MensagemWhatsappRepository mensagemRepository;
    @Autowired private ConversaBotRepository conversaRepository;

    private String payloadMensagem(String jid, boolean fromMe, String idProvedor, String texto) {
        return """
                {
                  "event": "messages.upsert",
                  "instance": "caladrius",
                  "data": {
                    "key": {"remoteJid": "%s", "fromMe": %s, "id": "%s"},
                    "pushName": "Contato Teste",
                    "message": {"conversation": "%s"}
                  }
                }
                """.formatted(jid, fromMe, idProvedor, texto);
    }

    private Usuario passageiroComTelefone(String telefone) {
        Usuario u = new Usuario();
        u.setNomeCompleto("Passageira Webhook");
        u.setTelefone(telefone);
        u.setStatus(StatusUsuario.ATIVO);
        u.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        return usuarioRepository.save(u);
    }

    @Test
    @DisplayName("RN-WPP-03: sem token → 403; token errado → 403 (nada é processado)")
    void tokenAusenteOuErrado() throws Exception {
        String payload = payloadMensagem("5583955550001@s.whatsapp.net", false, "WBK-403", "oi");

        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/webhooks/whatsapp")
                        .header("X-Webhook-Token", "token-errado")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        assertThat(mensagemRepository.existsByIdProvedorAndDirecao("WBK-403", DirecaoMensagem.RECEBIDA))
                .isFalse();
    }

    @Test
    @DisplayName("mensagem válida: 200, registra no log e o bot abre a conversa no MENU")
    void mensagemValidaChegaAoBot() throws Exception {
        passageiroComTelefone("83955550002");

        mockMvc.perform(post("/webhooks/whatsapp")
                        .header("X-Webhook-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadMensagem("5583955550002@s.whatsapp.net", false, "WBK-OK-1", "oi")))
                .andExpect(status().isOk());

        assertThat(mensagemRepository.existsByIdProvedorAndDirecao("WBK-OK-1", DirecaoMensagem.RECEBIDA))
                .isTrue();
        // O bot processou: a conversa foi criada e avançou para o MENU.
        assertThat(conversaRepository.findByTelefone("83955550002"))
                .hasValueSatisfying(c -> assertThat(c.getEtapa()).isEqualTo(EtapaConversa.MENU));
    }

    @Test
    @DisplayName("RN-WPP-03: evento repetido (mesmo id no provedor) é ignorado")
    void eventoRepetidoIgnorado() throws Exception {
        passageiroComTelefone("83955550003");
        String payload = payloadMensagem("5583955550003@s.whatsapp.net", false, "WBK-DUP", "oi");

        mockMvc.perform(post("/webhooks/whatsapp").header("X-Webhook-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mockMvc.perform(post("/webhooks/whatsapp").header("X-Webhook-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());

        long recebidas = mensagemRepository.findAll().stream()
                .filter(m -> "WBK-DUP".equals(m.getIdProvedor())).count();
        assertThat(recebidas).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-WPP-04: mensagens de grupo e fromMe são ignoradas")
    void grupoEFromMeIgnorados() throws Exception {
        mockMvc.perform(post("/webhooks/whatsapp").header("X-Webhook-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadMensagem("12036302222@g.us", false, "WBK-GRUPO", "oi")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/webhooks/whatsapp").header("X-Webhook-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadMensagem("5583955550004@s.whatsapp.net", true, "WBK-FROMME", "oi")))
                .andExpect(status().isOk());

        assertThat(mensagemRepository.existsByIdProvedorAndDirecao("WBK-GRUPO", DirecaoMensagem.RECEBIDA)).isFalse();
        assertThat(mensagemRepository.existsByIdProvedorAndDirecao("WBK-FROMME", DirecaoMensagem.RECEBIDA)).isFalse();
    }

    @Test
    @DisplayName("evento de conexão é aceito (atualiza o estado do painel) — 200")
    void eventoDeConexao() throws Exception {
        mockMvc.perform(post("/webhooks/whatsapp").header("X-Webhook-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "connection.update", "instance": "caladrius", "data": {"state": "open"}}
                                """))
                .andExpect(status().isOk());
    }
}
