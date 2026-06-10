package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.dto.ViagemForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lógica de negócio das viagens. Resolve as referências (veículo, motorista,
 * cidade de destino) e valida as regras de criação.
 */
@Service
@Transactional(readOnly = true)
public class ViagemService {

    private final ViagemRepository viagemRepository;
    private final VeiculoRepository veiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CidadeRepository cidadeRepository;

    public ViagemService(ViagemRepository viagemRepository,
                         VeiculoRepository veiculoRepository,
                         UsuarioRepository usuarioRepository,
                         CidadeRepository cidadeRepository) {
        this.viagemRepository = viagemRepository;
        this.veiculoRepository = veiculoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cidadeRepository = cidadeRepository;
    }

    /** Lista viagens (com veículo/motorista/cidade já carregados) paginadas. */
    public Page<Viagem> listar(Pageable pageable) {
        return viagemRepository.listarComRelacionamentos(pageable);
    }

    /** Busca uma viagem pelo id ou lança {@link RecursoNaoEncontradoException}. */
    public Viagem buscarPorId(UUID id) {
        return viagemRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Viagem", id));
    }

    /**
     * Cria uma viagem.
     *
     * @param form          dados do formulário (referências por UUID + data/horários)
     * @param criadoPorId   id do gerente autenticado que está criando a viagem
     * @return a viagem criada
     */
    @Transactional
    public Viagem criar(ViagemForm form, UUID criadoPorId) {
        Veiculo veiculo = veiculoRepository.findByIdAndRemovidoEmIsNull(form.veiculoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", form.veiculoId()));

        Usuario motorista = usuarioRepository.findByIdAndRemovidoEmIsNull(form.motoristaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista", form.motoristaId()));
        if (!motorista.temPapel(Papel.MOTORISTA)) {
            throw new RegraNegocioException("O usuário selecionado não possui o papel de motorista");
        }

        Cidade cidadeDestino = cidadeRepository.findById(form.cidadeDestinoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", form.cidadeDestinoId()));

        Usuario criadoPor = usuarioRepository.findById(criadoPorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", criadoPorId));

        if (!form.horarioChegada().isAfter(form.horarioSaida())) {
            throw new RegraNegocioException("O horário de chegada deve ser após o horário de saída");
        }

        Viagem viagem = new Viagem();
        viagem.setVeiculo(veiculo);
        viagem.setMotorista(motorista);
        viagem.setCidadeDestino(cidadeDestino);
        viagem.setDataViagem(form.dataViagem());
        viagem.setHorarioSaida(form.horarioSaida());
        viagem.setHorarioChegada(form.horarioChegada());
        viagem.setStatus(StatusViagem.PLANEJADA);
        viagem.setCriadoPor(criadoPor);
        return viagemRepository.save(viagem);
    }

    /** Exclui uma viagem (e seus assentos, por cascade no banco). */
    @Transactional
    public void excluir(UUID id) {
        Viagem viagem = buscarPorId(id);
        viagemRepository.delete(viagem);
    }
}
