package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP das páginas/endpoints públicos (sem autenticação) —
 * health check e telas de entrada.
 *
 * <p>Esta classe roda <strong>sem</strong> as variáveis do Umami, então é também onde se
 * verifica o estado padrão do analytics: desligado, sem script (SPEC-17, RN-ANL-01). O
 * estado ligado fica em {@link AnalyticsUmamiWebTest}.
 */
@DisplayName("Web — Páginas públicas")
class PaginasPublicasTest extends IntegracaoWebTestBase {

    // ===================== Health check (SPEC-17) =====================

    /** Contrato público da disciplina: /ping responde 200 com JSON. */
    @Test
    @DisplayName("GET /ping responde 200 (contrato público)")
    void ping_respondeOk() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk());
    }

    /**
     * O contrato original não pode regredir com a inclusão do healthcheck de banco:
     * os três campos históricos continuam presentes.
     */
    @Test
    @DisplayName("GET /ping preserva o contrato (status, service, timestamp)")
    void ping_preservaContrato() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("eq14"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    /**
     * SPEC-17: o campo vem de um {@code SELECT} real contra o PostgreSQL do Testcontainers —
     * é o caminho feliz do {@code BancoHealthIndicator}, que o teste unitário não cobre.
     */
    @Test
    @DisplayName("GET /ping reporta o banco acessível (database=up)")
    void ping_reportaBancoAcessivel() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("up"));
    }

    /** Health check do Actuator é público. */
    @Test
    @DisplayName("GET /actuator/health responde 200")
    void health_respondeOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * RN-HC-05: o componente do banco é visível a um probe <strong>anônimo</strong>
     * ({@code show-components: always}). Sem isso, quem consulta de fora veria apenas
     * {@code {"status":"UP"}} e não teria como constatar que o banco é verificado.
     */
    @Test
    @DisplayName("GET /actuator/health expõe o componente 'banco' a anônimo")
    void health_exibeComponenteBanco() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.banco.status").value("UP"));
    }

    // ===================== Telas públicas =====================

    /** A tela de login é pública e renderiza para usuários anônimos. */
    @Test
    @DisplayName("GET /login renderiza para anônimo")
    void login_renderizaParaAnonimo() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entrar")));
    }

    /** A tela de cadastro público (auto-cadastro de passageiro) é acessível. */
    @Test
    @DisplayName("GET /registrar renderiza para anônimo")
    void registrar_renderizaParaAnonimo() throws Exception {
        mockMvc.perform(get("/registrar"))
                .andExpect(status().isOk());
    }

    // ===================== Analytics desligado (SPEC-17) =====================

    /**
     * RN-ANL-01: sem {@code UMAMI_URL}/{@code UMAMI_WEBSITE_ID} nenhum script é renderizado.
     * É o que mantém o desenvolvimento e a própria suíte de testes fora da estatística.
     */
    @Test
    @DisplayName("sem as variáveis do Umami, nenhuma página carrega o rastreador")
    void semConfiguracao_naoRenderizaRastreador() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("script.js"))));
    }
}
