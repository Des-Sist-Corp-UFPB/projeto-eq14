package br.ufpb.dsc.caladrius.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Configuração do login social com Google (OAuth2/OIDC) — SPEC-08.
 *
 * <p>O {@link ClientRegistrationRepository} é criado <strong>apenas quando há
 * credenciais</strong> ({@code GOOGLE_CLIENT_ID} <em>não-vazio</em> no ambiente). Sem elas,
 * nenhum registro existe, o Boot não tenta validar a configuração OAuth e a
 * aplicação sobe normalmente apenas com o login por senha — o {@code SecurityConfig}
 * e a tela de login detectam a ausência via {@code ObjectProvider}/{@code googleHabilitado}.
 *
 * <p>A condição usa {@code @ConditionalOnExpression} (e não {@code @ConditionalOnProperty})
 * de propósito: em produção o Compose pode injetar a variável <strong>vazia</strong>
 * ({@code GOOGLE_CLIENT_ID=}); a expressão trata vazio como ausente, evitando que o
 * bean seja criado com client-id em branco (o que quebraria o boot).
 *
 * <p>Usa {@link CommonOAuth2Provider#GOOGLE}, que já traz os endpoints, o issuer e
 * os escopos padrão ({@code openid}, {@code profile}, {@code email}); só injetamos
 * o client-id/secret. A URI de redirecionamento padrão é
 * {@code {baseUrl}/login/oauth2/code/google}.
 */
@Configuration
@ConditionalOnExpression("'${GOOGLE_CLIENT_ID:}' != ''")
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
