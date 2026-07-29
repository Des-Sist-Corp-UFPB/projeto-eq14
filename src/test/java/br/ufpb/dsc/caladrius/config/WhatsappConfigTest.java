package br.ufpb.dsc.caladrius.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da normalização de {@code EVOLUTION_URL} (SPEC-10).
 *
 * <p>Nasceram de uma falha real em produção (2026-07-29): o painel do Railway exibe
 * o domínio <strong>sem</strong> o esquema (<code>evolution-api-....up.railway.app</code>).
 * Colado assim no {@code .env}, o {@code RestClient} monta uma URI relativa e o JDK
 * lança {@code IllegalArgumentException: URI with undefined scheme} — que, no listener
 * de boot, derrubava a aplicação inteira.
 *
 * <p>A configuração passou a aceitar a forma que a pessoa tem em mãos.
 */
@DisplayName("WhatsappConfig — normalização da EVOLUTION_URL")
class WhatsappConfigTest {

    @Test
    @DisplayName("domínio sem esquema (como o Railway mostra) ganha https://")
    void semEsquemaViraHttps() {
        assertThat(WhatsappConfig.normalizarUrl("evolution-api-production-c3f4.up.railway.app"))
                .isEqualTo("https://evolution-api-production-c3f4.up.railway.app");
    }

    @Test
    @DisplayName("esquema informado é preservado (inclusive http:// de dev local)")
    void esquemaInformadoEhPreservado() {
        assertThat(WhatsappConfig.normalizarUrl("https://evo.exemplo.com"))
                .isEqualTo("https://evo.exemplo.com");
        assertThat(WhatsappConfig.normalizarUrl("http://localhost:8080"))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("barra(s) no final são removidas — o adaptador concatena os paths")
    void barraFinalEhRemovida() {
        assertThat(WhatsappConfig.normalizarUrl("https://evo.exemplo.com/"))
                .isEqualTo("https://evo.exemplo.com");
        assertThat(WhatsappConfig.normalizarUrl("https://evo.exemplo.com///"))
                .isEqualTo("https://evo.exemplo.com");
        assertThat(WhatsappConfig.normalizarUrl("evo.exemplo.com/"))
                .isEqualTo("https://evo.exemplo.com");
    }

    @Test
    @DisplayName("espaços em volta são ignorados (colar do painel costuma trazê-los)")
    void espacosSaoIgnorados() {
        assertThat(WhatsappConfig.normalizarUrl("  https://evo.exemplo.com  "))
                .isEqualTo("https://evo.exemplo.com");
    }

    @Test
    @DisplayName("URL com porta e caminho de base continua intacta")
    void portaECaminhoPreservados() {
        assertThat(WhatsappConfig.normalizarUrl("https://evo.exemplo.com:8443/api"))
                .isEqualTo("https://evo.exemplo.com:8443/api");
    }

    @Test
    @DisplayName("vazio/nulo continua vazio (o bean nem chega a ser criado nesse caso)")
    void vazioContinuaVazio() {
        assertThat(WhatsappConfig.normalizarUrl(null)).isEmpty();
        assertThat(WhatsappConfig.normalizarUrl("   ")).isEmpty();
    }
}
