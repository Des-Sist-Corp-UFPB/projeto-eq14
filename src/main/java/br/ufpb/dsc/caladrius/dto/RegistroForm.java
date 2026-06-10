package br.ufpb.dsc.caladrius.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados do auto-cadastro público (passageiro).
 *
 * <p>Conforme o redesenho v3: o <strong>telefone</strong> é obrigatório; o
 * <strong>e-mail</strong> é opcional, mas, quando informado, também serve para
 * entrar e recuperar a conta. O CPF é opcional (validado no serviço quando
 * preenchido).
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
        String senha

) {
}
