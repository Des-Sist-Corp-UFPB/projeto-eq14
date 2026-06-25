package br.ufpb.dsc.caladrius.security;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.ProvedorOauth;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.IdentidadeOauthService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * {@code OAuth2UserService} customizado para o login social (OIDC) — SPEC-08.
 *
 * <p>Carrega o {@link OidcUser} padrão (claims do provedor), resolve no
 * {@link Usuario} do sistema via {@link IdentidadeOauthService} (identidade →
 * e-mail verificado → auto-provisão) e devolve um {@link UsuarioAutenticado},
 * mantendo o principal único da aplicação.
 *
 * <p>Hoje só o Google está registrado; o {@code registrationId} é mapeado para
 * {@link ProvedorOauth}. Contas que não podem autenticar (suspensas/removidas)
 * resultam em {@link OAuth2AuthenticationException}, exibida na tela de login.
 */
@Service
public class CaladriusOidcUserService extends OidcUserService {

    private final IdentidadeOauthService identidadeOauthService;

    public CaladriusOidcUserService(IdentidadeOauthService identidadeOauthService) {
        this.identidadeOauthService = identidadeOauthService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidc = super.loadUser(userRequest);
        ProvedorOauth provedor = mapearProvedor(
                userRequest.getClientRegistration().getRegistrationId());

        try {
            Usuario usuario = identidadeOauthService.resolverLogin(
                    provedor,
                    oidc.getSubject(),
                    oidc.getEmail(),
                    Boolean.TRUE.equals(oidc.getEmailVerified()),
                    oidc.getFullName());
            return new UsuarioAutenticado(usuario, oidc.getIdToken(), oidc.getUserInfo());
        } catch (RegraNegocioException e) {
            // Bloqueio de negócio (ex.: conta suspensa) → erro de autenticação OAuth.
            throw new OAuth2AuthenticationException(new OAuth2Error("acesso_negado", e.getMessage(), null), e);
        }
    }

    private ProvedorOauth mapearProvedor(String registrationId) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return ProvedorOauth.GOOGLE;
        }
        throw new OAuth2AuthenticationException(
                new OAuth2Error("provedor_nao_suportado", "Provedor não suportado: " + registrationId, null));
    }
}
