package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Organizacao;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Vinculo;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusVinculo;
import br.ufpb.dsc.caladrius.repository.OrganizacaoRepository;
import br.ufpb.dsc.caladrius.repository.VinculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de API da <strong>escolha de contexto</strong> (SPEC-PLT-02 §5.3): quando a
 * pessoa tem vínculo com mais de uma secretaria — ou mais de um papel na mesma — ela
 * escolhe por onde entra, e só então a sessão ganha um tenant.
 *
 * <p>Três propriedades sustentam esta tela:
 *
 * <ul>
 *   <li><strong>Regressão zero</strong> (CA-MT-02 na prática): quem não tem vínculo
 *       nenhum — que é <em>todo mundo</em> hoje — continua entrando direto, no tenant
 *       legado. A fase 1 não pode mudar o comportamento de produção;</li>
 *   <li><strong>FR-MT-09 / anti-enumeração</strong>: a tela lista <em>apenas</em> os
 *       vínculos de quem está logado. Nenhuma secretaria alheia aparece;</li>
 *   <li><strong>CA-MT-05</strong>: escolher o vínculo de outra pessoa é 404 — não 403,
 *       para não confirmar que aquele vínculo existe.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Escolha de contexto — /entrar/onde (API)")
class SelecaoContextoWebTest extends IntegracaoWebTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired private OrganizacaoRepository organizacaoRepository;
    @Autowired private VinculoRepository vinculoRepository;

    private Organizacao organizacao(String nome) {
        Organizacao org = new Organizacao();
        org.setNome(nome);
        org.setSlug(nome.toLowerCase().replace(' ', '-') + "-" + SEQ.getAndIncrement());
        return organizacaoRepository.save(org);
    }

    private Vinculo vinculoAtivo(Usuario usuario, Organizacao org, Papel papel) {
        Vinculo vinculo = new Vinculo(usuario, org, papel);
        vinculo.setStatus(StatusVinculo.ATIVO);
        return vinculoRepository.save(vinculo);
    }

    @Test
    @DisplayName("quem não tem vínculo nenhum entra direto — o deploy de hoje não muda (tenant legado)")
    void semVinculoEntraDireto() throws Exception {
        Usuario sozinho = persistir("Sem Vínculo", false, Papel.PASSAGEIRO);

        mockMvc.perform(get("/").with(autenticar(sozinho)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("com um único vínculo ativo, o contexto é assumido sem perguntar nada")
    void vinculoUnicoNaoPergunta() throws Exception {
        Usuario pessoa = persistir("Vínculo Único", false, Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Campina"), Papel.PASSAGEIRO);

        mockMvc.perform(get("/").with(autenticar(pessoa)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("com dois vínculos, qualquer tela leva à escolha antes de liberar o sistema")
    void doisVinculosExigemEscolha() throws Exception {
        Usuario pessoa = persistir("Motorista e Passageira", false, Papel.PASSAGEIRO, Papel.MOTORISTA);
        vinculoAtivo(pessoa, organizacao("Campina"), Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Caruaru"), Papel.MOTORISTA);

        mockMvc.perform(get("/").with(autenticar(pessoa)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entrar/onde"));
    }

    @Test
    @DisplayName("FR-MT-09: a tela mostra só os vínculos de quem está logado — nenhuma outra secretaria")
    void telaNaoEnumeraOutrasSecretarias() throws Exception {
        Usuario pessoa = persistir("Dona dos Vínculos", false, Papel.PASSAGEIRO, Papel.MOTORISTA);
        vinculoAtivo(pessoa, organizacao("Minha Secretaria"), Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Outra Minha"), Papel.MOTORISTA);

        Usuario estranho = persistir("Alheio", false, Papel.PASSAGEIRO);
        vinculoAtivo(estranho, organizacao("Secretaria Alheia"), Papel.PASSAGEIRO);

        mockMvc.perform(get("/entrar/onde").with(autenticar(pessoa)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Minha Secretaria")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Secretaria Alheia"))));
    }

    @Test
    @DisplayName("escolher um vínculo libera o sistema e a escolha vale para a sessão inteira")
    void escolherLiberaOSistema() throws Exception {
        Usuario pessoa = persistir("Escolhedora", false, Papel.PASSAGEIRO, Papel.MOTORISTA);
        Vinculo campina = vinculoAtivo(pessoa, organizacao("Campina Escolha"), Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Caruaru Escolha"), Papel.MOTORISTA);
        MockHttpSession sessao = new MockHttpSession();
        RequestPostProcessor login = autenticar(pessoa);

        mockMvc.perform(post("/entrar/onde").session(sessao).with(login).with(csrf())
                        .param("vinculo", campina.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Mesma sessão: o filtro não pede escolha de novo.
        mockMvc.perform(get("/").session(sessao).with(login))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA-MT-05: escolher o vínculo de outra pessoa é 404 — sem confirmar que ele existe")
    void vinculoDeOutraPessoaNaoEscolhivel() throws Exception {
        Usuario pessoa = persistir("Curiosa", false, Papel.PASSAGEIRO, Papel.MOTORISTA);
        vinculoAtivo(pessoa, organizacao("Campina Curiosa"), Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Caruaru Curiosa"), Papel.MOTORISTA);

        Usuario outra = persistir("Vítima", false, Papel.GERENTE);
        Vinculo alheio = vinculoAtivo(outra, organizacao("Secretaria da Vítima"), Papel.GERENTE);

        mockMvc.perform(post("/entrar/onde").with(autenticar(pessoa)).with(csrf())
                        .param("vinculo", alheio.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("vínculo inexistente também é 404 (mesma resposta, sem oráculo)")
    void vinculoInexistente() throws Exception {
        Usuario pessoa = persistir("Sem Sorte", false, Papel.PASSAGEIRO, Papel.MOTORISTA);
        vinculoAtivo(pessoa, organizacao("Campina Sorte"), Papel.PASSAGEIRO);
        vinculoAtivo(pessoa, organizacao("Caruaru Sorte"), Papel.MOTORISTA);

        mockMvc.perform(post("/entrar/onde").with(autenticar(pessoa)).with(csrf())
                        .param("vinculo", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a tela de escolha exige login — anônimo vai para o login")
    void anonimoNaoVeAEscolha() throws Exception {
        mockMvc.perform(get("/entrar/onde"))
                .andExpect(status().is3xxRedirection());
    }
}
