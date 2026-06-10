package br.ufpb.dsc.caladrius.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Rotas públicas: login, cadastro, health check e estáticos.
                        // /ping é o contrato público exigido pela disciplina (200 JSON).
                        .requestMatchers(
                                "/login", "/registrar",
                                "/ping", "/actuator/health",
                                "/webjars/**", "/css/**", "/js/**"
                        ).permitAll()
                        // Módulos de gestão — exclusivos do gerente.
                        .requestMatchers(
                                "/veiculos/**", "/cidades/**",
                                "/usuarios/**", "/viagens/**"
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
                        .permitAll()
                )

                // CSRF permanece ATIVO para os formulários de página inteira
                // (login e cadastro injetam o token via Thymeleaf). É desabilitado
                // apenas para os endpoints HTMX de mutação (POST/PUT/DELETE via AJAX),
                // seguindo o mesmo padrão do boilerplate.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/veiculos/**", "/cidades/**",
                                "/usuarios/**", "/viagens/**"
                        )
                );

        return http.build();
    }
}
