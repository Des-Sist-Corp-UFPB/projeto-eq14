package br.ufpb.dsc.caladrius.web;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Base dos testes de integração da camada web (HTTP).
 *
 * <p>Sobe o contexto completo do Spring Boot contra um PostgreSQL real
 * (Testcontainers, container <strong>único</strong> compartilhado entre as classes
 * para reaproveitar o cache do contexto do Spring) e expõe um {@link MockMvc} para
 * exercer os endpoints de verdade — controladores, serviços, repositórios,
 * segurança e renderização dos templates Thymeleaf.
 *
 * <p>Os helpers de autenticação criam um principal {@link UsuarioAutenticado} com os
 * papéis desejados, permitindo testar as regras de autorização (RBAC) por requisição.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegracaoWebTestBase {

    // Container único (singleton): iniciado uma vez por JVM e reusado por todas as
    // subclasses; com a mesma configuração, o contexto do Spring também é cacheado.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Gera telefones únicos para evitar colisão na unicidade entre os testes. */
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UsuarioRepository usuarioRepository;

    // ===================== Autenticação (principal sintético) =====================

    /** Principal autenticado com os papéis informados (sem persistir no banco). */
    protected RequestPostProcessor como(Papel... papeis) {
        Usuario u = new Usuario();
        u.setId(java.util.UUID.randomUUID());
        u.setNomeCompleto("Usuário de Teste");
        u.setStatus(StatusUsuario.ATIVO);
        u.setPapeis(EnumSet.copyOf(List.of(papeis)));
        return autenticar(u);
    }

    protected RequestPostProcessor comoGerente() { return como(Papel.GERENTE); }
    protected RequestPostProcessor comoMotorista() { return como(Papel.MOTORISTA); }
    protected RequestPostProcessor comoPassageiro() { return como(Papel.PASSAGEIRO); }
    protected RequestPostProcessor comoSysadmin() { return como(Papel.SYSADMIN); }

    /** Principal de PASSAGEIRO com perfil incompleto (login social sem telefone) — SPEC-08. */
    protected RequestPostProcessor comoPassageiroIncompleto() {
        Usuario u = new Usuario();
        u.setId(java.util.UUID.randomUUID());
        u.setNomeCompleto("Novo Google");
        u.setStatus(StatusUsuario.ATIVO);
        u.setPerfilIncompleto(true);
        u.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        return autenticar(u);
    }

    /** Constrói o pós-processador de autenticação a partir de um usuário. */
    protected RequestPostProcessor autenticar(Usuario u) {
        UsuarioAutenticado principal = new UsuarioAutenticado(u);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    // ===================== Persistência de apoio =====================

    /** Persiste um usuário real (necessário quando o endpoint o recarrega do banco). */
    protected Usuario persistir(String nome, boolean perfilIncompleto, Papel... papeis) {
        Usuario u = new Usuario();
        u.setNomeCompleto(nome);
        u.setTelefone(perfilIncompleto ? null : "8390000" + SEQ.getAndIncrement());
        u.setStatus(StatusUsuario.ATIVO);
        u.setPerfilIncompleto(perfilIncompleto);
        u.setPapeis(EnumSet.copyOf(List.of(papeis)));
        return usuarioRepository.save(u);
    }
}
