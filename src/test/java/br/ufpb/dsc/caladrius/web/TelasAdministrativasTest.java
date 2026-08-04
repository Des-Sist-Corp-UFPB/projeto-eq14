package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.service.ConfiguracaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP das telas de administração e de conta: painel do
 * SYSADMIN, configuração dinâmica de sessão (DT-10), trilha de auditoria (#19),
 * convites por token (#20), perfil/endereço (SPEC-CAD-04) e a ativação por link.
 *
 * <p>Cobrem também o <strong>RBAC</strong> de cada área — quem não pode entrar
 * recebe 403 — e os caminhos de erro que essas telas mostram em <em>flash</em>.
 */
@DisplayName("Web — Telas administrativas e de conta")
class TelasAdministrativasTest extends IntegracaoWebTestBase {

    @Autowired private ConfiguracaoService configuracaoService;

    // ------------------------------------------------------------------- /admin

    @Test
    @DisplayName("GET /admin e as subtelas abrem para o SYSADMIN")
    void painelAdmin() throws Exception {
        mockMvc.perform(get("/admin").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/configuracoes").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/auditoria").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/convites").with(comoSysadmin())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("RBAC: GERENTE não entra em /admin (403)")
    void adminSoParaSysadmin() throws Exception {
        mockMvc.perform(get("/admin").with(comoGerente())).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/auditoria").with(comoGerente())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/configuracoes grava o timeout de sessão e a cidade-sede")
    void salvarConfiguracoes() throws Exception {
        mockMvc.perform(post("/admin/configuracoes").with(comoSysadmin()).with(csrf())
                        .param("timeoutMinutos", "45")
                        .param("cidadeSedeId", cidadeDestino().getId().toString()))
                .andExpect(redirectedUrl("/admin/configuracoes"))
                .andExpect(flash().attributeExists("sucesso"));

        assertThat(configuracaoService.getTimeoutSessaoMinutos()).isEqualTo(45);
        assertThat(configuracaoService.getCidadeSedeId()).isPresent();
    }

    @Test
    @DisplayName("GET /historico mostra a trilha de operação ao GERENTE")
    void historicoDoGerente() throws Exception {
        mockMvc.perform(get("/historico").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/historico").param("pagina", "1").with(comoGerente()))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- convites

    @Test
    @DisplayName("POST /admin/convites gera o convite de GERENTE com o link de ativação")
    void convidarGerente() throws Exception {
        String telefone = telefoneUnico();
        // Quem convida precisa existir no banco: o token guarda quem o criou (FK).
        Usuario sysadmin = persistir("Sysadmin que convida", false, Papel.SYSADMIN);

        mockMvc.perform(post("/admin/convites").with(autenticar(sysadmin)).with(csrf())
                        .param("nome", "Nova Gestora")
                        .param("telefone", telefone)
                        .param("email", "nova.gestora" + telefone + "@exemplo.test"))
                .andExpect(redirectedUrl("/admin/convites"))
                .andExpect(flash().attributeExists("sucesso"));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone))
                .hasValueSatisfying(u -> {
                    assertThat(u.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
                    assertThat(u.temPapel(Papel.GERENTE)).isTrue();
                });
    }

    @Test
    @DisplayName("POST /admin/convites com telefone repetido devolve o erro em flash")
    void convidarComTelefoneRepetido() throws Exception {
        Usuario existente = persistir("Já cadastrada", false, Papel.PASSAGEIRO);

        mockMvc.perform(post("/admin/convites").with(comoSysadmin()).with(csrf())
                        .param("nome", "Repetida")
                        .param("telefone", existente.getTelefone()))
                .andExpect(redirectedUrl("/admin/convites"))
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    @DisplayName("GERENTE convida MOTORISTA em /usuarios/convidar")
    void convidarMotorista() throws Exception {
        String telefone = telefoneUnico();
        Usuario gerente = persistir("Gerente que convida", false, Papel.GERENTE);

        mockMvc.perform(get("/usuarios/convidar").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(post("/usuarios/convidar").with(autenticar(gerente)).with(csrf())
                        .param("nome", "Novo Motorista")
                        .param("telefone", telefone))
                .andExpect(redirectedUrl("/usuarios/convidar"))
                .andExpect(flash().attributeExists("sucesso"));

        assertThat(usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone))
                .hasValueSatisfying(u -> assertThat(u.temPapel(Papel.MOTORISTA)).isTrue());
    }

    // ----------------------------------------------------------------- ativação

    @Test
    @DisplayName("GET /ativar é público; POST com token inválido volta com erro (sem ativar nada)")
    void ativacaoComTokenInvalido() throws Exception {
        mockMvc.perform(get("/ativar").param("token", "qualquer")).andExpect(status().isOk());

        mockMvc.perform(post("/ativar").with(csrf())
                        .param("token", "token-que-nao-existe")
                        .param("senha", "senha-nova-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erro"));
    }

    // ------------------------------------------------------------------- perfil

    @Test
    @DisplayName("GET /perfil abre para qualquer autenticado e POST salva o endereço")
    void perfilEEndereco() throws Exception {
        Usuario passageiro = persistir("Passageira do Perfil", false, Papel.PASSAGEIRO);

        mockMvc.perform(get("/perfil").with(autenticar(passageiro))).andExpect(status().isOk());

        mockMvc.perform(post("/perfil").with(autenticar(passageiro)).with(csrf())
                        .param("bairro", "Centro")
                        .param("logradouro", "Rua das Flores")
                        .param("numero", "100"))
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attributeExists("sucesso"));
    }
}
