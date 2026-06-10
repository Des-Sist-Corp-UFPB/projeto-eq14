package br.ufpb.dsc.caladrius.domain.enums;

/**
 * Situação operacional de um veículo.
 */
public enum StatusVeiculo {

    DISPONIVEL("Disponível"),
    EM_VIAGEM("Em viagem"),
    MANUTENCAO("Manutenção"),
    INATIVO("Inativo");

    private final String rotulo;

    StatusVeiculo(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Rótulo amigável para exibição na interface. */
    public String getRotulo() {
        return rotulo;
    }
}
