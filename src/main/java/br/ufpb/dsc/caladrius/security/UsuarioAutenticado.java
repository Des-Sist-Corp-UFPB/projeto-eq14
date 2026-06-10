package br.ufpb.dsc.caladrius.security;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapta um {@link Usuario} do domínio para o contrato {@link UserDetails} do
 * Spring Security.
 *
 * <p>Expõe campos extras ({@link #getId()}, {@link #getNomeCompleto()}) usados
 * nos templates — por exemplo, a navbar mostra o nome do usuário logado via
 * {@code sec:authentication="principal.nomeCompleto"}.
 *
 * <p>{@link #getUsername()} devolve o telefone (identificador canônico, sempre
 * presente), independentemente de o login ter sido feito por e-mail ou telefone.
 */
public class UsuarioAutenticado implements UserDetails {

    private final UUID id;
    private final String nomeCompleto;
    private final String telefone;
    private final String email;
    private final String hashSenha;
    private final boolean ativo;
    private final List<GrantedAuthority> authorities;

    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.nomeCompleto = usuario.getNomeCompleto();
        this.telefone = usuario.getTelefone();
        this.email = usuario.getEmail();
        this.hashSenha = usuario.getHashSenha();
        this.ativo = usuario.isAtivo();
        this.authorities = usuario.getPapeis().stream()
                .map(Papel::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    // ===================== Contrato UserDetails =====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return hashSenha;
    }

    @Override
    public String getUsername() {
        return telefone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    // ===================== Extras para a aplicação =====================

    public UUID getId() {
        return id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }
}
