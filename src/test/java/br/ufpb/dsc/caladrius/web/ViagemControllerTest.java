package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração HTTP do {@code ViagemController} (SPEC-05/06): criação de
 * viagem imprevista, painel semanal, designação a partir de uma linha e transição
 * de status — percorrendo controller → serviço → banco de verdade.
 *
 * <p>O gerente aqui é <strong>persistido</strong> (e não só um principal sintético)
 * porque o serviço registra quem criou a viagem e recarrega esse usuário do banco.
 */
@DisplayName("Web — ViagemController (SPEC-05/06)")
class ViagemControllerTest extends IntegracaoWebTestBase {

    @Autowired private ViagemRepository viagemRepository;

    private RequestPostProcessor gerentePersistido() {
        return autenticar(persistir("Gerente das Viagens", false, Papel.GERENTE));
    }

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("GET /viagens lista; com HX-Request devolve o fragmento da tabela")
    void listar() throws Exception {
        mockMvc.perform(get("/viagens").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/viagens").with(comoGerente()).header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /viagens/nova devolve o modal do formulário com as opções")
    void novaForm() throws Exception {
        mockMvc.perform(get("/viagens/nova").with(comoGerente())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /viagens/semana renderiza o painel da semana (com e sem data de referência)")
    void painelSemana() throws Exception {
        mockMvc.perform(get("/viagens/semana").with(comoGerente())).andExpect(status().isOk());
        mockMvc.perform(get("/viagens/semana").param("ref", LocalDate.now().toString())
                        .with(comoGerente()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ criação

    @Test
    @DisplayName("POST /viagens cria a viagem imprevista e devolve a linha da tabela")
    void criar() throws Exception {
        Veiculo veiculo = persistirVeiculo();
        Usuario motorista = persistir("Motorista da Viagem", false, Papel.MOTORISTA);
        long antes = viagemRepository.count();

        mockMvc.perform(post("/viagens").with(gerentePersistido())
                        .param("veiculoId", veiculo.getId().toString())
                        .param("motoristaId", motorista.getId().toString())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("dataViagem", LocalDate.now().plusDays(3).toString())
                        .param("horarioSaida", "06:00")
                        .param("horarioChegada", "12:00"))
                .andExpect(status().isOk());

        assertThat(viagemRepository.count()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("POST /viagens sem veículo devolve o formulário (validação), sem criar")
    void criarInvalido() throws Exception {
        long antes = viagemRepository.count();

        mockMvc.perform(post("/viagens").with(gerentePersistido())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("dataViagem", LocalDate.now().plusDays(3).toString())
                        .param("horarioSaida", "06:00")
                        .param("horarioChegada", "12:00"))
                .andExpect(status().isOk());

        assertThat(viagemRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("POST /viagens com motorista sem o papel volta ao formulário com o erro da regra")
    void criarComUsuarioQueNaoEhMotorista() throws Exception {
        Veiculo veiculo = persistirVeiculo();
        Usuario naoMotorista = persistir("Só Passageiro", false, Papel.PASSAGEIRO);
        long antes = viagemRepository.count();

        mockMvc.perform(post("/viagens").with(gerentePersistido())
                        .param("veiculoId", veiculo.getId().toString())
                        .param("motoristaId", naoMotorista.getId().toString())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("dataViagem", LocalDate.now().plusDays(4).toString())
                        .param("horarioSaida", "06:00")
                        .param("horarioChegada", "12:00"))
                .andExpect(status().isOk());

        assertThat(viagemRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("POST /viagens com chegada antes da saída é recusado pela regra de negócio")
    void criarComHorarioInvertido() throws Exception {
        Veiculo veiculo = persistirVeiculo();
        Usuario motorista = persistir("Motorista Horário", false, Papel.MOTORISTA);
        long antes = viagemRepository.count();

        mockMvc.perform(post("/viagens").with(gerentePersistido())
                        .param("veiculoId", veiculo.getId().toString())
                        .param("motoristaId", motorista.getId().toString())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("dataViagem", LocalDate.now().plusDays(5).toString())
                        .param("horarioSaida", "12:00")
                        .param("horarioChegada", "06:00"))
                .andExpect(status().isOk());

        assertThat(viagemRepository.count()).isEqualTo(antes);
    }

    // --------------------------------------------------------- status e exclusão

    @Test
    @DisplayName("POST /viagens/{id}/status muda o status e volta para a listagem")
    void alterarStatus() throws Exception {
        UUID viagemId = criarViagem();

        mockMvc.perform(post("/viagens/" + viagemId + "/status").with(gerentePersistido())
                        .param("status", "EM_ANDAMENTO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));
    }

    @Test
    @DisplayName("POST /viagens/{id}/status de viagem inexistente devolve 404")
    void alterarStatusDeViagemInexistente() throws Exception {
        mockMvc.perform(post("/viagens/" + UUID.randomUUID() + "/status").with(gerentePersistido())
                        .param("status", "CANCELADA"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /viagens/{id} exclui; id inexistente devolve 404")
    void excluir() throws Exception {
        UUID viagemId = criarViagem();

        mockMvc.perform(delete("/viagens/" + viagemId).with(comoGerente()))
                .andExpect(status().isOk());
        assertThat(viagemRepository.findById(viagemId)).isEmpty();

        mockMvc.perform(delete("/viagens/" + UUID.randomUUID()).with(comoGerente()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ designação

    @Test
    @DisplayName("POST /viagens/designar sem veículo/motorista volta ao painel com o erro")
    void designarSemDados() throws Exception {
        mockMvc.perform(post("/viagens/designar").with(gerentePersistido()).with(csrf())
                        .param("linhaId", UUID.randomUUID().toString())
                        .param("dataViagem", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erro"));
    }

    // ---------------------------------------------------------------------- RBAC

    @Test
    @DisplayName("RBAC: PASSAGEIRO não acessa a gestão de viagens (403)")
    void rbac() throws Exception {
        mockMvc.perform(get("/viagens").with(comoPassageiro())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------- apoio

    /** Cria uma viagem pelo endpoint e devolve o id da mais recente. */
    private UUID criarViagem() throws Exception {
        Veiculo veiculo = persistirVeiculo();
        Usuario motorista = persistir("Motorista Status", false, Papel.MOTORISTA);
        LocalDate data = LocalDate.now().plusDays(10);

        mockMvc.perform(post("/viagens").with(gerentePersistido())
                        .param("veiculoId", veiculo.getId().toString())
                        .param("motoristaId", motorista.getId().toString())
                        .param("cidadeDestinoId", cidadeDestino().getId().toString())
                        .param("dataViagem", data.toString())
                        .param("horarioSaida", "06:00")
                        .param("horarioChegada", "12:00"))
                .andExpect(status().isOk());

        return viagemRepository.findAll().stream()
                .filter(v -> v.getMotorista().getId().equals(motorista.getId()))
                .findFirst().orElseThrow().getId();
    }
}
