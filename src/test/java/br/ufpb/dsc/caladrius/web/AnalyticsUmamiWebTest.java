package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do <strong>analytics de uso (Umami)</strong> — SPEC-17 §9.
 *
 * <p>Sobe o contexto <em>com</em> as variáveis do Umami definidas (o oposto de
 * {@link PaginasPublicasTest}) e verifica as três garantias que sustentam a entrega:
 * o rastreador aparece nas telas medidas, vem com as proteções de privacidade, e
 * <strong>não</strong> aparece nas telas cujo endereço carrega token (RN-ANL-03).
 */
@TestPropertySource(properties = {
        "UMAMI_URL=https://umami.dsc.rodrigor.com",
        "UMAMI_WEBSITE_ID=18d3d5e0-9a19-426f-a74e-72f5c837fc74",
        "UMAMI_DOMINIOS=eq14.dsc.rodrigor.com"
})
@DisplayName("Web — Analytics de uso (Umami / SPEC-17)")
class AnalyticsUmamiWebTest extends IntegracaoWebTestBase {

    private static final String SCRIPT = "https://umami.dsc.rodrigor.com/script.js";

    // ===================== Onde deve aparecer =====================

    /** O funil de entrada é anônimo — se o rastreador só existisse no layout logado, ele se perderia. */
    @Test
    @DisplayName("tela pública de login carrega o rastreador")
    void login_carregaRastreador() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(SCRIPT)))
                .andExpect(content().string(containsString(
                        "data-website-id=\"18d3d5e0-9a19-426f-a74e-72f5c837fc74\"")));
    }

    /** O layout autenticado cobre todas as telas internas de uma vez. */
    @Test
    @DisplayName("layout autenticado carrega o rastreador")
    void layoutAutenticado_carregaRastreador() throws Exception {
        mockMvc.perform(get("/").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(SCRIPT)));
    }

    // ===================== Proteções =====================

    /**
     * RN-ANL-03 (1ª camada): a query string não é enviada. Vale para qualquer URL com
     * segredo, inclusive as que ainda não existem.
     */
    @Test
    @DisplayName("o rastreador não envia a query string (data-exclude-search)")
    void rastreador_naoEnviaQueryString() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("data-exclude-search=\"true\"")));
    }

    /**
     * RN-ANL-02: sem {@code data-domains} qualquer ambiente com as variáveis preenchidas
     * contaminaria a estatística — o Umami aceita eventos pelo website-id e não filtra a
     * origem por conta própria.
     */
    @Test
    @DisplayName("o rastreador restringe a coleta ao domínio de produção")
    void rastreador_restringePorDominio() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("data-domains=\"eq14.dsc.rodrigor.com\"")));
    }

    /**
     * RN-ANL-03 (2ª camada) — o ponto mais importante desta suíte. {@code /ativar?token=…}
     * define a senha da conta: se o rastreador estivesse nesta página e a instância do Umami
     * ignorasse o {@code data-exclude-search}, um token válido iria para o servidor de
     * analytics. A ausência aqui é deliberada e este teste existe para que continue assim.
     */
    @Test
    @DisplayName("/ativar NÃO carrega o rastreador (token na URL)")
    void ativar_naoCarregaRastreador() throws Exception {
        mockMvc.perform(get("/ativar").param("token", "token-secreto-de-ativacao"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("script.js"))));
    }

    /** Mesma proteção para a confirmação de e-mail, que também recebe token pela URL. */
    @Test
    @DisplayName("/verificar-email NÃO carrega o rastreador (token na URL)")
    void verificarEmail_naoCarregaRastreador() throws Exception {
        mockMvc.perform(get("/verificar-email").param("token", "token-secreto-de-verificacao"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("script.js"))));
    }
}
