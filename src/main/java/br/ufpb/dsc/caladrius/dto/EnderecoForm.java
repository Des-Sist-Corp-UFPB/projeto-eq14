package br.ufpb.dsc.caladrius.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Dados do endereço do passageiro (SPEC-07). Todos os campos são opcionais — o
 * endereço pode ser completado depois (regra RN-END-01); a obrigatoriedade para
 * solicitar viagem é aplicada no fluxo de solicitação (futuro).
 */
public record EnderecoForm(

        UUID municipioId,

        @Size(max = 120, message = "Bairro muito longo")
        String bairro,

        @Size(max = 180, message = "Logradouro muito longo")
        String logradouro,

        @Size(max = 20)
        String numero,

        @Size(max = 120)
        String complemento,

        @Size(max = 180, message = "Ponto de referência muito longo")
        String pontoReferencia,

        @Size(max = 9, message = "CEP inválido")
        String cep

) {
    /** Verdadeiro se o formulário não traz nenhum dado de endereço. */
    public boolean vazio() {
        return municipioId == null
                && vazio(bairro) && vazio(logradouro) && vazio(numero)
                && vazio(complemento) && vazio(pontoReferencia) && vazio(cep);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
