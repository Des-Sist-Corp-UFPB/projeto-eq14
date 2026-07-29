package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP do {@code LinhaController} (SPEC-06): o CRUD das linhas
 * programadas — o "template" recorrente de onde saem as viagens rotineiras — mais o
 * liga/desliga da linha e a trava de exclusão.
 */
@DisplayName("Web — LinhaController (SPEC-06)")
class LinhaControllerTest extends IntegracaoWebTestBase {

    @Autowired private LinhaProgramadaRepository linhaRepository;

    /** Cria uma linha pelo próprio endpoint e devolve a última persistida. */
    private LinhaProgramada criarLinha(String saida, String chegada) throws Exception {
        mockMvc.perform(post("/linhas").with(comoGerente()).with(csrf())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("horarioSaida", saida)
                        .param("horarioChegada", chegada)
                        .param("dias", "SEGUNDA", "QUARTA")
                        .param("ativa", "true"))
                .andExpect(status().is3xxRedirection());
        return linhaRepository.findAll().stream()
                .filter(l -> l.getHorarioSaida().toString().startsWith(saida))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("GET /linhas e /linhas/nova renderizam para o GERENTE")
    void telas() throws Exception {
        mockMvc.perform(get("/linhas").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/linhas/nova").with(comoGerente())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /linhas/{id}/editar carrega a linha existente")
    void editarForm() throws Exception {
        LinhaProgramada linha = criarLinha("05:10", "07:10");

        mockMvc.perform(get("/linhas/" + linha.getId() + "/editar").with(comoGerente()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /linhas/{id}/editar de linha inexistente devolve 404")
    void editarInexistente() throws Exception {
        mockMvc.perform(get("/linhas/" + UUID.randomUUID() + "/editar").with(comoGerente()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ escrita

    @Test
    @DisplayName("POST /linhas cria a linha com os dias marcados e redireciona")
    void criar() throws Exception {
        LinhaProgramada linha = criarLinha("05:20", "07:20");

        assertThat(linha.getDias()).hasSize(2);
        assertThat(linha.isAtiva()).isTrue();
    }

    @Test
    @DisplayName("POST /linhas sem destino devolve o formulário (validação), sem criar")
    void criarInvalido() throws Exception {
        long antes = linhaRepository.count();

        mockMvc.perform(post("/linhas").with(comoGerente()).with(csrf())
                        .param("horarioSaida", "05:30")
                        .param("horarioChegada", "07:30"))
                .andExpect(status().isOk());

        assertThat(linhaRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("POST /linhas com chegada antes da saída volta com o erro de regra de negócio")
    void criarComHorarioInvertido() throws Exception {
        mockMvc.perform(post("/linhas").with(comoGerente()).with(csrf())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("horarioSaida", "09:00")
                        .param("horarioChegada", "08:00")
                        .param("dias", "SEGUNDA"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /linhas/{id}/editar atualiza o horário da linha")
    void atualizar() throws Exception {
        LinhaProgramada linha = criarLinha("05:40", "07:40");

        mockMvc.perform(post("/linhas/" + linha.getId() + "/editar").with(comoGerente()).with(csrf())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("horarioSaida", "06:45")
                        .param("horarioChegada", "08:45")
                        .param("dias", "SEXTA")
                        .param("ativa", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(linhaRepository.findById(linha.getId()))
                .hasValueSatisfying(l -> assertThat(l.getHorarioSaida().toString()).startsWith("06:45"));
    }

    @Test
    @DisplayName("POST /linhas/{id}/alternar desativa e reativa a linha")
    void alternar() throws Exception {
        LinhaProgramada linha = criarLinha("05:50", "07:50");

        mockMvc.perform(post("/linhas/" + linha.getId() + "/alternar").with(comoGerente()).with(csrf()))
                .andExpect(redirectedUrl("/linhas"));
        assertThat(linhaRepository.findById(linha.getId()))
                .hasValueSatisfying(l -> assertThat(l.isAtiva()).isFalse());

        mockMvc.perform(post("/linhas/" + linha.getId() + "/alternar").with(comoGerente()).with(csrf()));
        assertThat(linhaRepository.findById(linha.getId()))
                .hasValueSatisfying(l -> assertThat(l.isAtiva()).isTrue());
    }

    @Test
    @DisplayName("POST /linhas/{id}/excluir remove a linha sem viagens e avisa em flash")
    void excluir() throws Exception {
        LinhaProgramada linha = criarLinha("04:15", "06:15");

        mockMvc.perform(post("/linhas/" + linha.getId() + "/excluir").with(comoGerente()).with(csrf()))
                .andExpect(redirectedUrl("/linhas"))
                .andExpect(flash().attributeExists("sucesso"));

        assertThat(linhaRepository.findById(linha.getId())).isEmpty();
    }

    // ---------------------------------------------------------------------- RBAC

    @Test
    @DisplayName("RBAC: só o GERENTE administra linhas (403 para os demais)")
    void rbac() throws Exception {
        mockMvc.perform(get("/linhas").with(comoPassageiro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/linhas").with(comoMotorista())).andExpect(status().isForbidden());
    }
}
