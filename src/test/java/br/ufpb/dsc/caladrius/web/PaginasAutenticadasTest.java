package br.ufpb.dsc.caladrius.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP das demais telas autenticadas (leitura), cada uma com o
 * papel adequado. Exercem o caminho de leitura de vários controladores, serviços,
 * repositórios e a renderização dos templates de uma só vez.
 */
@DisplayName("Web — Telas autenticadas (leitura por papel)")
class PaginasAutenticadasTest extends IntegracaoWebTestBase {

    // ----------------------------------------------------------------- gerente

    @Test
    @DisplayName("GERENTE: viagens (lista, nova, painel semanal)")
    void gerente_viagens() throws Exception {
        mockMvc.perform(get("/viagens").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/viagens/nova").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/viagens/semana").with(comoGerente())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GERENTE: linhas programadas (lista e nova)")
    void gerente_linhas() throws Exception {
        mockMvc.perform(get("/linhas").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/linhas/nova").with(comoGerente())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GERENTE: análise de passageiros, histórico e WhatsApp")
    void gerente_outros() throws Exception {
        mockMvc.perform(get("/analise").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/historico").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/whatsapp").with(comoGerente())).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- sysadmin

    @Test
    @DisplayName("SYSADMIN: home do admin, auditoria e configurações")
    void sysadmin_admin() throws Exception {
        mockMvc.perform(get("/admin").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/auditoria").with(comoSysadmin())).andExpect(status().isOk());
        mockMvc.perform(get("/admin/configuracoes").with(comoSysadmin())).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- motorista

    @Test
    @DisplayName("MOTORISTA: minhas viagens")
    void motorista_minhasViagens() throws Exception {
        mockMvc.perform(get("/minhas-viagens").with(comoMotorista())).andExpect(status().isOk());
    }

    // --------------------------------------------------------------- passageiro

    @Test
    @DisplayName("PASSAGEIRO: meu perfil (endereço)")
    void passageiro_perfil() throws Exception {
        mockMvc.perform(get("/perfil").with(comoPassageiro())).andExpect(status().isOk());
    }
}
