package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.AreaSistema;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração da <strong>central de logs</strong> ({@code /logs}): RBAC,
 * filtro por área, busca livre e a garantia de que as ações do painel WhatsApp —
 * que antes não deixavam rastro nenhum — agora entram na trilha.
 */
@DisplayName("Web — Central de logs (/logs)")
class LogControllerTest extends IntegracaoWebTestBase {

    @Autowired private LogAuditoriaRepository logRepository;

    // ---------------------------------------------------------------------- RBAC

    @Test
    @DisplayName("GERENTE e SYSADMIN acessam a central de logs")
    void acessoPermitido() throws Exception {
        mockMvc.perform(get("/logs").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Logs do sistema")));
        mockMvc.perform(get("/logs").with(comoSysadmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RBAC: passageiro e motorista não veem os logs do sistema (403)")
    void acessoNegado() throws Exception {
        mockMvc.perform(get("/logs").with(comoPassageiro())).andExpect(status().isForbidden());
        mockMvc.perform(get("/logs").with(comoMotorista())).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------- filtros

    @Test
    @DisplayName("a tela oferece todas as áreas como etiqueta/filtro")
    void listaAsAreas() throws Exception {
        mockMvc.perform(get("/logs").with(comoGerente()))
                .andExpect(model().attribute("areas", AreaSistema.values()))
                .andExpect(content().string(containsString("WhatsApp")))
                .andExpect(content().string(containsString("Solicitações")));
    }

    @Test
    @DisplayName("filtro por área devolve só os eventos daquela área")
    void filtroPorArea() throws Exception {
        // Gera um evento de USUARIOS e outro de CIDADES pelos endpoints reais.
        mockMvc.perform(post("/usuarios").with(comoGerente())
                        .param("nomeCompleto", "Alvo do Log")
                        .param("telefone", telefoneUnico())
                        .param("senha", "segredo123")
                        .param("papeis", "PASSAGEIRO")
                        .param("status", "ATIVO"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/logs").param("area", "USUARIOS").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("USUARIO_CRIADO")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("LOGIN_SUCESSO"))));
    }

    @Test
    @DisplayName("busca livre encontra pelo nome do autor e pelo detalhe")
    void buscaLivre() throws Exception {
        Usuario gerente = persistir("Gerente Rastreável", false, Papel.GERENTE);

        mockMvc.perform(post("/cidades").with(autenticar(gerente))
                        .param("nome", "Cidade Do Log")
                        .param("uf", "PB")
                        .param("tipo", "METROPOLITANA"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/logs").param("busca", "Cidade Do Log").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CIDADE_CRIADA")));
    }

    @Test
    @DisplayName("área desconhecida na URL vira 400 (o filtro aceita só o catálogo)")
    void areaInvalida() throws Exception {
        mockMvc.perform(get("/logs").param("area", "NAO_EXISTE").with(comoGerente()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------ lacuna fechada: WhatsApp

    @Test
    @DisplayName("as ações do painel WhatsApp passam a deixar rastro na trilha")
    void painelWhatsappGeraLog() throws Exception {
        long antes = logRepository.count();

        mockMvc.perform(post("/whatsapp/configuracoes").with(comoGerente()).with(csrf())
                        .param("nomeExibicao", "Transporte de Patos")
                        .param("mensagemConfirmacao", "Viagem em {data}."))
                .andExpect(status().is3xxRedirection());

        assertThat(logRepository.count()).isGreaterThan(antes);
        assertThat(logRepository.findAll().stream()
                .anyMatch(l -> "WHATSAPP_CONFIG_ALTERADA".equals(l.getAcao()))).isTrue();

        mockMvc.perform(get("/logs").param("area", "WHATSAPP").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WHATSAPP_CONFIG_ALTERADA")));
    }

    // ------------------------------------------------------------ /historico

    @Test
    @DisplayName("/historico passa a mostrar só o ciclo das solicitações")
    void historicoSoDeSolicitacoes() throws Exception {
        mockMvc.perform(get("/historico").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Histórico de solicitações")))
                // eventos de outras áreas não aparecem mais aqui
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("USUARIO_CRIADO"))));
    }
}
