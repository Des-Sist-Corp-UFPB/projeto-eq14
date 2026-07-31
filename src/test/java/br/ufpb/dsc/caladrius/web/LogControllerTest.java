package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.LogAuditoria;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.AreaSistema;
import br.ufpb.dsc.caladrius.domain.enums.CategoriaAuditoria;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.repository.LogAuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

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

    // -------------------------------------------------- renderização da tela

    /*
     * Os testes desta seção existem por causa da DT-31: o template `logs/lista.html`
     * ficou dois commits fora do repositório e o /logs subiu para produção estourando
     * 500, porque nada exercia os ramos da tela. Toda asserção aqui é sobre o que o
     * Thymeleaf realmente renderiza — paginação, estado vazio e as etiquetas —, que é
     * a parte do /logs sem instrumentação de cobertura (JaCoCo não mede template).
     */

    @Test
    @DisplayName("paginação aparece acima de uma página e leva o filtro junto")
    void paginacaoPreservaOFiltro() throws Exception {
        // 30 eventos > TAMANHO_PAGINA (25) ⇒ a segunda página passa a existir. O termo é
        // único para que o resultado seja exatamente este lote, e não o que as outras
        // classes de teste deixaram no banco (o container é compartilhado).
        String marcador = "marcadordepaginacao";
        List<LogAuditoria> semeados = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            semeados.add(logRepository.save(
                    logDe(CategoriaAuditoria.OPERACAO, "CIDADE_CRIADA", "evento " + i + " " + marcador)));
        }

        try {
            mockMvc.perform(get("/logs").param("busca", marcador).with(comoGerente()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("pagination")))
                    // sem isto, virar a página perderia o filtro e devolveria a trilha inteira
                    .andExpect(content().string(containsString("busca=" + marcador)));

            mockMvc.perform(get("/logs").param("busca", marcador).param("pagina", "1").with(comoGerente()))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("paginaAtual", 1))
                    .andExpect(content().string(containsString("busca=" + marcador)));
        } finally {
            // Devolve o banco ao estado anterior: 30 eventos a mais deslocariam a
            // primeira página de quem listar a trilha depois.
            logRepository.deleteAll(semeados);
        }
    }

    @Test
    @DisplayName("filtro sem resultado mostra o estado vazio, não uma tabela em branco")
    void estadoVazio() throws Exception {
        mockMvc.perform(get("/logs").param("busca", "termo-que-nao-casa-com-nada-xyz").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhum evento para este filtro")));
    }

    @Test
    @DisplayName("a etiqueta do filtro em uso vem marcada como ativa")
    void etiquetaAtivaAcompanhaOFiltro() throws Exception {
        // Sem filtro, quem fica ativa é "Todas".
        mockMvc.perform(get("/logs").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cal-tag--todas cal-tag--ativa")));

        // Com área escolhida, a marca migra para a etiqueta daquela área.
        mockMvc.perform(get("/logs").param("area", "WHATSAPP").with(comoGerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cal-tag--ativa")));
    }

    // ------------------------------------------------------- área "Sistema"

    @Test
    @DisplayName("a área Sistema reúne o que nenhuma etiqueta reivindica")
    void areaSistemaReuneOComplemento() throws Exception {
        LogAuditoria orfao = logRepository.save(
                logDe(CategoriaAuditoria.SISTEMA, "ACAO_SEM_AREA_CONHECIDA", "evento sem etiqueta"));

        try {
            mockMvc.perform(get("/logs").param("area", "SISTEMA").with(comoGerente()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("ACAO_SEM_AREA_CONHECIDA")))
                    // é o complemento: ação já classificada não aparece aqui
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("LOGIN_SUCESSO"))));
        } finally {
            logRepository.delete(orfao);
        }
    }

    @Test
    @DisplayName("busca livre atravessa as áreas: o termo vence a etiqueta selecionada")
    void buscaVenceAArea() throws Exception {
        String marcador = "termoqueatravessaareas";
        LogAuditoria evento = logRepository.save(
                logDe(CategoriaAuditoria.OPERACAO, "CIDADE_CRIADA", marcador));

        try {
            // Pede a área de USUARIOS, mas com termo: vale o termo. Os dois filtros não se
            // combinam de propósito — uma busca que só olhasse a área escolhida esconderia
            // justamente o evento que se procura (AuditoriaService.listar).
            mockMvc.perform(get("/logs").param("area", "USUARIOS").param("busca", marcador).with(comoGerente()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("CIDADE_CRIADA")));
        } finally {
            logRepository.delete(evento);
        }
    }

    /** Log cru direto no repositório — evita 30 requisições HTTP só para haver o que paginar. */
    private LogAuditoria logDe(CategoriaAuditoria categoria, String acao, String detalhe) {
        LogAuditoria log = new LogAuditoria();
        log.setCategoria(categoria);
        log.setAcao(acao);
        log.setDetalhe(detalhe);
        log.setUsuarioNome("Semeador de Teste");
        return log;
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
