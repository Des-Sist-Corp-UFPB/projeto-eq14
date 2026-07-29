package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /** Busca via HTMX (fragmento próprio) e listagem paginada. */
    @Test
    @DisplayName("GET /cidades com HX-Request e /cidades/fragmento-tabela devolvem a tabela")
    void fragmentos() throws Exception {
        mockMvc.perform(get("/cidades").with(comoGerente()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/cidades/fragmento-tabela").param("busca", "patos")
                        .with(comoGerente()))
                .andExpect(status().isOk());
    }

    /** Formulário inválido (sem nome) devolve o modal, sem criar. */
    @Test
    @DisplayName("POST /cidades sem nome devolve o formulário com erro")
    void criar_invalido() throws Exception {
        mockMvc.perform(post("/cidades").with(comoGerente())
                        .param("nome", "")
                        .param("uf", "PB")
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk());
    }

    /** Edição: modal preenchido, atualização válida e id inexistente. */
    @Test
    @DisplayName("GET /cidades/{id}/editar + PUT /cidades/{id} atualizam a cidade")
    void editarEAtualizar() throws Exception {
        var cidade = cidadeDestino();

        mockMvc.perform(get("/cidades/" + cidade.getId() + "/editar").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(cidade.getNome())));

        mockMvc.perform(put("/cidades/" + cidade.getId()).with(comoGerente())
                        .param("nome", cidade.getNome())
                        .param("uf", cidade.getUf())
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/cidades/" + cidade.getId()).with(comoGerente())
                        .param("nome", "")
                        .param("uf", "PB")
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cidades/" + java.util.UUID.randomUUID() + "/editar").with(comoGerente()))
                .andExpect(status().isNotFound());
    }

    /** DT-01: excluir cidade remove em cascata as viagens; id inexistente devolve 404. */
    @Test
    @DisplayName("DELETE /cidades/{id} exclui; inexistente devolve 404")
    void excluir() throws Exception {
        mockMvc.perform(post("/cidades").with(comoGerente())
                        .param("nome", "Cidade Descartável")
                        .param("uf", "PB")
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk());

        var descartavel = cidadeRepository.findAll().stream()
                .filter(c -> "Cidade Descartável".equals(c.getNome()))
                .findFirst().orElseThrow();

        mockMvc.perform(delete("/cidades/" + descartavel.getId()).with(comoGerente()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/cidades/" + java.util.UUID.randomUUID()).with(comoGerente()))
                .andExpect(status().isNotFound());
    }
}
