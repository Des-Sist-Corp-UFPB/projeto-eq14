package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Papéis de acesso do sistema (RBAC — Role-Based Access Control).
 *
 * <p>Um usuário pode ter mais de um papel. As permissões de cada tela/endpoint
 * são decididas a partir desses papéis (ver {@code SecurityConfig}).
 *
 * <p><strong>Integração com o Spring Security:</strong> o Spring representa
 * autoridades de papel com o prefixo {@code ROLE_}. Assim, {@link #GERENTE}
 * corresponde à autoridade {@code ROLE_GERENTE} e a regra {@code hasRole('GERENTE')}.
 */
public enum Papel {

    PASSAGEIRO("Passageiro"),
    MOTORISTA("Motorista"),
    GERENTE("Gerente");

    private final String rotulo;

    Papel(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }

    /** Autoridade no formato esperado pelo Spring Security (ex.: {@code ROLE_GERENTE}). */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
