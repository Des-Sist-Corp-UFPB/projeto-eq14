package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP do {@code VeiculoController} — listagem, fragmento de
 * formulário e criação (que percorre controller → service → repositório reais).
 */
@DisplayName("Web — VeiculoController")
class VeiculoControllerTest extends IntegracaoWebTestBase {

    /** Página de listagem renderiza para o GERENTE. */
    @Test
    @DisplayName("GET /veiculos lista para o GERENTE (200)")
    void listar_comoGerente_ok() throws Exception {
        mockMvc.perform(get("/veiculos").with(comoGerente()))
                .andExpect(status().isOk());
    }

    /** Requisição HTMX devolve apenas o fragmento da tabela. */
    @Test
    @DisplayName("GET /veiculos com HX-Request devolve fragmento da tabela")
    void listar_htmx_devolveFragmento() throws Exception {
        mockMvc.perform(get("/veiculos").with(comoGerente()).header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    /** O modal de novo veículo é servido como fragmento. */
    @Test
    @DisplayName("GET /veiculos/novo devolve o modal do formulário")
    void novoForm_devolveModal() throws Exception {
        mockMvc.perform(get("/veiculos/novo").with(comoGerente()))
                .andExpect(status().isOk());
    }

    /** Criação válida persiste e devolve o fragmento da linha com a placa. */
    @Test
    @DisplayName("POST /veiculos cria o veículo e devolve a linha")
    void criar_valido_persiste() throws Exception {
        mockMvc.perform(post("/veiculos").with(comoGerente())
                        .param("placa", "ABC1D23")
                        .param("marca", "Mercedes")
                        .param("modelo", "Sprinter")
                        .param("ano", "2022")
                        .param("tipo", "VAN")
                        .param("capacidade", "15")
                        .param("possuiAcessibilidade", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ABC1D23")));
    }

    /** Formulário inválido (placa curta) volta o modal com erro, sem persistir. */
    @Test
    @DisplayName("POST /veiculos com placa inválida devolve o formulário (sem criar)")
    void criar_invalido_devolveForm() throws Exception {
        mockMvc.perform(post("/veiculos").with(comoGerente())
                        .param("placa", "X")
                        .param("marca", "Fiat")
                        .param("modelo", "Ducato")
                        .param("ano", "2020")
                        .param("tipo", "VAN")
                        .param("capacidade", "10"))
                .andExpect(status().isOk());
    }

    /** PASSAGEIRO não pode criar veículo (RBAC). */
    @Test
    @DisplayName("POST /veiculos como PASSAGEIRO é proibido (403)")
    void criar_comoPassageiro_proibido() throws Exception {
        mockMvc.perform(post("/veiculos").with(comoPassageiro())
                        .param("placa", "DEF4G56").param("marca", "VW").param("modelo", "Kombi")
                        .param("ano", "2015").param("tipo", "VAN").param("capacidade", "9"))
                .andExpect(status().isForbidden());
    }
}
