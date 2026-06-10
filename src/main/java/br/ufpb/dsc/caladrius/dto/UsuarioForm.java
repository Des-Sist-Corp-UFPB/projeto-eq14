package br.ufpb.dsc.caladrius.dto;

import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Dados de criação/edição de um {@code Usuario} pelo gerente.
 *
 * <p>A {@code senha} é obrigatória na criação e opcional na edição (em branco =
 * mantém a atual) — essa regra é aplicada no serviço. Os {@code papeis} definem
 * o acesso (RBAC).
 */
public record UsuarioForm(

        @NotBlank(message = "O nome completo é obrigatório")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nomeCompleto,

        @NotBlank(message = "O telefone é obrigatório")
        @Size(max = 20, message = "Telefone inválido")
        String telefone,

        @Size(max = 14, message = "CPF inválido")
        String cpf,

        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email,

        @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres")
        String senha,

        Set<Papel> papeis,

        StatusUsuario status

) {
}
