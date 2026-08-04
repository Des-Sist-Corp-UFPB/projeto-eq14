package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Finalidade de um {@code TokenAtivacao} (link mágico) — SPEC-ACE-03.
 *
 * <ul>
 *   <li>{@link #ATIVACAO} — convite/onboarding: define a senha e ativa a conta
 *       (comportamento original — ADR-11). É o <strong>padrão</strong>.</li>
 *   <li>{@link #VERIFICAR_EMAIL} — apenas confirma o e-mail (marca
 *       {@code email_verificado_em}); não mexe em senha nem status.</li>
 * </ul>
 */
public enum FinalidadeToken {
    ATIVACAO,
    VERIFICAR_EMAIL
}
