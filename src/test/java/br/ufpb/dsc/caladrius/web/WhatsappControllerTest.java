package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do painel {@code /whatsapp} (SPEC-10 §10): RBAC
 * (exclusivo do GERENTE — RN-WPP-09) e comportamento sem a integração
 * configurada (RN-WPP-02).
 */
@DisplayName("WhatsappController — Integração (SPEC-10)")
class WhatsappControllerTest extends IntegracaoWebTestBase {

    @Test
    @DisplayName("RN-WPP-09: GERENTE acessa o painel; demais papéis recebem 403; anônimo vai ao login")
    void rbacDoPainel() throws Exception {
        mockMvc.perform(get("/whatsapp").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/whatsapp").with(comoPassageiro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/whatsapp").with(comoMotorista())).andExpect(status().isForbidden());
        mockMvc.perform(get("/whatsapp").with(comoSysadmin())).andExpect(status().isForbidden());
        mockMvc.perform(get("/whatsapp")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("RN-WPP-02: sem a integração configurada, o painel informa e não quebra")
    void painelSemIntegracao() throws Exception {
        mockMvc.perform(get("/whatsapp").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Integração não configurada")));
    }

    @Test
    @DisplayName("fragmento de status (polling HTMX) responde para o GERENTE")
    void fragmentoDeStatus() throws Exception {
        mockMvc.perform(get("/whatsapp/status").with(comoGerente()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RN-WPP-02: teste de envio sem integração → flash de erro (fluxo não quebra)")
    void testeDeEnvioSemIntegracao() throws Exception {
        mockMvc.perform(post("/whatsapp/teste").with(comoGerente()).with(csrf())
                        .param("telefone", "(83) 95555-0009")
                        .param("texto", "olá"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/whatsapp"))
                .andExpect(flash().attributeExists("erro"));
    }
}
