package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Ciclo de vida de uma viagem.
 *
 * <p>Fluxo intencional:
 * {@code PLANEJADA → CONFIRMADA → EM_ANDAMENTO → CONCLUIDA} (ou {@code CANCELADA}).
 */
public enum StatusViagem {

    PLANEJADA("Planejada"),
    CONFIRMADA("Confirmada"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada");

    private final String rotulo;

    StatusViagem(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
