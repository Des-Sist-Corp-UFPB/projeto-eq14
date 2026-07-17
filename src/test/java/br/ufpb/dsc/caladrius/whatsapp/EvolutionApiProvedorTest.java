package br.ufpb.dsc.caladrius.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testes do adaptador {@link EvolutionApiProvedor} (SPEC-10 §10) com
 * {@link MockRestServiceServer}: mapeamento dos endpoints da Evolution v2,
 * header {@code apikey}, tradução dos estados e falha HTTP → exceção da porta.
 */
@DisplayName("EvolutionApiProvedor — Testes Unitários (SPEC-10)")
class EvolutionApiProvedorTest {

    private static final String BASE = "https://evo.teste";

    private MockRestServiceServer servidor;
    private EvolutionApiProvedor provedor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader("apikey", "chave-teste");
        servidor = MockRestServiceServer.bindTo(builder).build();
        provedor = new EvolutionApiProvedor(builder.build(), "caladrius",
                "segredo-webhook", "https://app.teste");
    }

    // ------------------------------------------------------------- status

    @Test
    @DisplayName("statusConexao: envia a apikey e traduz open → CONECTADO")
    void statusConexao_open() {
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "chave-teste"))
                .andRespond(withSuccess("{\"instance\":{\"state\":\"open\"}}", MediaType.APPLICATION_JSON));

        assertThat(provedor.statusConexao()).isEqualTo(StatusConexaoWhatsapp.CONECTADO);
        servidor.verify();
    }

    @Test
    @DisplayName("statusConexao: connecting → AGUARDANDO_QR e close → DESCONECTADO")
    void statusConexao_demaisEstados() {
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andRespond(withSuccess("{\"instance\":{\"state\":\"connecting\"}}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andRespond(withSuccess("{\"instance\":{\"state\":\"close\"}}", MediaType.APPLICATION_JSON));

        assertThat(provedor.statusConexao()).isEqualTo(StatusConexaoWhatsapp.AGUARDANDO_QR);
        assertThat(provedor.statusConexao()).isEqualTo(StatusConexaoWhatsapp.DESCONECTADO);
    }

    @Test
    @DisplayName("statusConexao: instância inexistente (404) → DESCONECTADO, sem exceção")
    void statusConexao_instanciaInexistente() {
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(provedor.statusConexao()).isEqualTo(StatusConexaoWhatsapp.DESCONECTADO);
    }

    @Test
    @DisplayName("statusConexao: erro 5xx da Evolution → WhatsappException (exceção da porta)")
    void statusConexao_falhaHttp() {
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provedor.statusConexao()).isInstanceOf(WhatsappException.class);
    }

    // ------------------------------------------------------------- envio

    @Test
    @DisplayName("enviarTexto: POST sendText com o número prefixado pelo DDI 55")
    void enviarTexto() {
        servidor.expect(requestTo(BASE + "/message/sendText/caladrius"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "chave-teste"))
                .andExpect(jsonPath("$.number").value("5583999999999"))
                .andExpect(jsonPath("$.text").value("Olá!"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provedor.enviarTexto("83999999999", "Olá!");
        servidor.verify();
    }

    @Test
    @DisplayName("enviarTexto: falha HTTP → WhatsappException")
    void enviarTexto_falha() {
        servidor.expect(requestTo(BASE + "/message/sendText/caladrius"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provedor.enviarTexto("83999999999", "Olá!"))
                .isInstanceOf(WhatsappException.class);
    }

    // ------------------------------------------------------------- conexão

    @Test
    @DisplayName("iniciarConexao: desconectada → cria instância, registra webhook (com token) e devolve o QR")
    void iniciarConexao_fluxoCompleto() {
        servidor.expect(requestTo(BASE + "/instance/connectionState/caladrius"))
                .andRespond(withSuccess("{\"instance\":{\"state\":\"close\"}}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/instance/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.instanceName").value("caladrius"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/webhook/set/caladrius"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.webhook.url").value("https://app.teste/webhooks/whatsapp"))
                .andExpect(jsonPath("$.webhook.headers['X-Webhook-Token']").value("segredo-webhook"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/instance/connect/caladrius"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"base64\":\"data:image/png;base64,QR\"}", MediaType.APPLICATION_JSON));

        ConexaoWhatsapp conexao = provedor.iniciarConexao();

        assertThat(conexao.status()).isEqualTo(StatusConexaoWhatsapp.AGUARDANDO_QR);
        assertThat(conexao.qrCodeBase64()).isEqualTo("data:image/png;base64,QR");
        servidor.verify();
    }

    @Test
    @DisplayName("desconectar: DELETE logout da instância")
    void desconectar() {
        servidor.expect(requestTo(BASE + "/instance/logout/caladrius"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provedor.desconectar();
        servidor.verify();
    }

    @Test
    @DisplayName("contaConectada: acha a instância na lista e normaliza o número do JID")
    void contaConectada() {
        servidor.expect(requestTo(BASE + "/instance/fetchInstances?instanceName=caladrius"))
                .andRespond(withSuccess("""
                        [{"name":"caladrius","ownerJid":"5583999999999@s.whatsapp.net","profileName":"Secretaria de Saúde"}]
                        """, MediaType.APPLICATION_JSON));

        Optional<ContaWhatsapp> conta = provedor.contaConectada();

        assertThat(conta).isPresent();
        assertThat(conta.get().numero()).isEqualTo("83999999999");
        assertThat(conta.get().nomePerfil()).isEqualTo("Secretaria de Saúde");
    }

    @Test
    @DisplayName("contaConectada: sem pareamento (sem ownerJid) → vazio")
    void contaConectada_semPareamento() {
        servidor.expect(requestTo(BASE + "/instance/fetchInstances?instanceName=caladrius"))
                .andRespond(withSuccess("[{\"name\":\"caladrius\"}]", MediaType.APPLICATION_JSON));

        assertThat(provedor.contaConectada()).isEmpty();
    }
}
