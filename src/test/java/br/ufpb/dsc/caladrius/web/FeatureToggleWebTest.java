package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Municipio;
import br.ufpb.dsc.caladrius.repository.LogAuditoriaRepository;
import br.ufpb.dsc.caladrius.repository.MunicipioRepository;
import br.ufpb.dsc.caladrius.service.ChaveFeature;
import br.ufpb.dsc.caladrius.service.FeatureFlagService;
import br.ufpb.dsc.caladrius.service.ParametroSistema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de API (MockMvc + Testcontainers) do feature toggle — SPEC-PLT-01 §8.
 *
 * <p>Exercem os critérios de aceite de ponta a ponta: RBAC das telas, o efeito real
 * do <strong>modo de manutenção</strong> sobre requisições de papéis distintos
 * (CA-FLG-01), a imunidade do {@code /ping} (CA-FLG-02), o efeito imediato do
 * interruptor com auditoria (CA-FLG-06), os parâmetros de negócio (CA-FLG-04) e o
 * entitlement por município (CA-FLG-05).
 *
 * <p>Como as flags são <strong>estado global</strong> compartilhado pelo contexto do
 * Spring, cada teste devolve tudo ao padrão no {@code @AfterEach} — senão um teste
 * deixaria o sistema "em manutenção" para os seguintes.
 */
@DisplayName("Feature toggle — API (SPEC-PLT-01)")
class FeatureToggleWebTest extends IntegracaoWebTestBase {

    @Autowired private FeatureFlagService featureFlags;
    @Autowired private MunicipioRepository municipioRepository;
    @Autowired private LogAuditoriaRepository auditoriaRepository;

    @AfterEach
    void restaurarPadroes() {
        featureFlags.definir(ChaveFeature.MODO_MANUTENCAO, false);
        featureFlags.definir(ChaveFeature.BOT_WHATSAPP, true);
        featureFlags.definir(ParametroSistema.OTP_MAX_TENTATIVAS,
                ParametroSistema.OTP_MAX_TENTATIVAS.getPadrao());
    }

    // ------------------------------------------------------------------ RBAC

    @Test
    @DisplayName("/admin/features: SYSADMIN vê a tela; GERENTE recebe 403")
    void rbacDaTela() throws Exception {
        mockMvc.perform(get("/admin/features").with(comoSysadmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Interruptores")));

        mockMvc.perform(get("/admin/features").with(comoGerente()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/municipios").with(comoPassageiro()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------- CA-FLG-06 (efeito + auditoria)

    @Test
    @DisplayName("CA-FLG-06: alternar a flag vale na hora e fica registrado na auditoria")
    void alternarFlagTemEfeitoImediatoEAuditoria() throws Exception {
        long antes = auditoriaRepository.count();

        mockMvc.perform(post("/admin/features/BOT_WHATSAPP")
                        .param("ativo", "false")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Desligada")));

        assertThat(featureFlags.ativo(ChaveFeature.BOT_WHATSAPP)).isFalse();
        assertThat(auditoriaRepository.count()).isGreaterThan(antes);
    }

    // ---------------------------------------------------- CA-FLG-01/02 (manutenção)

    @Test
    @DisplayName("CA-FLG-01: em manutenção, o GERENTE recebe 503 e o SYSADMIN continua entrando")
    void manutencaoBarraUsuarioComumELiberaSysadmin() throws Exception {
        featureFlags.definir(ChaveFeature.MODO_MANUTENCAO, true);

        mockMvc.perform(get("/viagens").with(comoGerente()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(forwardedUrl("/manutencao"));

        mockMvc.perform(get("/admin/features").with(comoSysadmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA-FLG-02: /ping e o health check ficam imunes à manutenção (Art. XIII)")
    void pingImuneAManutencao() throws Exception {
        featureFlags.definir(ChaveFeature.MODO_MANUTENCAO, true);

        mockMvc.perform(get("/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // A própria página de manutenção é pública e responde 503 (semântica correta).
        mockMvc.perform(get("/manutencao")).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("manutenção + HTMX: responde 503 com HX-Redirect (não injeta a página no fragmento)")
    void manutencaoEmRequisicaoHtmx() throws Exception {
        featureFlags.definir(ChaveFeature.MODO_MANUTENCAO, true);

        mockMvc.perform(get("/viagens").header("HX-Request", "true").with(comoGerente()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("HX-Redirect", "/manutencao"));
    }

    // ------------------------------------------------------- parâmetros de negócio

    @Test
    @DisplayName("FR-FLG-02: salvar parâmetros grava o valor e ele passa a valer")
    void salvarParametros() throws Exception {
        mockMvc.perform(post("/admin/features/parametros")
                        .param(ParametroSistema.OTP_MAX_TENTATIVAS.name(), "3")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/features"));

        assertThat(featureFlags.parametro(ParametroSistema.OTP_MAX_TENTATIVAS)).isEqualTo(3);
    }

    @Test
    @DisplayName("CA-FLG-04/RN-FLG-06: valor fora do intervalo é recusado e o anterior permanece")
    void parametroForaDoIntervaloEhRecusado() throws Exception {
        mockMvc.perform(post("/admin/features/parametros")
                        .param(ParametroSistema.OTP_MAX_TENTATIVAS.name(), "999")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erro"));

        assertThat(featureFlags.parametro(ParametroSistema.OTP_MAX_TENTATIVAS))
                .isEqualTo(ParametroSistema.OTP_MAX_TENTATIVAS.getPadrao());
    }

    @Test
    @DisplayName("valor não numérico no parâmetro devolve mensagem de erro, sem quebrar")
    void parametroNaoNumerico() throws Exception {
        mockMvc.perform(post("/admin/features/parametros")
                        .param(ParametroSistema.OTP_VALIDADE_MINUTOS.name(), "dez")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    @DisplayName("flag desconhecida na URL vira 400 (não altera nada)")
    void flagDesconhecida() throws Exception {
        mockMvc.perform(post("/admin/features/NAO_EXISTE")
                        .param("ativo", "true")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------- CA-FLG-05 (entitlement V15)

    @Test
    @DisplayName("CA-FLG-05: marcar a adesão de um município persiste o entitlement")
    void adesaoDoMunicipio() throws Exception {
        Municipio municipio = municipioRepository.findAllByOrderByNomeAsc().get(0);
        assertThat(municipio.isPagamentoHabilitado()).isFalse();

        mockMvc.perform(post("/admin/municipios/" + municipio.getId())
                        .param("habilitado", "true")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aderiu")));

        assertThat(municipioRepository.findById(municipio.getId()))
                .hasValueSatisfying(m -> assertThat(m.isPagamentoHabilitado()).isTrue());

        // devolve ao estado original (a base é compartilhada entre os testes)
        mockMvc.perform(post("/admin/municipios/" + municipio.getId())
                        .param("habilitado", "false")
                        .with(comoSysadmin()).with(csrf()))
                .andExpect(status().isOk());
    }
}
