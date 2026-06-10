package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.enums.StatusVeiculo;
import br.ufpb.dsc.caladrius.domain.enums.TipoVeiculo;
import jakarta.validation.constraints.*;

/**
 * Dados de criação/edição de um {@code Veiculo}.
 *
 * <p>O campo {@code status} só é usado na edição; na criação o serviço aplica
 * {@code DISPONIVEL} por padrão.
 */
public record VeiculoForm(

        @NotBlank(message = "A placa é obrigatória")
        @Size(min = 7, max = 8, message = "A placa deve ter 7 caracteres (ex.: ABC1D23)")
        String placa,

        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 60, message = "A marca deve ter no máximo 60 caracteres")
        String marca,

        @NotBlank(message = "O modelo é obrigatório")
        @Size(max = 60, message = "O modelo deve ter no máximo 60 caracteres")
        String modelo,

        @NotNull(message = "O ano é obrigatório")
        @Min(value = 1950, message = "Ano inválido")
        @Max(value = 2100, message = "Ano inválido")
        Integer ano,

        @NotNull(message = "O tipo é obrigatório")
        TipoVeiculo tipo,

        @NotNull(message = "A capacidade é obrigatória")
        @Min(value = 1, message = "A capacidade deve ser ao menos 1")
        @Max(value = 100, message = "Capacidade máxima de 100 assentos")
        Integer capacidade,

        boolean possuiAcessibilidade,

        StatusVeiculo status

) {
}
