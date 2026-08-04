package br.ufpb.dsc.caladrius.multitenancia;

import java.util.Optional;
import java.util.UUID;

/**
 * Portador da <strong>organização da requisição corrente</strong> (SPEC-PLT-02 §7.1).
 *
 * <p>É preenchido pelo {@link ContextoTenantFilter} a partir do vínculo que a pessoa
 * escolheu ao entrar — <strong>nunca</strong> a partir de cabeçalho, parâmetro ou
 * subdomínio enviados pelo cliente (RN-MT-08).
 *
 * <p><strong>Ausência de contexto significa "tenant legado"</strong>: o deploy
 * single-tenant de hoje, em que todos os dados vivem no schema padrão. É o estado de
 * 100% das requisições até a fase 2 da spec — por isso a ausência é normal aqui, e não
 * um erro. Quando o schema por tenant entrar (fase 2), este é o ponto único onde o
 * {@code CurrentTenantIdentifierResolver} do Hibernate vai buscar o tenant.
 *
 * <p>O estado vive em {@link ThreadLocal} e <strong>não atravessa threads</strong>: com
 * requisições concorrentes sobre um pool de conexões, vazar aqui seria vazar dado de
 * outra secretaria. Quem define é obrigado a limpar (o filtro faz isso em
 * {@code finally}).
 */
public final class ContextoTenant {

    private static final ThreadLocal<UUID> ORGANIZACAO = new ThreadLocal<>();

    private ContextoTenant() {
    }

    /** A organização da requisição, ou vazio quando se opera no tenant legado. */
    public static Optional<UUID> organizacao() {
        return Optional.ofNullable(ORGANIZACAO.get());
    }

    /** {@code true} quando não há organização definida (deploy single-tenant). */
    public static boolean legado() {
        return ORGANIZACAO.get() == null;
    }

    /** Define a organização da requisição; {@code null} equivale a {@link #limpar()}. */
    public static void definir(UUID organizacaoId) {
        if (organizacaoId == null) {
            limpar();
            return;
        }
        ORGANIZACAO.set(organizacaoId);
    }

    /** Remove o contexto. Obrigatório ao fim de cada requisição (a thread é reusada). */
    public static void limpar() {
        ORGANIZACAO.remove();
    }
}
