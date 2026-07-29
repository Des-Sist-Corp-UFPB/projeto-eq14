package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Testes de integração HTTP do {@code AuthController} (SPEC-01/07/12): a página de
 * login e o auto-cadastro público de passageiro, incluindo o endereço opcional e os
 * dois caminhos de erro — formato (Bean Validation) e regra de negócio (telefone/CPF).
 */
@DisplayName("Web — AuthController (SPEC-01)")
class AuthControllerTest extends IntegracaoWebTestBase {

    @Test
    @DisplayName("GET /login e GET /registrar são públicos")
    void telasPublicas() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        mockMvc.perform(get("/registrar")).andExpect(status().isOk())
                .andExpect(model().attributeExists("registroForm", "municipios"));
    }

    @Test
    @DisplayName("POST /registrar cria o passageiro e leva ao login")
    void cadastroValido() throws Exception {
        String telefone = telefoneUnico();

        mockMvc.perform(post("/registrar").with(csrf())
                        .param("nomeCompleto", "Joana Passageira")
                        .param("telefone", telefone)
                        .param("senha", "segredo123")
                        .param("bairro", "Centro"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?cadastro"));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone))
                .hasValueSatisfying(u -> {
                    assertThat(u.temPapel(Papel.PASSAGEIRO)).isTrue();
                    assertThat(u.getStatus()).isEqualTo(StatusUsuario.ATIVO);
                });
    }

    @Test
    @DisplayName("POST /registrar com senha curta reexibe o formulário (Bean Validation)")
    void cadastroComSenhaCurta() throws Exception {
        String telefone = telefoneUnico();

        mockMvc.perform(post("/registrar").with(csrf())
                        .param("nomeCompleto", "Senha Curta")
                        .param("telefone", telefone)
                        .param("senha", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/registro"))
                .andExpect(model().attributeHasFieldErrors("registroForm", "senha"));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone)).isEmpty();
    }

    @Test
    @DisplayName("POST /registrar com telefone já cadastrado reexibe o formulário com o erro da regra")
    void cadastroComTelefoneRepetido() throws Exception {
        Usuario existente = persistir("Titular do telefone", false, Papel.PASSAGEIRO);

        mockMvc.perform(post("/registrar").with(csrf())
                        .param("nomeCompleto", "Tentando de novo")
                        .param("telefone", existente.getTelefone())
                        .param("senha", "segredo123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/registro"))
                .andExpect(model().attributeHasErrors("registroForm"));
    }

    @Test
    @DisplayName("POST /registrar com CPF inválido reexibe o formulário (regra de negócio)")
    void cadastroComCpfInvalido() throws Exception {
        String telefone = telefoneUnico();

        mockMvc.perform(post("/registrar").with(csrf())
                        .param("nomeCompleto", "CPF Torto")
                        .param("telefone", telefone)
                        .param("cpf", "11111111111")
                        .param("senha", "segredo123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/registro"));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone)).isEmpty();
    }
}
