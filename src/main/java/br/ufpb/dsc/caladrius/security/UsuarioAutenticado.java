package br.ufpb.dsc.caladrius.security;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapta um {@link Usuario} do domínio para o contrato {@link UserDetails} do
 * Spring Security.
 *
 * <p><strong>Principal único (SPEC-08):</strong> esta classe implementa também
 * {@link OidcUser}, de modo que tanto o login por senha ({@code formLogin}) quanto
 * o login social ({@code oauth2Login}) produzem o <em>mesmo</em> tipo de principal.
 * Assim, {@code @AuthenticationPrincipal UsuarioAutenticado} e
 * {@code sec:authentication="principal.nomeCompleto"} continuam funcionando para
 * ambos os fluxos. No login por senha, os dados OIDC ({@link #idToken}/{@link #userInfo})
 * são nulos/vazios.
 *
 * <p>Expõe campos extras ({@link #getId()}, {@link #getNomeCompleto()}) usados nos
 * templates — por exemplo, a navbar mostra o nome do usuário logado.
 */
public class UsuarioAutenticado implements UserDetails, OidcUser {

    private final UUID id;
    private final String nomeCompleto;
    private final String telefone;
    private final String email;
    private final String hashSenha;
    private final boolean ativo;
    private final boolean perfilIncompleto;
    private final List<GrantedAuthority> authorities;

    /** Dados OIDC — presentes apenas no login social; nulos no login por senha. */
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public UsuarioAutenticado(Usuario usuario) {
        this(usuario, null, null);
    }

    public UsuarioAutenticado(Usuario usuario, OidcIdToken idToken, OidcUserInfo userInfo) {
        this.id = usuario.getId();
        this.nomeCompleto = usuario.getNomeCompleto();
        this.telefone = usuario.getTelefone();
        this.email = usuario.getEmail();
        this.hashSenha = usuario.getHashSenha();
        this.ativo = usuario.isAtivo();
        this.perfilIncompleto = usuario.isPerfilIncompleto();
        this.authorities = usuario.getPapeis().stream()
                .map(Papel::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        this.idToken = idToken;
        this.userInfo = userInfo;
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

    /**
     * Identificador canônico para o Spring Security. Normalmente o telefone; quando
     * nulo (conta de login social com perfil incompleto), recai no e-mail e, por fim,
     * no id — garantindo um username não-nulo.
     */
    @Override
    public String getUsername() {
        if (telefone != null) {
            return telefone;
        }
        return email != null ? email : String.valueOf(id);
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

    // ===================== Contrato OidcUser / OAuth2User =====================

    @Override
    public Map<String, Object> getClaims() {
        return idToken != null ? idToken.getClaims() : Map.of();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return getClaims();
    }

    /** Nome único do principal para o Spring (estável): o id do usuário. */
    @Override
    public String getName() {
        return String.valueOf(id);
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

    /** {@code true} se faltam dados obrigatórios (telefone) — ver SPEC-08. */
    public boolean isPerfilIncompleto() {
        return perfilIncompleto;
    }
}
