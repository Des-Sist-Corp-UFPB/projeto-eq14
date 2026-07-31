package br.ufpb.dsc.caladrius.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários de {@link ConfiguracaoUmami} — analytics de uso (SPEC-17 §9).
 *
 * <p>O valor desta classe está em <strong>não</strong> ligar o rastreador por engano e em
 * normalizar a URL colada do painel; é isso que os casos abaixo fixam.
 */
@DisplayName("ConfiguracaoUmami — Analytics de uso (SPEC-17)")
class ConfiguracaoUmamiTest {

    private static final String ID = "18d3d5e0-9a19-426f-a74e-72f5c837fc74";

    /** RN-ANL-01: sem as duas variáveis não há rastreamento — é o estado de dev e dos testes. */
    @Test
    @DisplayName("sem variáveis fica desligado")
    void semVariaveis_ficaDesligado() {
        assertThat(ConfiguracaoUmami.de("", "", "").isAtivo()).isFalse();
        assertThat(ConfiguracaoUmami.de(null, null, null).isAtivo()).isFalse();
        assertThat(ConfiguracaoUmami.desligado().isAtivo()).isFalse();
    }

    /** Meia configuração é configuração nenhuma: um script sem website-id não coleta nada. */
    @Test
    @DisplayName("só a URL, ou só o website-id, não liga o rastreador")
    void configuracaoIncompleta_ficaDesligado() {
        assertThat(ConfiguracaoUmami.de("https://umami.dsc.rodrigor.com", "", null).isAtivo()).isFalse();
        assertThat(ConfiguracaoUmami.de("", ID, null).isAtivo()).isFalse();
    }

    @Test
    @DisplayName("com as duas variáveis monta a URL do script")
    void configuracaoCompleta_montaScript() {
        ConfiguracaoUmami cfg = ConfiguracaoUmami.de("https://umami.dsc.rodrigor.com", ID, null);

        assertThat(cfg.isAtivo()).isTrue();
        assertThat(cfg.scriptUrl()).isEqualTo("https://umami.dsc.rodrigor.com/script.js");
        assertThat(cfg.websiteId()).isEqualTo(ID);
    }

    /**
     * Armadilha real (a mesma da {@code EVOLUTION_URL}): o painel mostra o endereço sem o
     * esquema. Sem normalizar, o {@code src} viraria relativo e o script não carregaria —
     * silenciosamente.
     */
    @Test
    @DisplayName("presume https:// quando o esquema falta e remove a barra final")
    void normalizaUrl() {
        assertThat(ConfiguracaoUmami.de("umami.dsc.rodrigor.com", ID, null).scriptUrl())
                .isEqualTo("https://umami.dsc.rodrigor.com/script.js");
        assertThat(ConfiguracaoUmami.de("https://umami.dsc.rodrigor.com/", ID, null).scriptUrl())
                .isEqualTo("https://umami.dsc.rodrigor.com/script.js");
        assertThat(ConfiguracaoUmami.de("  https://umami.dsc.rodrigor.com//  ", ID, null).scriptUrl())
                .isEqualTo("https://umami.dsc.rodrigor.com/script.js");
    }

    /** http:// explícito é respeitado (instância local de teste). */
    @Test
    @DisplayName("respeita http:// explícito")
    void respeitaEsquemaInformado() {
        assertThat(ConfiguracaoUmami.de("http://localhost:3000", ID, null).scriptUrl())
                .isEqualTo("http://localhost:3000/script.js");
    }

    /**
     * RN-ANL-02: {@code dominios} vira {@code null} quando não configurado, para o Thymeleaf
     * omitir o atributo {@code data-domains} em vez de renderizá-lo vazio (o que restringiria
     * a coleta a "nenhum domínio").
     */
    @Test
    @DisplayName("domínios em branco viram null (atributo omitido)")
    void dominiosEmBranco_viramNull() {
        assertThat(ConfiguracaoUmami.de("https://u.exemplo.com", ID, "  ").dominios()).isNull();
        assertThat(ConfiguracaoUmami.de("https://u.exemplo.com", ID, null).dominios()).isNull();
        assertThat(ConfiguracaoUmami.de("https://u.exemplo.com", ID, " eq14.dsc.rodrigor.com ").dominios())
                .isEqualTo("eq14.dsc.rodrigor.com");
    }
}
