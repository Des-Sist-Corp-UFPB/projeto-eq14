package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.service.ConfiguracaoService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aplica o tempo de sessão <strong>dinâmico</strong> (DT-10).
 *
 * <p>O {@code application.yml} congela o timeout no boot (o Tomcat o lê uma única
 * vez). Para que o SYSADMIN altere o valor em runtime sem redeploy, este listener
 * define o {@code maxInactiveInterval} de cada sessão recém-criada a partir do
 * valor guardado no banco ({@link ConfiguracaoService#getTimeoutSessaoMinutos()}).
 */
@Configuration
public class SessaoConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> timeoutSessaoListener(
            ConfiguracaoService configuracaoService) {
        return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                int minutos = configuracaoService.getTimeoutSessaoMinutos();
                se.getSession().setMaxInactiveInterval(minutos * 60);
            }
        });
    }
}
