package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP do fluxo de <strong>perfil incompleto</strong> do login
 * social (SPEC-08): o filtro que prende a navegação e a tela {@code /conta/completar}.
 */
@DisplayName("Web — Completar perfil (SPEC-08)")
class ContaCompletarTest extends IntegracaoWebTestBase {

    /** Conta de login social sem telefone vê a tela de completar cadastro. */
    @Test
    @DisplayName("GET /conta/completar renderiza para perfil incompleto")
    void completar_perfilIncompleto_renderiza() throws Exception {
        mockMvc.perform(get("/conta/completar").with(comoPassageiroIncompleto()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Telefone")));
    }

    /** Enquanto incompleto, qualquer rota de negócio é redirecionada para a tela de completar. */
    @Test
    @DisplayName("perfil incompleto em / é redirecionado para /conta/completar")
    void navegacao_perfilIncompleto_redireciona() throws Exception {
        mockMvc.perform(get("/").with(comoPassageiroIncompleto()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/conta/completar"));
    }

    /** Perfil já completo não vê a tela de completar — volta ao início. */
    @Test
    @DisplayName("GET /conta/completar com perfil completo redireciona para /")
    void completar_perfilCompleto_redirecionaInicio() throws Exception {
        mockMvc.perform(get("/conta/completar").with(comoGerente()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /** Informar um telefone válido grava o número e remove o marcador de perfil incompleto. */
    @Test
    @DisplayName("POST /conta/completar grava telefone e libera a conta")
    void completar_telefoneValido_liberaConta() throws Exception {
        Usuario novo = persistir("Google User", true, Papel.PASSAGEIRO);

        mockMvc.perform(post("/conta/completar").with(autenticar(novo)).with(csrf())
                        .param("telefone", "(83) 98123-4567"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Usuario recarregado = usuarioRepository.findById(novo.getId()).orElseThrow();
        assertThat(recarregado.isPerfilIncompleto()).isFalse();
        assertThat(recarregado.getTelefone()).isEqualTo("83981234567");
    }

    /** Telefone já usado por outra conta é rejeitado e mantém a tela de completar. */
    @Test
    @DisplayName("POST /conta/completar com telefone duplicado mantém a tela")
    void completar_telefoneDuplicado_mantemTela() throws Exception {
        Usuario existente = persistir("Já Cadastrado", false, Papel.PASSAGEIRO);
        Usuario novo = persistir("Google User 2", true, Papel.PASSAGEIRO);

        mockMvc.perform(post("/conta/completar").with(autenticar(novo)).with(csrf())
                        .param("telefone", existente.getTelefone()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("já cadastrado")));

        Usuario recarregado = usuarioRepository.findById(novo.getId()).orElseThrow();
        assertThat(recarregado.isPerfilIncompleto()).isTrue();
    }
}
