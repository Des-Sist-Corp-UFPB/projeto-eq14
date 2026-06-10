package br.ufpb.dsc.caladrius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação CALADRIUS.
 *
 * <p><strong>CALADRIUS</strong> — sistema de agendamento de vagas em transporte
 * público municipal de saúde: pacientes solicitam transporte para consultas na
 * cidade metropolitana mais próxima; o gestor organiza viagens, veículos e
 * motoristas.
 *
 * <p>A anotação {@code @SpringBootApplication} combina três anotações:
 * <ul>
 *   <li>{@code @Configuration} — permite declarar beans (métodos {@code @Bean}).</li>
 *   <li>{@code @EnableAutoConfiguration} — autoconfiguração do Spring Boot.</li>
 *   <li>{@code @ComponentScan} — escaneia este pacote e subpacotes em busca de
 *       componentes ({@code @Service}, {@code @Repository}, {@code @Controller}...).</li>
 * </ul>
 *
 * <p><strong>Disciplina:</strong> Desenvolvimento de Sistemas Corporativos (DSC)<br>
 * <strong>Professor:</strong> Rodrigo Rebouças — UFPB Campus IV — equipe eq14
 *
 * @author Equipe eq14 — DSC/UFPB
 */
@SpringBootApplication
public class CaladriusApplication {

    /**
     * Ponto de entrada da JVM.
     *
     * <p>{@code SpringApplication.run()} inicializa o contexto do Spring, cria os beans,
     * executa as migrações do Flyway e sobe o servidor embutido (Tomcat).
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(CaladriusApplication.class, args);
    }
}
