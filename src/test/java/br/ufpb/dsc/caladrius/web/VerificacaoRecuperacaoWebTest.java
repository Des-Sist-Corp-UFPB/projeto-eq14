package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP das telas de verificação/recuperação (SPEC-ACE-03):
 * rotas públicas, anti-enumeração e redirecionamentos.
 */
@DisplayName("Web — Verificação de contato e recuperação de senha (SPEC-ACE-03)")
class VerificacaoRecuperacaoWebTest extends IntegracaoWebTestBase {

    @Test
    @DisplayName("GET das telas públicas renderiza para anônimo")
    void telasPublicas_renderizam() throws Exception {
        mockMvc.perform(get("/esqueci-senha")).andExpect(status().isOk());
        mockMvc.perform(get("/redefinir-senha")).andExpect(status().isOk());
        mockMvc.perform(get("/verificar-telefone")).andExpect(status().isOk());
        mockMvc.perform(get("/verificar-email").param("token", "qualquer")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /esqueci-senha com identificador desconhecido redireciona (anti-enumeração, sem erro)")
    void esqueciSenha_desconhecido_redirecionaGenerico() throws Exception {
        mockMvc.perform(post("/esqueci-senha")
                        .param("metodo", "TELEFONE")
                        .param("valor", "83900000000")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/redefinir-senha"));
    }

    @Test
    @DisplayName("POST /verificar-telefone com telefone desconhecido volta com erro")
    void verificarTelefone_desconhecido_voltaComErro() throws Exception {
        mockMvc.perform(post("/verificar-telefone")
                        .param("telefone", "83900000000")
                        .param("codigo", "123456")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verificar-telefone"));
    }

    @Test
    @DisplayName("POST sem CSRF é barrado (403)")
    void postSemCsrf_barrado() throws Exception {
        mockMvc.perform(post("/esqueci-senha")
                        .param("metodo", "TELEFONE")
                        .param("valor", "83900000000"))
                .andExpect(status().isForbidden());
    }
}
