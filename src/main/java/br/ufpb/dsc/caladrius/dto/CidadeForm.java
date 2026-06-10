package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.enums.TipoCidade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de criação/edição de uma {@code Cidade}.
 */
public record CidadeForm(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "A UF é obrigatória")
        @Size(min = 2, max = 2, message = "A UF deve ter 2 letras (ex.: PB)")
        String uf,

        @NotNull(message = "O tipo é obrigatório")
        TipoCidade tipo

) {
}
