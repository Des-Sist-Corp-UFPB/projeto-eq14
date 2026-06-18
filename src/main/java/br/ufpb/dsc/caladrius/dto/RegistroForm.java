package br.ufpb.dsc.caladrius.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Dados do auto-cadastro público (passageiro).
 *
 * <p>Conforme o redesenho v3: o <strong>telefone</strong> é obrigatório; o
 * <strong>e-mail</strong> é opcional, mas, quando informado, também serve para
 * entrar e recuperar a conta. O CPF é opcional (validado no serviço quando
 * preenchido).
 *
 * <p>SPEC-07: o <strong>endereço é opcional</strong> no cadastro (pode ser
 * completado depois no perfil); a obrigatoriedade vale na solicitação de viagem.
 */
public record RegistroForm(

        @NotBlank(message = "Informe seu nome completo")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nomeCompleto,

        @NotBlank(message = "Informe seu telefone")
        @Size(max = 20, message = "Telefone inválido")
        String telefone,

        @Size(max = 14, message = "CPF inválido")
        String cpf,

        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email,

        @NotBlank(message = "Crie uma senha")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres")
        String senha,

        // ----- Endereço (opcional, SPEC-07) -----
        UUID municipioId,
        @Size(max = 120) String bairro,
        @Size(max = 180) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 120) String complemento,
        @Size(max = 180) String pontoReferencia,
        @Size(max = 9) String cep

) {
    /** Converte os campos de endereço deste cadastro para um {@link EnderecoForm}. */
    public EnderecoForm paraEnderecoForm() {
        return new EnderecoForm(municipioId, bairro, logradouro, numero, complemento, pontoReferencia, cep);
    }
}
