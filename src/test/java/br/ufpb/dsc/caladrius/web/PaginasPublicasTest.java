package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP das páginas/endpoints públicos (sem autenticação) —
 * health check e telas de entrada.
 */
@DisplayName("Web — Páginas públicas")
class PaginasPublicasTest extends IntegracaoWebTestBase {

    /** Contrato público da disciplina: /ping responde 200 com JSON. */
    @Test
    @DisplayName("GET /ping responde 200 (contrato público)")
    void ping_respondeOk() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk());
    }

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

    /** Health check do Actuator é público. */
    @Test
    @DisplayName("GET /actuator/health responde 200")
    void health_respondeOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
