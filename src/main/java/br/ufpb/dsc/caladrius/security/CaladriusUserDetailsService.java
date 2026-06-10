package br.ufpb.dsc.caladrius.security;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega usuários do banco para o Spring Security, substituindo o
 * {@code InMemoryUserDetailsManager} do boilerplate.
 *
 * <p><strong>Login flexível (redesenho v3):</strong> o mesmo campo aceita
 * <em>e-mail OU telefone</em>. Detectamos o formato automaticamente: se o valor
 * contém "@", buscamos por e-mail; caso contrário, normalizamos para dígitos e
 * buscamos por telefone.
 *
 * <p>A verificação da senha é feita pelo Spring (via {@code PasswordEncoder}),
 * não aqui. Usuários não-ativos têm {@code isEnabled() == false} e são barrados.
 */
@Service
public class CaladriusUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CaladriusUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Localiza o usuário pelo identificador digitado no login (e-mail ou telefone).
     *
     * @param identificador e-mail ou telefone informado no formulário
     * @return os dados de autenticação do usuário
     * @throws UsernameNotFoundException se nenhum usuário ativo corresponder
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        String entrada = identificador == null ? "" : identificador.trim();

        Usuario usuario = Documentos.pareceEmail(entrada)
                ? usuarioRepository.findByEmailIgnoreCaseAndRemovidoEmIsNull(entrada).orElse(null)
                : usuarioRepository.findByTelefoneAndRemovidoEmIsNull(Documentos.apenasDigitos(entrada)).orElse(null);

        if (usuario == null) {
            // Mensagem genérica — não revela se o problema foi identificador ou senha.
            throw new UsernameNotFoundException("Credenciais inválidas");
        }
        return new UsuarioAutenticado(usuario);
    }
}
