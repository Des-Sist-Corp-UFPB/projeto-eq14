package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP do <strong>controle de acesso (RBAC)</strong> definido
 * no {@code SecurityConfig}: rotas exigem o papel certo; anônimos são mandados ao
 * login; papéis errados recebem 403.
 */
@DisplayName("Web — Controle de acesso (RBAC)")
class ControleAcessoTest extends IntegracaoWebTestBase {

    // ---------------------------------------------------------------- anônimo

    /** Sem autenticação, rotas protegidas redirecionam ao login. */
    @Test
    @DisplayName("anônimo em rota de gestão é redirecionado ao login")
    void anonimo_rotaProtegida_redireciona() throws Exception {
        mockMvc.perform(get("/veiculos")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/viagens")).andExpect(status().is3xxRedirection());
    }

    // ---------------------------------------------------------------- gerente

    /** O GERENTE acessa os módulos de gestão. */
    @Test
    @DisplayName("GERENTE acessa os módulos de gestão (200)")
    void gerente_acessaGestao() throws Exception {
        mockMvc.perform(get("/veiculos").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/cidades").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/usuarios").with(comoGerente())).andExpect(status().isOk());
    }

    /** Áreas de SYSADMIN e de MOTORISTA são vedadas ao GERENTE (papéis isolados). */
    @Test
    @DisplayName("GERENTE não acessa /admin nem /minhas-viagens (403)")
    void gerente_naoAcessaAdminNemMotorista() throws Exception {
        mockMvc.perform(get("/admin").with(comoGerente())).andExpect(status().isForbidden());
        mockMvc.perform(get("/minhas-viagens").with(comoGerente())).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- sysadmin

    /** O SYSADMIN acessa a administração; mas não os módulos de gestão do gerente. */
    @Test
    @DisplayName("SYSADMIN acessa /admin (200) e é vedado em /veiculos (403)")
    void sysadmin_acessaAdmin() throws Exception {
        mockMvc.perform(get("/admin").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/veiculos").with(comoSysadmin())).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- motorista

    /** O MOTORISTA acessa apenas a sua visão de viagens. */
    @Test
    @DisplayName("MOTORISTA acessa /minhas-viagens (200) e é vedado em /veiculos (403)")
    void motorista_acessaMinhasViagens() throws Exception {
        mockMvc.perform(get("/minhas-viagens").with(comoMotorista())).andExpect(status().isOk());
        mockMvc.perform(get("/veiculos").with(comoMotorista())).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- passageiro

    /** O PASSAGEIRO não acessa gestão nem administração. */
    @Test
    @DisplayName("PASSAGEIRO é vedado em gestão e administração (403)")
    void passageiro_vedadoGestao() throws Exception {
        mockMvc.perform(get("/veiculos").with(comoPassageiro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").with(comoPassageiro())).andExpect(status().isForbidden());
    }
}
