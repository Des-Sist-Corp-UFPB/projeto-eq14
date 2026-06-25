package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Testes de integração HTTP do {@code HomeController} — painel inicial autenticado.
 */
@DisplayName("Web — HomeController")
class HomeControllerTest extends IntegracaoWebTestBase {

    /** O painel inicial renderiza para um usuário autenticado (GERENTE). */
    @Test
    @DisplayName("GET / renderiza o início para o GERENTE")
    void inicio_comoGerente_ok() throws Exception {
        mockMvc.perform(get("/").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(view().name("inicio"));
    }
}
