package br.ufpb.dsc.caladrius.multitenancia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de {@link ContextoTenant} — o portador da organização da requisição
 * (SPEC-PLT-02 §7.1).
 *
 * <p>É uma classe pequena, mas guarda dois invariantes de <strong>segurança</strong>:
 * o padrão é o <em>tenant legado</em> (deploy single-tenant de hoje, RN-MT-10 não é
 * violada porque nada foi escolhido ainda) e o contexto <strong>não atravessa
 * threads</strong> — com um pool de conexões e requisições concorrentes, vazar aqui é
 * vazar dado de outra secretaria.
 */
@DisplayName("ContextoTenant — organização da requisição corrente")
class ContextoTenantTest {

    @AfterEach
    void limpar() {
        ContextoTenant.limpar();
    }

    @Test
    @DisplayName("sem nada definido, a requisição roda no tenant legado (deploy single-tenant)")
    void padraoEhLegado() {
        assertThat(ContextoTenant.organizacao()).isEmpty();
        assertThat(ContextoTenant.legado()).isTrue();
    }

    @Test
    @DisplayName("definir passa a valer para a requisição; limpar devolve ao legado")
    void definirELimpar() {
        UUID org = UUID.randomUUID();

        ContextoTenant.definir(org);
        assertThat(ContextoTenant.organizacao()).contains(org);
        assertThat(ContextoTenant.legado()).isFalse();

        ContextoTenant.limpar();
        assertThat(ContextoTenant.organizacao()).isEmpty();
    }

    @Test
    @DisplayName("definir(null) equivale a limpar — nunca deixa um contexto meio definido")
    void definirNuloLimpa() {
        ContextoTenant.definir(UUID.randomUUID());

        ContextoTenant.definir(null);

        assertThat(ContextoTenant.organizacao()).isEmpty();
    }

    @Test
    @DisplayName("o contexto NÃO atravessa threads — uma requisição não enxerga o tenant da outra")
    void naoVazaEntreThreads() throws InterruptedException {
        ContextoTenant.definir(UUID.randomUUID());
        AtomicBoolean vazou = new AtomicBoolean(true);

        Thread outra = new Thread(() -> vazou.set(ContextoTenant.organizacao().isPresent()));
        outra.start();
        outra.join();

        assertThat(vazou).isFalse();
    }
}
