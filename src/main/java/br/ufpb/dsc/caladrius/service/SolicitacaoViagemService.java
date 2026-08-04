package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.Viagem;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.domain.enums.TipoSolicitacao;
import br.ufpb.dsc.caladrius.dto.GradeDiaSemana;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.notificacao.CanalTipo;
import br.ufpb.dsc.caladrius.notificacao.NotificacaoDestino;
import br.ufpb.dsc.caladrius.observabilidade.RastreamentoService;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.SolicitacaoViagemRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica das solicitações de transporte do passageiro (SPEC-VIA-03).
 *
 * <p>O passageiro vê as linhas disponíveis e solicita transporte numa data. A
 * <strong>alocação</strong> acontece quando existe a viagem materializada da
 * linha naquela data (designada pelo gerente — SPEC-VIA-02): a solicitação aponta
 * para a viagem e o passageiro passa a ver os dados dela.
 */
@Service
@Transactional(readOnly = true)
public class SolicitacaoViagemService {

    private static final Logger log = LoggerFactory.getLogger(SolicitacaoViagemService.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitacaoViagemRepository solicitacaoRepository;
    private final LinhaProgramadaRepository linhaRepository;
    private final ViagemRepository viagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final CidadeRepository cidadeRepository;
    private final NotificacaoService notificacaoService;
    private final WhatsappService whatsappService;
    private final RastreamentoService rastreamento;
    private final AuditoriaService auditoriaService;

    public SolicitacaoViagemService(SolicitacaoViagemRepository solicitacaoRepository,
                                    LinhaProgramadaRepository linhaRepository,
                                    ViagemRepository viagemRepository,
                                    UsuarioRepository usuarioRepository,
                                    CidadeRepository cidadeRepository,
                                    NotificacaoService notificacaoService,
                                    WhatsappService whatsappService,
                                    RastreamentoService rastreamento,
                                    AuditoriaService auditoriaService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.linhaRepository = linhaRepository;
        this.viagemRepository = viagemRepository;
        this.usuarioRepository = usuarioRepository;
        this.cidadeRepository = cidadeRepository;
        this.notificacaoService = notificacaoService;
        this.whatsappService = whatsappService;
        this.rastreamento = rastreamento;
        this.auditoriaService = auditoriaService;
    }

    /** Linhas ativas oferecidas ao passageiro (aba "Linhas disponíveis"). */
    public List<LinhaProgramada> linhasDisponiveis() {
        return linhaRepository.findByAtivaTrueOrderByHorarioSaidaAsc();
    }

    /** Cidades cadastradas oferecidas como destino no fluxo sob demanda (D4). */
    public List<Cidade> cidadesDestino() {
        return cidadeRepository.findAllByOrderByNomeAsc();
    }

    /** Linhas ativas que operam no dia da semana de {@code data} (calendário). */
    public List<LinhaProgramada> linhasQueOperamEm(LocalDate data) {
        DiaSemana dia = DiaSemana.de(data.getDayOfWeek());
        return linhasDisponiveis().stream().filter(linha -> linha.operaEm(dia)).toList();
    }

    /**
     * Grade semanal (domingo→sábado): em cada dia, as linhas ativas que operam.
     * Alimenta o panorama da aba "Linhas disponíveis" (estilo grade de horários).
     */
    public List<GradeDiaSemana> gradeSemanal() {
        List<LinhaProgramada> ativas = linhasDisponiveis();
        List<GradeDiaSemana> grade = new ArrayList<>();
        for (DiaSemana dia : DiaSemana.values()) {
            grade.add(new GradeDiaSemana(dia,
                    ativas.stream().filter(linha -> linha.operaEm(dia)).toList()));
        }
        return grade;
    }

    /**
     * Registra uma solicitação do passageiro para uma linha numa data. Se a viagem
     * dessa linha+data já estiver designada, o passageiro já entra {@code ALOCADA};
     * caso contrário fica {@code PENDENTE} até a designação.
     *
     * @return a solicitação criada
     */
    @Transactional
    public SolicitacaoViagem solicitar(UUID passageiroId, UUID linhaId, LocalDate data, String observacao) {
        Usuario passageiro = usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", passageiroId));
        LinhaProgramada linha = linhaRepository.findById(linhaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Linha programada", linhaId));

        if (!linha.isAtiva()) {
            throw new RegraNegocioException("Esta linha não está disponível para solicitação");
        }
        if (data == null || data.isBefore(LocalDate.now())) {
            throw new RegraNegocioException("Escolha uma data igual ou posterior a hoje");
        }
        if (!linha.operaEm(DiaSemana.de(data.getDayOfWeek()))) {
            throw new RegraNegocioException("Esta linha não opera no dia da semana escolhido");
        }
        if (solicitacaoRepository.existsByPassageiro_IdAndLinha_IdAndDataDesejadaAndStatusNot(
                passageiroId, linhaId, data, StatusSolicitacao.CANCELADA)) {
            throw new RegraNegocioException("Você já tem uma solicitação para esta linha nesta data");
        }

        SolicitacaoViagem solicitacao = new SolicitacaoViagem();
        solicitacao.setPassageiro(passageiro);
        solicitacao.setLinha(linha);
        solicitacao.setDataDesejada(data);
        solicitacao.setObservacao(observacao != null && !observacao.isBlank() ? observacao.trim() : null);
        // Já designada para esta data? Então a solicitação nasce alocada.
        viagemRepository.findByLinhaProgramada_IdAndDataViagem(linhaId, data).ifPresentOrElse(
                viagem -> {
                    solicitacao.setViagem(viagem);
                    solicitacao.setStatus(StatusSolicitacao.ALOCADA);
                },
                () -> solicitacao.setStatus(StatusSolicitacao.PENDENTE));
        SolicitacaoViagem salva = solicitacaoRepository.save(solicitacao);
        auditoriaService.registrarOperacao("SOLICITACAO_CRIADA", "SolicitacaoViagem",
                String.valueOf(salva.getId()),
                "Por linha — " + nomeDestino(salva) + " em " + DATA_BR.format(data)
                        + " (" + salva.getStatus() + ")");
        return salva;
    }

    /**
     * Solicitações do passageiro (aba "Minhas viagens"). Antes de listar,
     * reavalia as {@code PENDENTE}: se a viagem da linha+data passou a existir
     * (o gerente designou depois da solicitação), aloca-a — assim a tela reflete
     * a designação sem ação do passageiro.
     */
    @Transactional
    public List<SolicitacaoViagem> listarDoPassageiro(UUID passageiroId) {
        List<SolicitacaoViagem> solicitacoes = solicitacaoRepository.listarDoPassageiro(passageiroId);
        for (SolicitacaoViagem solicitacao : solicitacoes) {
            if (solicitacao.getStatus() == StatusSolicitacao.PENDENTE) {
                viagemRepository.findByLinhaProgramada_IdAndDataViagem(
                                solicitacao.getLinha().getId(), solicitacao.getDataDesejada())
                        .ifPresent(viagem -> {
                            solicitacao.setViagem(viagem);
                            solicitacao.setStatus(StatusSolicitacao.ALOCADA);
                            solicitacaoRepository.save(solicitacao);
                        });
            }
        }
        return solicitacoes;
    }

    /**
     * Cancela uma solicitação. Só o próprio passageiro dono pode cancelar
     * (não pode cancelar/ver a de outro).
     */
    @Transactional
    public void cancelar(UUID solicitacaoId, UUID passageiroId) {
        SolicitacaoViagem solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação", solicitacaoId));
        if (!solicitacao.getPassageiro().getId().equals(passageiroId)) {
            throw new RegraNegocioException("Você só pode cancelar as suas próprias solicitações");
        }
        if (solicitacao.getStatus() == StatusSolicitacao.CANCELADA) {
            return;
        }
        solicitacao.setStatus(StatusSolicitacao.CANCELADA);
        solicitacao.setViagem(null);
        solicitacaoRepository.save(solicitacao);
        auditoriaService.registrarOperacao("SOLICITACAO_CANCELADA", "SolicitacaoViagem",
                solicitacaoId.toString(), "Cancelada pelo passageiro — " + nomeDestino(solicitacao));
    }

    // ===================== SPEC-WPP-02 — sob demanda =====================

    /**
     * Registra uma solicitação <strong>sob demanda</strong> (SPEC-WPP-02): sem linha,
     * o passageiro informa destino + data + horário + condições. Nasce
     * {@code PENDENTE} aguardando a <strong>avaliação do gestor</strong> (não há
     * alocação automática). Reaproveitada pelo bot do WhatsApp e por telas web.
     *
     * @return a solicitação criada
     */
    @Transactional
    public SolicitacaoViagem solicitarSobDemanda(UUID passageiroId, UUID cidadeDestinoId,
                                                 LocalDate data, LocalTime horario, String condicoes) {
        Usuario passageiro = usuarioRepository.findByIdAndRemovidoEmIsNull(passageiroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", passageiroId));
        Cidade destino = cidadeRepository.findById(cidadeDestinoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", cidadeDestinoId));

        // Span de negócio (SPEC-OPE-01): nomeia a operação de domínio e anexa atributos
        // (destino/tipo) — sem PII (RN-OBS-08). Uma violação de regra abaixo é gravada
        // no span como ERROR; com o agente desligado, todo o rastrear() é no-op (RN-OBS-06).
        Map<String, String> atributos = Map.of(
                "solicitacao.tipo", TipoSolicitacao.SOB_DEMANDA.name(),
                "solicitacao.cidade_destino", destino.getNome());
        return rastreamento.rastrear("solicitar-sob-demanda", atributos, () -> {
            if (data == null || data.isBefore(LocalDate.now())) {
                throw new RegraNegocioException("Escolha uma data igual ou posterior a hoje");
            }
            if (solicitacaoRepository.existsByPassageiro_IdAndCidadeDestino_IdAndDataDesejadaAndStatusNot(
                    passageiroId, cidadeDestinoId, data, StatusSolicitacao.CANCELADA)) {
                throw new RegraNegocioException("Você já tem uma solicitação para este destino nesta data");
            }

            SolicitacaoViagem solicitacao = new SolicitacaoViagem();
            solicitacao.setTipo(TipoSolicitacao.SOB_DEMANDA);
            solicitacao.setPassageiro(passageiro);
            solicitacao.setCidadeDestino(destino);
            solicitacao.setDataDesejada(data);
            solicitacao.setHorarioDesejado(horario);
            solicitacao.setCondicoes(condicoes != null && !condicoes.isBlank() ? condicoes.trim() : null);
            solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            SolicitacaoViagem salva = solicitacaoRepository.save(solicitacao);
            // Log estruturado (vai ao Loki via agente, correlacionado ao trace) — sem PII.
            log.info("solicitação sob demanda criada: solicitacao_id={} destino={} data={} status={}",
                    salva.getId(), destino.getNome(), data, salva.getStatus());
            auditoriaService.registrarOperacao("SOLICITACAO_CRIADA", "SolicitacaoViagem",
                    String.valueOf(salva.getId()),
                    "Sob demanda — " + destino.getNome() + " em " + DATA_BR.format(data));
            return salva;
        });
    }

    /** Fila do gestor: demandas aguardando avaliação (mais antigas primeiro). */
    public List<SolicitacaoViagem> listarDemandaPendente() {
        return solicitacaoRepository.listarPorTipoEStatus(
                TipoSolicitacao.SOB_DEMANDA, StatusSolicitacao.PENDENTE);
    }

    /** Viagens candidatas (mesmo destino+data) para alocar uma demanda. */
    public List<Viagem> viagensCandidatas(UUID solicitacaoId) {
        SolicitacaoViagem solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação", solicitacaoId));
        Cidade destino = solicitacao.getDestino();
        if (destino == null) {
            return List.of();
        }
        return viagemRepository.candidatasParaDemanda(destino.getId(), solicitacao.getDataDesejada());
    }

    /**
     * O gestor <strong>aprova</strong> uma demanda alocando-a a uma viagem
     * (RN-AVU-03): vincula a viagem, marca {@code ALOCADA} e notifica o
     * passageiro (WhatsApp + in-app). A viagem deve ser do mesmo destino da
     * solicitação.
     */
    @Transactional
    public SolicitacaoViagem aprovar(UUID solicitacaoId, UUID viagemId) {
        SolicitacaoViagem solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação", solicitacaoId));

        // 2º span de negócio (SPEC-OPE-01): a decisão do gestor (aprovar/alocar). Erros de
        // regra (não pendente, destino divergente) e a viagem inexistente caem no span
        // como ERROR; sem agente, todo o rastrear() é no-op (RN-OBS-06).
        Map<String, String> atributos = Map.of(
                "solicitacao.id", String.valueOf(solicitacaoId),
                "solicitacao.cidade_destino", nomeDestino(solicitacao));
        return rastreamento.rastrear("aprovar-solicitacao", atributos, () -> {
            if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
                throw new RegraNegocioException("Só é possível aprovar uma solicitação pendente");
            }
            Viagem viagem = viagemRepository.findById(viagemId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Viagem", viagemId));
            Cidade destino = solicitacao.getDestino();
            if (destino != null && !viagem.getCidadeDestino().getId().equals(destino.getId())) {
                throw new RegraNegocioException("A viagem escolhida tem destino diferente do solicitado");
            }

            solicitacao.setViagem(viagem);
            solicitacao.setStatus(StatusSolicitacao.ALOCADA);
            solicitacao.setMotivoRecusa(null);
            SolicitacaoViagem salva = solicitacaoRepository.save(solicitacao);

            String hora = viagem.getHorarioSaida() != null ? viagem.getHorarioSaida().toString() : "";
            notificar(solicitacao.getPassageiro(), "Transporte aprovado",
                    whatsappService.mensagemConfirmacao(nomeDestino(solicitacao),
                            DATA_BR.format(solicitacao.getDataDesejada()), hora));
            log.info("solicitação aprovada: solicitacao_id={} viagem_id={} destino={} status={}",
                    solicitacaoId, viagemId, nomeDestino(solicitacao), salva.getStatus());
            auditoriaService.registrarOperacao("SOLICITACAO_APROVADA", "SolicitacaoViagem",
                    solicitacaoId.toString(),
                    nomeDestino(solicitacao) + " — alocada na viagem " + viagemId);
            return salva;
        });
    }

    /**
     * O gestor <strong>recusa</strong> uma demanda (RN-AVU-04): registra o motivo,
     * marca {@code RECUSADA} e notifica o passageiro.
     */
    @Transactional
    public SolicitacaoViagem recusar(UUID solicitacaoId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da recusa");
        }
        SolicitacaoViagem solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação", solicitacaoId));
        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new RegraNegocioException("Só é possível recusar uma solicitação pendente");
        }

        solicitacao.setStatus(StatusSolicitacao.RECUSADA);
        solicitacao.setMotivoRecusa(motivo.trim());
        solicitacao.setViagem(null);
        SolicitacaoViagem salva = solicitacaoRepository.save(solicitacao);

        notificar(solicitacao.getPassageiro(), "Transporte não aprovado",
                "Seu transporte para " + nomeDestino(solicitacao) + " em "
                        + DATA_BR.format(solicitacao.getDataDesejada())
                        + " não foi aprovado. Motivo: " + motivo.trim());
        auditoriaService.registrarOperacao("SOLICITACAO_RECUSADA", "SolicitacaoViagem",
                solicitacaoId.toString(), nomeDestino(solicitacao) + " — motivo: " + motivo.trim());
        return salva;
    }

    // ----------------------------------------------------------- apoio

    /** Notifica o passageiro pelos canais WhatsApp e in-app (RN-AVU-05/08). */
    private void notificar(Usuario passageiro, String titulo, String mensagem) {
        notificacaoService.enviar(
                new NotificacaoDestino(passageiro.getId(), passageiro.getEmail(), passageiro.getTelefone()),
                titulo, mensagem, CanalTipo.WHATSAPP, CanalTipo.IN_APP);
    }

    private String nomeDestino(SolicitacaoViagem solicitacao) {
        Cidade destino = solicitacao.getDestino();
        return destino != null ? destino.getNome() : "seu destino";
    }
}
