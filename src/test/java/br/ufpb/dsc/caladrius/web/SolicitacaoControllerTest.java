package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração HTTP da visão do passageiro (SPEC-09): RBAC, fluxo de
 * solicitação via sistema e isolamento (um passageiro não vê o do outro).
 */
@DisplayName("Web — Solicitação de transporte do passageiro (SPEC-09)")
class SolicitacaoControllerTest extends IntegracaoWebTestBase {

    @Autowired private CidadeRepository cidadeRepository;
    @Autowired private LinhaProgramadaRepository linhaRepository;

    /** Cria e persiste uma linha ativa que opera em todos os dias (para qualquer data servir). */
    private LinhaProgramada linhaAtiva(String destino) {
        return linhaNosDias(destino, EnumSet.allOf(DiaSemana.class));
    }

    /** Cria e persiste uma linha ativa que opera apenas nos dias informados. */
    private LinhaProgramada linhaNosDias(String destino, EnumSet<DiaSemana> dias) {
        Cidade cidade = cidadeRepository.save(new Cidade(destino, "PB", TipoCidade.METROPOLITANA));
        LinhaProgramada linha = new LinhaProgramada();
        linha.setCidadeDestino(cidade);
        linha.setHorarioSaida(LocalTime.of(7, 0));
        linha.setHorarioChegada(LocalTime.of(9, 0));
        linha.setAtiva(true);
        linha.setDias(dias);
        return linhaRepository.save(linha);
    }

    // ----------------------------------------------------------------- RBAC

    @Test
    @DisplayName("PASSAGEIRO acessa /solicitacoes (200)")
    void passageiro_acessa() throws Exception {
        mockMvc.perform(get("/solicitacoes").with(comoPassageiro())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GERENTE e MOTORISTA são vedados em /solicitacoes (403)")
    void outrosPapeis_vedados() throws Exception {
        mockMvc.perform(get("/solicitacoes").with(comoGerente())).andExpect(status().isForbidden());
        mockMvc.perform(get("/solicitacoes").with(comoMotorista())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anônimo em /solicitacoes é redirecionado ao login")
    void anonimo_redireciona() throws Exception {
        mockMvc.perform(get("/solicitacoes")).andExpect(status().is3xxRedirection());
    }

    // -------------------------------------------------- calendário (filtro por data)

    @Test
    @DisplayName("calendário: a data selecionada só oferece para solicitar as linhas daquele dia")
    void calendario_filtraLinhasSolicitaveisPorDia() throws Exception {
        LinhaProgramada soSegunda = linhaNosDias("Sousa", EnumSet.of(DiaSemana.SEGUNDA));
        LocalDate segunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate terca = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        String idLinha = soSegunda.getId().toString();

        // Numa segunda, a linha é solicitável → o formulário com o id da linha aparece.
        mockMvc.perform(get("/solicitacoes").param("data", segunda.toString()).with(comoPassageiro()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(idLinha)));

        // Numa terça, a linha NÃO opera → não há formulário de solicitação (id ausente).
        mockMvc.perform(get("/solicitacoes").param("data", terca.toString()).with(comoPassageiro()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(idLinha))));
    }

    // ----------------------------------------------------- fluxo de solicitação

    @Test
    @DisplayName("passageiro solicita transporte e passa a vê-lo em \"Minhas viagens\"")
    void solicitar_eVer() throws Exception {
        Usuario passageiro = persistir("Passageiro Solicitante", false, Papel.PASSAGEIRO);
        LinhaProgramada linha = linhaAtiva("Campina Grande");
        LocalDate data = LocalDate.now().plusDays(2);
        String marcador = "OBS-" + java.util.UUID.randomUUID();

        mockMvc.perform(post("/solicitacoes")
                        .param("linhaId", linha.getId().toString())
                        .param("data", data.toString())
                        .param("observacao", marcador)
                        .with(autenticar(passageiro)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/solicitacoes"));

        mockMvc.perform(get("/solicitacoes").with(autenticar(passageiro)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Campina Grande")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(marcador)));
    }

    @Test
    @DisplayName("isolamento: um passageiro não vê a solicitação de outro")
    void naoVeDeOutro() throws Exception {
        Usuario dono = persistir("Dono", false, Papel.PASSAGEIRO);
        Usuario bisbilhoteiro = persistir("Bisbilhoteiro", false, Papel.PASSAGEIRO);
        LinhaProgramada linha = linhaAtiva("Patos");
        LocalDate data = LocalDate.now().plusDays(2);
        String marcador = "PRIVADO-" + java.util.UUID.randomUUID();

        mockMvc.perform(post("/solicitacoes")
                        .param("linhaId", linha.getId().toString())
                        .param("data", data.toString())
                        .param("observacao", marcador)
                        .with(autenticar(dono)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // O outro passageiro não deve ver o marcador da solicitação alheia.
        mockMvc.perform(get("/solicitacoes").with(autenticar(bisbilhoteiro)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(marcador))));
    }
}
