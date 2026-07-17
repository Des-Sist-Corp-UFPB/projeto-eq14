package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusUsuario;
import br.ufpb.dsc.caladrius.dto.EnderecoForm;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.util.Documentos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

/**
 * Auto-cadastro (onboarding) do passageiro pelo WhatsApp (SPEC-11).
 *
 * <p>Cria um {@code Usuario} <strong>PASSAGEIRO ATIVO sem senha</strong>
 * (decisão D2): o telefone verificado pelo WhatsApp é a identidade; a conta
 * <strong>não autentica na web</strong> enquanto não definir senha (Art. VIII —
 * sem credencial, não há login). O acesso web é obtido depois pela opção
 * "Acesso à plataforma" do bot ({@link ConviteService#gerarAcessoPlataforma}).
 *
 * <p>Reaproveita o {@link EnderecoService} (SPEC-07) para o endereço de embarque.
 * É um serviço de domínio puro — não conhece o WhatsApp; o bot o invoca.
 */
@Service
@Transactional(readOnly = true)
public class OnboardingService {

    private final UsuarioRepository usuarioRepository;
    private final EnderecoService enderecoService;
    private final AuditoriaService auditoriaService;

    public OnboardingService(UsuarioRepository usuarioRepository,
                             EnderecoService enderecoService,
                             AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.enderecoService = enderecoService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Cadastra um passageiro a partir dos dados mínimos coletados pelo bot (D3):
     * nome, endereço de embarque e CPF. O telefone já vem normalizado (dígitos).
     *
     * @return o passageiro criado (ATIVO, sem senha)
     * @throws RegraNegocioException dados inválidos ou telefone/CPF já cadastrado
     */
    @Transactional
    public Usuario cadastrarPassageiro(String telefone, String nome, String endereco, String cpf) {
        String tel = Documentos.apenasDigitos(telefone);
        if (!StringUtils.hasText(tel)) {
            throw new RegraNegocioException("Telefone inválido");
        }
        if (usuarioRepository.existsByTelefoneAndRemovidoEmIsNull(tel)) {
            throw new RegraNegocioException("Este telefone já está cadastrado");
        }
        if (!StringUtils.hasText(nome)) {
            throw new RegraNegocioException("Informe o seu nome completo");
        }
        String cpfDigitos = Documentos.apenasDigitos(cpf);
        if (!Documentos.cpfValido(cpfDigitos)) {
            throw new RegraNegocioException("CPF inválido");
        }
        if (usuarioRepository.existsByCpfAndRemovidoEmIsNull(cpfDigitos)) {
            throw new RegraNegocioException("Este CPF já está cadastrado");
        }

        Usuario passageiro = new Usuario();
        passageiro.setNomeCompleto(nome.trim());
        passageiro.setTelefone(tel);
        passageiro.setCpf(cpfDigitos);
        // Sem senha (D2): a conta é ATIVA para o bot, mas não loga na web até
        // definir senha via "Acesso à plataforma".
        passageiro.setHashSenha(null);
        passageiro.setStatus(StatusUsuario.ATIVO);
        passageiro.setPapeis(EnumSet.of(Papel.PASSAGEIRO));
        passageiro = usuarioRepository.save(passageiro);

        // Endereço de embarque (onde o motorista busca) — texto livre no logradouro.
        if (StringUtils.hasText(endereco)) {
            enderecoService.salvar(passageiro.getId(),
                    new EnderecoForm(null, null, endereco.trim(), null, null, null, null));
        }

        auditoriaService.registrarOperacao("PASSAGEIRO_AUTOCADASTRO", "Usuario",
                passageiro.getId().toString(), "Cadastro pelo WhatsApp — " + nome.trim());
        return passageiro;
    }
}
