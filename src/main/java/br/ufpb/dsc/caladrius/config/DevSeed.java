package br.ufpb.dsc.caladrius.config;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.domain.enums.TipoViagem;
import br.ufpb.dsc.caladrius.domain.enums.TipoVeiculo;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Semeia dados de <strong>demonstração apenas em DEV</strong> ({@code @Profile("dev")})
 * para exercitar a jornada do passageiro (SPEC-09) logo após {@code mvn spring-boot:run},
 * sem precisar cadastrar tudo na mão.
 *
 * <p>Nunca roda em produção (perfil {@code prod}). É <strong>idempotente</strong>: cada
 * item é criado só se ainda não existir (por telefone, placa, ou linha/viagem equivalente),
 * então pode rodar a cada boot sem duplicar.
 *
 * <p>Cria, usando as cidades semeadas na V3 (Patos = origem; João Pessoa / Campina Grande /
 * Cajazeiras = destinos):
 * <ul>
 *   <li><strong>2 passageiros</strong> — {@code 83977777777} e {@code 83966666666} (senha {@code passageiro123});</li>
 *   <li><strong>2 motoristas</strong> — {@code 83955555551} e {@code 83955555552} (senha {@code motorista123});</li>
 *   <li><strong>2 veículos</strong> — um carro comum e uma van acessível (PCD);</li>
 *   <li><strong>3 linhas</strong>: Campina (seg–qua), Campina (qui–sáb) e João Pessoa (seg/qua/sex);</li>
 *   <li><strong>1 viagem imprevista</strong> numa data futura (para os painéis do gestor).</li>
 * </ul>
 *
 * <p>Para um banco de dev limpo (só estes dados), recrie o volume:
 * {@code docker compose -f docker/docker-compose.dev.yml down -v && ... up postgres}.
 */
