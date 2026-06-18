package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.Papel;
import br.ufpb.dsc.caladrius.domain.enums.StatusViagem;
import br.ufpb.dsc.caladrius.domain.enums.TipoViagem;
import br.ufpb.dsc.caladrius.dto.DesignacaoForm;
import br.ufpb.dsc.caladrius.dto.PainelSemana;
import br.ufpb.dsc.caladrius.dto.ViagemForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
    private final LinhaProgramadaRepository linhaRepository;
    private final ConfiguracaoService configuracaoService;
    private final AuditoriaService auditoriaService;

    public ViagemService(ViagemRepository viagemRepository,
                         VeiculoRepository veiculoRepository,
                         UsuarioRepository usuarioRepository,
                         CidadeRepository cidadeRepository,
                         LinhaProgramadaRepository linhaRepository,
                         ConfiguracaoService configuracaoService,
                         AuditoriaService auditoriaService) {
        this.viagemRepository = viagemRepository;
        this.veiculoRepository = veiculoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cidadeRepository = cidadeRepository;
        this.linhaRepository = linhaRepository;
        this.configuracaoService = configuracaoService;
        this.auditoriaService = auditoriaService;
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
        validarDisponibilidade(veiculo.getId(), motorista.getId(),
                form.dataViagem(), form.horarioSaida(), form.horarioChegada(), null);

        Viagem viagem = new Viagem();
        viagem.setVeiculo(veiculo);
        viagem.setMotorista(motorista);
        viagem.setCidadeDestino(cidadeDestino);
        viagem.setCidadeOrigem(sede());
        viagem.setTipo(TipoViagem.IMPREVISTA);
        viagem.setDataViagem(form.dataViagem());
        viagem.setHorarioSaida(form.horarioSaida());
        viagem.setHorarioChegada(form.horarioChegada());
        viagem.setStatus(StatusViagem.PLANEJADA);
        viagem.setCriadoPor(criadoPor);
        viagem = viagemRepository.save(viagem);
        auditoriaService.registrarOperacao("VIAGEM_CRIADA", "Viagem", viagem.getId().toString(),
                "Imprevista p/ " + cidadeDestino.getNome() + " em " + form.dataViagem());
        return viagem;
    }

    // ===================== SPEC-06 =====================

    /**
     * Designa veículo + motorista a uma linha numa data → materializa a viagem
     * rotineira (FR-VIA-10), com checagem de conflito (RN-VIA-08).
     */
    @Transactional
    public Viagem designar(DesignacaoForm form, UUID criadoPorId) {
        LinhaProgramada linha = linhaRepository.findById(form.linhaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Linha programada", form.linhaId()));
        if (!linha.isAtiva()) {
            throw new RegraNegocioException("Esta linha está desabilitada");
        }
        if (!linha.operaEm(DiaSemana.de(form.dataViagem().getDayOfWeek()))) {
            throw new RegraNegocioException("A linha não opera neste dia da semana");
        }
        if (viagemRepository.findByLinhaProgramada_IdAndDataViagem(linha.getId(), form.dataViagem()).isPresent()) {
            throw new RegraNegocioException("Esta linha já foi designada nesta data");
        }

        Veiculo veiculo = veiculoRepository.findByIdAndRemovidoEmIsNull(form.veiculoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", form.veiculoId()));
        Usuario motorista = usuarioRepository.findByIdAndRemovidoEmIsNull(form.motoristaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motorista", form.motoristaId()));
        if (!motorista.temPapel(Papel.MOTORISTA)) {
            throw new RegraNegocioException("O usuário selecionado não possui o papel de motorista");
        }
        Usuario criadoPor = usuarioRepository.findById(criadoPorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", criadoPorId));

        validarDisponibilidade(veiculo.getId(), motorista.getId(),
                form.dataViagem(), linha.getHorarioSaida(), linha.getHorarioChegada(), null);

        Viagem viagem = new Viagem();
        viagem.setLinhaProgramada(linha);
        viagem.setTipo(TipoViagem.ROTINEIRA);
        viagem.setVeiculo(veiculo);
        viagem.setMotorista(motorista);
        viagem.setCidadeOrigem(linha.getCidadeOrigem() != null ? linha.getCidadeOrigem() : sede());
        viagem.setCidadeDestino(linha.getCidadeDestino());
        viagem.setDataViagem(form.dataViagem());
        viagem.setHorarioSaida(linha.getHorarioSaida());
        viagem.setHorarioChegada(linha.getHorarioChegada());
        viagem.setHorarioRetorno(linha.getHorarioRetorno());
        viagem.setStatus(StatusViagem.PLANEJADA);
        viagem.setCriadoPor(criadoPor);
        viagem = viagemRepository.save(viagem);
        auditoriaService.registrarOperacao("VIAGEM_DESIGNADA", "Viagem", viagem.getId().toString(),
                linha.getCidadeDestino().getNome() + " em " + form.dataViagem() + " — " + motorista.getNomeCompleto());
        return viagem;
    }

    /**
     * Altera o status da viagem (FR-VIA-08 / DT-05). Permitido ao gerente ou ao
     * próprio motorista da viagem; o passageiro não altera.
     */
    @Transactional
    public void alterarStatus(UUID viagemId, StatusViagem novoStatus, UUID solicitanteId, boolean ehGerente) {
        Viagem viagem = buscarPorId(viagemId);
        if (!ehGerente && (solicitanteId == null || !solicitanteId.equals(viagem.getMotorista().getId()))) {
            throw new RegraNegocioException("Você só pode alterar o status das suas próprias viagens");
        }
        viagem.setStatus(novoStatus);
        viagemRepository.save(viagem);
        auditoriaService.registrarOperacao("VIAGEM_STATUS", "Viagem", viagemId.toString(),
                "→ " + novoStatus.getRotulo());
    }

    /** Viagens designadas a um motorista (visão do motorista). */
    public List<Viagem> listarDoMotorista(UUID motoristaId) {
        return viagemRepository.listarDoMotorista(motoristaId);
    }

    /** Monta o painel semanal (domingo→sábado) que contém {@code referencia}. */
    public PainelSemana painelSemana(LocalDate referencia) {
        LocalDate base = referencia != null ? referencia : LocalDate.now();
        // Domingo como primeiro dia: getValue() MON=1..SUN=7 → SUN%7=0.
        LocalDate inicio = base.minusDays(base.getDayOfWeek().getValue() % 7);
        LocalDate fim = inicio.plusDays(6);

        List<Viagem> daSemana = viagemRepository.listarDaSemana(inicio, fim);
        List<PainelSemana.DiaPainel> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate data = inicio.plusDays(i);
            DiaSemana dia = DiaSemana.de(data.getDayOfWeek());
            List<PainelSemana.ItemDia> itens = new ArrayList<>();
            for (LinhaProgramada linha : linhaRepository.ativasNoDia(dia)) {
                Viagem designada = daSemana.stream()
                        .filter(v -> v.getLinhaProgramada() != null
                                && v.getLinhaProgramada().getId().equals(linha.getId())
                                && v.getDataViagem().equals(data))
                        .findFirst().orElse(null);
                itens.add(new PainelSemana.ItemDia(linha, designada));
            }
            dias.add(new PainelSemana.DiaPainel(data, dia, itens));
        }
        return new PainelSemana(inicio, fim, dias);
    }

    /**
     * Valida disponibilidade de veículo/motorista (RN-VIA-08): rejeita se já
     * houver viagem não cancelada com janela sobreposta na mesma data.
     */
    private void validarDisponibilidade(UUID veiculoId, UUID motoristaId, LocalDate data,
                                        LocalTime saida, LocalTime chegada, UUID ignorarId) {
        if (viagemRepository.contarConflitosMotorista(motoristaId, data, saida, chegada, ignorarId) > 0) {
            throw new RegraNegocioException("Motorista indisponível neste horário (já alocado em outra viagem)");
        }
        if (viagemRepository.contarConflitosVeiculo(veiculoId, data, saida, chegada, ignorarId) > 0) {
            throw new RegraNegocioException("Veículo indisponível neste horário (já alocado em outra viagem)");
        }
    }

    /** Cidade-sede configurada (origem padrão das viagens), ou null. */
    private Cidade sede() {
        return configuracaoService.getCidadeSedeId()
                .flatMap(cidadeRepository::findById)
                .orElse(null);
    }

    /** Exclui uma viagem (e seus assentos, por cascade no banco). */
    @Transactional
    public void excluir(UUID id) {
        Viagem viagem = buscarPorId(id);
        viagemRepository.delete(viagem);
        auditoriaService.registrarOperacao("VIAGEM_EXCLUIDA", "Viagem", id.toString(),
                "Destino " + viagem.getCidadeDestino().getNome() + " em " + viagem.getDataViagem());
    }
}
