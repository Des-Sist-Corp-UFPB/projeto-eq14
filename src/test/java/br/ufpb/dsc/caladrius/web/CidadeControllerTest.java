package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP do {@code CidadeController} — listagem e criação.
 */
@DisplayName("Web — CidadeController")
class CidadeControllerTest extends IntegracaoWebTestBase {

    /** Listagem renderiza para o GERENTE. */
    @Test
    @DisplayName("GET /cidades lista para o GERENTE (200)")
    void listar_comoGerente_ok() throws Exception {
        mockMvc.perform(get("/cidades").with(comoGerente()))
                .andExpect(status().isOk());
    }

    /** Modal de nova cidade é servido como fragmento. */
    @Test
    @DisplayName("GET /cidades/nova devolve o modal do formulário")
    void novaForm_devolveModal() throws Exception {
        mockMvc.perform(get("/cidades/nova").with(comoGerente()))
                .andExpect(status().isOk());
    }

    /** Criação válida persiste e devolve a linha com o nome. */
    @Test
    @DisplayName("POST /cidades cria a cidade e devolve a linha")
    void criar_valido_persiste() throws Exception {
        mockMvc.perform(post("/cidades").with(comoGerente())
                        .param("nome", "Cajazeiras")
                        .param("uf", "PB")
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cajazeiras")));
    }
}
