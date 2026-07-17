package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do painel de avaliação sob demanda (SPEC-11): RBAC
 * (exclusivo do GERENTE) e as ações de aprovar/recusar com validação.
 */
@DisplayName("GestaoSolicitacaoController — Integração (SPEC-11)")
class GestaoSolicitacaoControllerTest extends IntegracaoWebTestBase {

    @Test
    @DisplayName("RBAC: GERENTE acessa o painel; demais papéis 403; anônimo redireciona")
    void rbac() throws Exception {
        mockMvc.perform(get("/gestao/solicitacoes").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/gestao/solicitacoes").with(comoPassageiro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/gestao/solicitacoes").with(comoMotorista())).andExpect(status().isForbidden());
        mockMvc.perform(get("/gestao/solicitacoes")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("recusar sem motivo válido → flash de erro (regra de negócio)")
    void recusarSemMotivo() throws Exception {
        mockMvc.perform(post("/gestao/solicitacoes/{id}/recusar", UUID.randomUUID())
                        .with(comoGerente()).with(csrf())
                        .param("motivo", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gestao/solicitacoes"))
                .andExpect(flash().attributeExists("erro"));
    }
}
