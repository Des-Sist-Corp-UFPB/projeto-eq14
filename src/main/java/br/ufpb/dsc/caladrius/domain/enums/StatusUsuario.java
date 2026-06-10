package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Situação cadastral de um {@code Usuario}.
 *
 * <p>Apenas usuários {@link #ATIVO} conseguem autenticar no sistema
 * (ver {@code CaladriusUserDetailsService}).
 */
public enum StatusUsuario {

    PENDENTE("Pendente"),
    ATIVO("Ativo"),
    INATIVO("Inativo"),
    SUSPENSO("Suspenso");

    private final String rotulo;

    StatusUsuario(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
