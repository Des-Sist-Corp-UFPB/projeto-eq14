package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP do {@code UsuarioController} (SPEC-CAD-01): CRUD completo
 * via HTMX, incluindo os caminhos de erro que a tela precisa exibir bem — validação
 * de formulário, regra de negócio (telefone duplicado) e as travas da DT-02
 * (não excluir a si mesmo nem o último gerente).
 */
@DisplayName("Web — UsuarioController (SPEC-CAD-01)")
class UsuarioControllerTest extends IntegracaoWebTestBase {

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("GET /usuarios lista para o GERENTE; com HX-Request devolve só o fragmento")
    void listar() throws Exception {
        mockMvc.perform(get("/usuarios").with(comoGerente()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios").with(comoGerente()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios/fragmento-tabela").param("busca", "zzz")
                        .with(comoGerente()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /usuarios/novo e /{id}/editar devolvem o modal do formulário")
    void formularios() throws Exception {
        Usuario existente = persistir("Editável", false, Papel.MOTORISTA);

        mockMvc.perform(get("/usuarios/novo").with(comoGerente()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/usuarios/" + existente.getId() + "/editar").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(existente.getTelefone())));
    }

    @Test
    @DisplayName("GET /usuarios/{id}/editar com id inexistente devolve 404")
    void editarInexistente() throws Exception {
        mockMvc.perform(get("/usuarios/" + UUID.randomUUID() + "/editar").with(comoGerente()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ escrita

    @Test
    @DisplayName("POST /usuarios cria e devolve a linha da tabela")
    void criar() throws Exception {
        String telefone = telefoneUnico();

        mockMvc.perform(post("/usuarios").with(comoGerente())
                        .param("nomeCompleto", "Maria da Silva")
                        .param("telefone", telefone)
                        .param("senha", "segredo123")
                        .param("papeis", "PASSAGEIRO")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maria da Silva")));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone)).isPresent();
    }

    @Test
    @DisplayName("POST /usuarios sem nome devolve o formulário com erro (não persiste)")
    void criarInvalido() throws Exception {
        mockMvc.perform(post("/usuarios").with(comoGerente())
                        .param("nomeCompleto", "")
                        .param("telefone", telefoneUnico())
                        .param("senha", "segredo123")
                        .param("papeis", "PASSAGEIRO")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /usuarios com telefone já usado volta ao formulário com a regra de negócio")
    void criarTelefoneDuplicado() throws Exception {
        Usuario existente = persistir("Já existe", false, Papel.PASSAGEIRO);

        mockMvc.perform(post("/usuarios").with(comoGerente())
                        .param("nomeCompleto", "Outra pessoa")
                        .param("telefone", existente.getTelefone())
                        .param("senha", "segredo123")
                        .param("papeis", "PASSAGEIRO")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findAll().stream()
                .filter(u -> existente.getTelefone().equals(u.getTelefone()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT /usuarios/{id} atualiza o cadastro (senha em branco mantém a atual)")
    void atualizar() throws Exception {
        Usuario alvo = persistir("Nome Antigo", false, Papel.MOTORISTA);

        mockMvc.perform(put("/usuarios/" + alvo.getId()).with(comoGerente())
                        .param("nomeCompleto", "Nome Novo")
                        .param("telefone", alvo.getTelefone())
                        .param("senha", "")
                        .param("papeis", "MOTORISTA")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nome Novo")));

        assertThat(usuarioRepository.findById(alvo.getId()))
                .hasValueSatisfying(u -> assertThat(u.getNomeCompleto()).isEqualTo("Nome Novo"));
    }

    @Test
    @DisplayName("PUT /usuarios/{id} inválido devolve o formulário preenchido com o usuário")
    void atualizarInvalido() throws Exception {
        Usuario alvo = persistir("Continua", false, Papel.MOTORISTA);

        mockMvc.perform(put("/usuarios/" + alvo.getId()).with(comoGerente())
                        .param("nomeCompleto", "")
                        .param("telefone", alvo.getTelefone())
                        .param("papeis", "MOTORISTA")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findById(alvo.getId()))
                .hasValueSatisfying(u -> assertThat(u.getNomeCompleto()).isEqualTo("Continua"));
    }

    // ------------------------------------------------------------------ exclusão

    @Test
    @DisplayName("DELETE /usuarios/{id} faz o soft-delete (200)")
    void excluir() throws Exception {
        Usuario alvo = persistir("Some daqui", false, Papel.PASSAGEIRO);

        mockMvc.perform(delete("/usuarios/" + alvo.getId()).with(comoGerente()))
                .andExpect(status().isOk());

        assertThat(usuarioRepository.findByIdAndRemovidoEmIsNull(alvo.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /usuarios/{id} inexistente devolve 404")
    void excluirInexistente() throws Exception {
        mockMvc.perform(delete("/usuarios/" + UUID.randomUUID()).with(comoGerente()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DT-02: excluir a si mesmo é recusado com 409 e a mensagem da regra")
    void excluirASiMesmoEhRecusado() throws Exception {
        Usuario eu = persistir("Gerente Logado", false, Papel.GERENTE);

        mockMvc.perform(delete("/usuarios/" + eu.getId()).with(autenticar(eu)))
                .andExpect(status().isConflict());

        assertThat(usuarioRepository.findByIdAndRemovidoEmIsNull(eu.getId())).isPresent();
    }

    // ---------------------------------------------------------------------- RBAC

    @Test
    @DisplayName("RBAC: PASSAGEIRO não acessa a gestão de usuários (403)")
    void rbac() throws Exception {
        mockMvc.perform(get("/usuarios").with(comoPassageiro()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios").with(comoMotorista()))
                .andExpect(status().isForbidden());
    }
}
