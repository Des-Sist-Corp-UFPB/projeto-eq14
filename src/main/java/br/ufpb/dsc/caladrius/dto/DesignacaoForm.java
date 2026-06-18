package br.ufpb.dsc.caladrius.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Designação de veículo + motorista a uma linha num dia → materializa a viagem
 * rotineira (SPEC-06, FR-VIA-10).
 */
public record DesignacaoForm(

        @NotNull UUID linhaId,

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataViagem,

        @NotNull(message = "Selecione o veículo") UUID veiculoId,

        @NotNull(message = "Selecione o motorista") UUID motoristaId

) {
}
