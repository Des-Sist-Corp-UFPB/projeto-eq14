package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.domain.SolicitacaoViagem;
import br.ufpb.dsc.caladrius.domain.Usuario;
import br.ufpb.dsc.caladrius.domain.enums.DiaSemana;
import br.ufpb.dsc.caladrius.domain.enums.StatusSolicitacao;
import br.ufpb.dsc.caladrius.dto.GradeDiaSemana;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.SolicitacaoViagemRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lógica das solicitações de transporte do passageiro (SPEC-09).
 *
 * <p>O passageiro vê as linhas disponíveis e solicita transporte numa data. A
 * <strong>alocação</strong> acontece quando existe a viagem materializada da
 * linha naquela data (designada pelo gerente — SPEC-06): a solicitação aponta
 * para a viagem e o passageiro passa a ver os dados dela.
 */
@Service
@Transactional(readOnly = true)
public class SolicitacaoViagemService {

    private final SolicitacaoViagemRepository solicitacaoRepository;
    private final LinhaProgramadaRepository linhaRepository;
    private final ViagemRepository viagemRepository;
    private final UsuarioRepository usuarioRepository;

    public SolicitacaoViagemService(SolicitacaoViagemRepository solicitacaoRepository,
                                    LinhaProgramadaRepository linhaRepository,
                                    ViagemRepository viagemRepository,
                                    UsuarioRepository usuarioRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.linhaRepository = linhaRepository;
        this.viagemRepository = viagemRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Linhas ativas oferecidas ao passageiro (aba "Linhas disponíveis"). */
    public List<LinhaProgramada> linhasDisponiveis() {
        return linhaRepository.findByAtivaTrueOrderByHorarioSaidaAsc();
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
        return solicitacaoRepository.save(solicitacao);
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
    }
}
