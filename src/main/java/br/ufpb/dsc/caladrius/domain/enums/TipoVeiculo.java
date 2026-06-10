package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Classificação de um veículo da frota.
 */
public enum TipoVeiculo {

    CARRO("Carro"),
    VAN("Van"),
    MICRO_ONIBUS("Micro-ônibus"),
    ONIBUS("Ônibus"),
    AMBULANCIA("Ambulância");

    private final String rotulo;

    TipoVeiculo(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