@Component
@Profile("dev")
@Order(10) // após o DataInitializer (admin)
public class DevSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    private static final String SENHA_PASSAGEIRO = "passageiro123";
    private static final String SENHA_MOTORISTA = "motorista123";
    private static final String ADMIN_TELEFONE = "83999999999";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CidadeRepository cidadeRepository;
    private final LinhaProgramadaRepository linhaRepository;
    private final VeiculoRepository veiculoRepository;
    private final ViagemRepository viagemRepository;

    public DevSeed(UsuarioRepository usuarioRepository,
                   PasswordEncoder passwordEncoder,
                   CidadeRepository cidadeRepository,
                   LinhaProgramadaRepository linhaRepository,
                   VeiculoRepository veiculoRepository,
                   ViagemRepository viagemRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.cidadeRepository = cidadeRepository;
        this.linhaRepository = linhaRepository;
        this.veiculoRepository = veiculoRepository;
        this.viagemRepository = viagemRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Cidade patos = cidade("Patos");
        Cidade campina = cidade("Campina Grande");
        Cidade joaoPessoa = cidade("João Pessoa");
        Cidade cajazeiras = cidade("Cajazeiras");

        // Pessoas
        garantirUsuario("83977777777", "Passageiro de Teste", SENHA_PASSAGEIRO, Papel.PASSAGEIRO);
        garantirUsuario("83966666666", "Passageiro Dois", SENHA_PASSAGEIRO, Papel.PASSAGEIRO);
        Usuario motorista1 = garantirUsuario("83955555551", "Motorista Um", SENHA_MOTORISTA, Papel.MOTORISTA);
        garantirUsuario("83955555552", "Motorista Dois", SENHA_MOTORISTA, Papel.MOTORISTA);

        // Frota (a van é acessível — útil para a futura solicitação PCD, RN-SOL-08)
        Veiculo carro = garantirVeiculo("DEV1A01", "Fiat", "Doblò", 2022, TipoVeiculo.CARRO, 4, false);
        garantirVeiculo("DEV2A02", "Renault", "Master", 2023, TipoVeiculo.VAN, 12, true);

        // 3 linhas pré-definidas
        garantirLinha(patos, campina, LocalTime.of(6, 0), LocalTime.of(8, 0), LocalTime.of(12, 0),
                EnumSet.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA));
        garantirLinha(patos, campina, LocalTime.of(13, 0), LocalTime.of(15, 0), LocalTime.of(18, 0),
                EnumSet.of(DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO));
        garantirLinha(patos, joaoPessoa, LocalTime.of(7, 0), LocalTime.of(9, 30), LocalTime.of(16, 0),
                EnumSet.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA));

        // 1 viagem imprevista futura (criada pelo administrador/gerente)
        Usuario admin = usuarioRepository.findByTelefoneAndRemovidoEmIsNull(ADMIN_TELEFONE).orElse(null);
        garantirViagemImprevista(patos, cajazeiras != null ? cajazeiras : joaoPessoa, motorista1, carro, admin);
    }

    // ----------------------------------------------------------- helpers idempotentes

    private Usuario garantirUsuario(String telefone, String nome, String senha, Papel... papeis) {
        return usuarioRepository.findByTelefoneAndRemovidoEmIsNull(telefone).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNomeCompleto(nome);
            u.setTelefone(telefone);
            u.setHashSenha(passwordEncoder.encode(senha));
            u.setStatus(StatusUsuario.ATIVO);
            u.setPapeis(EnumSet.copyOf(List.of(papeis)));
            Usuario salvo = usuarioRepository.save(u);
            log.info("[DEV] Usuário criado — {} / {} ({}).", nome, telefone, List.of(papeis));
            return salvo;
        });
    }

    private Veiculo garantirVeiculo(String placa, String marca, String modelo, int ano,
                                    TipoVeiculo tipo, int capacidade, boolean acessivel) {
        return veiculoRepository.findByRemovidoEmIsNullOrderByPlacaAsc().stream()
                .filter(v -> v.getPlaca().equalsIgnoreCase(placa))
                .findFirst()
                .orElseGet(() -> {
                    Veiculo salvo = veiculoRepository.save(
                            new Veiculo(placa, marca, modelo, ano, tipo, capacidade, acessivel));
                    log.info("[DEV] Veículo criado — {} {} ({}).", placa, modelo, tipo);
                    return salvo;
                });
    }

    /** Cria a linha se ainda não houver uma para o mesmo destino + horário de saída. */
    private void garantirLinha(Cidade origem, Cidade destino, LocalTime saida, LocalTime chegada,
                               LocalTime retorno, EnumSet<DiaSemana> dias) {
        if (destino == null) {
            return;
        }
        boolean existe = linhaRepository.findAll().stream().anyMatch(l ->
                l.getCidadeDestino() != null
                        && l.getCidadeDestino().getNome().equalsIgnoreCase(destino.getNome())
                        && saida.equals(l.getHorarioSaida()));
        if (existe) {
            return;
        }
        LinhaProgramada linha = new LinhaProgramada();
        linha.setCidadeOrigem(origem);
        linha.setCidadeDestino(destino);
        linha.setHorarioSaida(saida);
        linha.setHorarioChegada(chegada);
        linha.setHorarioRetorno(retorno);
        linha.setAtiva(true);
        linha.setDias(dias);
        linhaRepository.save(linha);
        log.info("[DEV] Linha criada — {} {} {}.", destino.getNome(), saida, dias);
    }

    /** Cria a viagem imprevista se ainda não houver uma para o mesmo destino + data. */
    private void garantirViagemImprevista(Cidade origem, Cidade destino, Usuario motorista,
                                          Veiculo veiculo, Usuario criadoPor) {
        if (destino == null || motorista == null || veiculo == null || criadoPor == null) {
            log.warn("[DEV] Viagem imprevista não criada (faltou destino/motorista/veículo/gestor).");
            return;
        }
        LocalDate data = LocalDate.now().plusDays(5);
        boolean existe = viagemRepository.findAll().stream().anyMatch(v ->
                v.getTipo() == TipoViagem.IMPREVISTA
                        && data.equals(v.getDataViagem())
                        && v.getCidadeDestino() != null
                        && v.getCidadeDestino().getNome().equalsIgnoreCase(destino.getNome()));
        if (existe) {
            return;
        }
        Viagem viagem = new Viagem();
        viagem.setTipo(TipoViagem.IMPREVISTA);
        viagem.setVeiculo(veiculo);
        viagem.setMotorista(motorista);
        viagem.setCidadeOrigem(origem);
        viagem.setCidadeDestino(destino);
        viagem.setDataViagem(data);
        viagem.setHorarioSaida(LocalTime.of(9, 0));
        viagem.setHorarioChegada(LocalTime.of(11, 0));
        viagem.setStatus(StatusViagem.PLANEJADA);
        viagem.setCriadoPor(criadoPor);
        viagemRepository.save(viagem);
        log.info("[DEV] Viagem imprevista criada — {} em {}.", destino.getNome(), data);
    }

    private Cidade cidade(String nome) {
        return cidadeRepository.findAllByOrderByNomeAsc().stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nome))
                .findFirst()
                .orElse(null);
    }
}
