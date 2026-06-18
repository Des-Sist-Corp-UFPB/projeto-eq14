package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Tipo de uma viagem (ocorrência):
 * <ul>
 *   <li>{@link #ROTINEIRA} — materializada a partir de uma {@code LinhaProgramada};</li>
 *   <li>{@link #IMPREVISTA} — avulsa, criada pontualmente (sem linha).</li>
 * </ul>
 */
public enum TipoViagem {

    ROTINEIRA("Rotineira"),
    IMPREVISTA("Imprevista");

    private final String rotulo;

    TipoViagem(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
