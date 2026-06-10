package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Semeia o usuário administrador (gerente) inicial na primeira execução.
 *
 * <p><strong>Por que em Java e não no Flyway?</strong> A senha precisa ser um
 * hash BCrypt compatível com o {@code PasswordEncoder} do Spring. Gerar o hash
 * aqui usa exatamente o mesmo encoder da autenticação — garantindo que o login
 * funcione — e evita depender da extensão {@code pgcrypto} no banco compartilhado.
 *
 * <p>É <strong>idempotente</strong>: se o administrador já existir, nada é feito.
 *
 * <p>Credenciais padrão (TROCAR em produção):
 * <ul>
 *   <li>telefone: {@code 83999999999} (ou e-mail {@code admin@caladrius.local})</li>
 *   <li>senha: {@code admin123}</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String ADMIN_TELEFONE = "83999999999";
    private static final String ADMIN_EMAIL = "admin@caladrius.local";
    private static final String ADMIN_SENHA = "admin123";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(ADMIN_TELEFONE)) {
            log.info("Administrador já existe — seed ignorado.");
            return;
        }

        Usuario admin = new Usuario();
        admin.setNomeCompleto("Administrador");
        admin.setTelefone(ADMIN_TELEFONE);
        admin.setEmail(ADMIN_EMAIL);
        admin.setHashSenha(passwordEncoder.encode(ADMIN_SENHA));
        admin.setStatus(StatusUsuario.ATIVO);
        admin.setPapeis(EnumSet.of(Papel.GERENTE));

        usuarioRepository.save(admin);
        log.info("Usuário administrador (gerente) criado — telefone {} / e-mail {}.",
                ADMIN_TELEFONE, ADMIN_EMAIL);
    }
}
