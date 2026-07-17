package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.security.CaladriusOidcUserService;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import br.ufpb.dsc.caladrius.service.AuditoriaService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Configuração de segurança do CALADRIUS (Spring Security 6).
 *
 * <p><strong>Autenticação no banco:</strong> diferente do boilerplate (que usava
 * {@code InMemoryUserDetailsManager}), aqui os usuários vêm do PostgreSQL. O
 * Spring Boot conecta automaticamente o nosso {@code CaladriusUserDetailsService}
 * ao {@link PasswordEncoder} abaixo. O login aceita <em>e-mail OU telefone</em>.
 *
 * <p><strong>Autorização (RBAC):</strong> os módulos de cadastro/operação exigem
 * o papel {@code GERENTE}; as rotas públicas ({@code /login}, {@code /registrar},
 * {@code /ping}) ficam liberadas; o restante exige apenas autenticação.
 *
 * @author Equipe eq14 — DSC/UFPB
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Algoritmo de hash de senhas — BCrypt (adaptativo, com salt automático).
     * É usado tanto na autenticação quanto no cadastro de usuários.
     *
     * @return codificador BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cadeia de filtros de segurança HTTP.
     *
     * @param http construtor de configuração
     * @return cadeia configurada
     * @throws Exception em erro de configuração
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuditoriaService auditoriaService,
                                           CaladriusOidcUserService oidcUserService,
                                           ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Rotas públicas: login, cadastro, health check e estáticos.
                        // /ping é o contrato público exigido pela disciplina (200 JSON).
                        .requestMatchers(
                                "/login", "/registrar", "/ativar",
                                "/ping", "/actuator/health",
                                "/webjars/**", "/css/**", "/js/**"
                        ).permitAll()
                        // Webhook da Evolution (SPEC-10): chamada servidor-a-servidor,
                        // autenticada pelo header X-Webhook-Token no controller (RN-WPP-03).
                        .requestMatchers("/webhooks/whatsapp").permitAll()
                        // Administração do sistema — exclusiva do SYSADMIN (papel isolado).
                        .requestMatchers("/admin/**").hasRole("SYSADMIN")
                        // Visão do motorista — exclusiva do MOTORISTA.
                        .requestMatchers("/minhas-viagens/**").hasRole("MOTORISTA")
                        // Visão do passageiro (solicitar transporte) — exclusiva do PASSAGEIRO.
                        .requestMatchers("/solicitacoes/**").hasRole("PASSAGEIRO")
                        // Módulos de gestão — exclusivos do gerente.
                        .requestMatchers(
                                "/veiculos/**", "/cidades/**",
                                "/usuarios/**", "/viagens/**", "/linhas/**",
                                "/historico/**", "/analise/**", "/whatsapp/**",
                                // Avaliação das solicitações sob demanda (SPEC-11).
                                "/gestao/**"
                        ).hasRole("GERENTE")
                        // Qualquer outra rota exige apenas estar autenticado.
                        .anyRequest().authenticated()
                )

                // Página de login customizada (e-mail OU telefone + senha).
                .formLogin(form -> form
                        .loginPage("/login")
                        // Após autenticar, vai sempre para o painel inicial.
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        // Auditoria de logout (#19, categoria SEGURANCA).
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null
                                    && authentication.getPrincipal() instanceof UsuarioAutenticado u) {
                                auditoriaService.registrarSeguranca("LOGOUT", "SUCESSO",
                                        u.getId(), u.getNomeCompleto(), request.getRemoteAddr());
                            }
                        })
                        .permitAll()
                )

                // CSRF permanece ATIVO para os formulários de página inteira
                // (login e cadastro injetam o token via Thymeleaf). É desabilitado
                // apenas para os endpoints HTMX de mutação (POST/PUT/DELETE via AJAX),
                // seguindo o mesmo padrão do boilerplate.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/veiculos/**", "/cidades/**",
                                "/usuarios/**", "/viagens/**",
                                // Webhook servidor-a-servidor (SPEC-10): sem sessão/token
                                // CSRF — a autenticação é o X-Webhook-Token.
                                "/webhooks/whatsapp"
                        )
                );

        // Login social (Google/OIDC) — SPEC-08. Só é ativado quando há credenciais
        // configuradas (GOOGLE_CLIENT_ID/SECRET); sem elas, o Boot não cria o
        // ClientRegistrationRepository e o app sobe normalmente apenas com o formLogin.
        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
            );
        }

        // Após a autenticação/autorização, barra a navegação de contas com perfil
        // incompleto (login social sem telefone), levando-as a /conta/completar.
        http.addFilterAfter(new PerfilIncompletoFilter(), AuthorizationFilter.class);

        return http.build();
    }
}
