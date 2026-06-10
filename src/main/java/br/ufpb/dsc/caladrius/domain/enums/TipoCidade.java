package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Distingue a cidade de origem (município de partida dos pacientes) do
 * destino metropolitano (onde ficam as consultas/serviços de saúde).
 */
public enum TipoCidade {

    ORIGEM("Origem"),
    METROPOLITANA("Metropolitana");

    private final String rotulo;

    TipoCidade(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
