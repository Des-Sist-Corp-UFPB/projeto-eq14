package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.domain.LinhaProgramada;
import br.ufpb.dsc.caladrius.dto.LinhaProgramadaForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.LinhaProgramadaRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Lógica das linhas programadas (templates recorrentes — SPEC-06).
 */
@Service
@Transactional(readOnly = true)
public class LinhaProgramadaService {

    private final LinhaProgramadaRepository linhaRepository;
    private final CidadeRepository cidadeRepository;
    private final ConfiguracaoService configuracaoService;
    private final ViagemRepository viagemRepository;
    private final AuditoriaService auditoriaService;

    public LinhaProgramadaService(LinhaProgramadaRepository linhaRepository,
                                  CidadeRepository cidadeRepository,
                                  ConfiguracaoService configuracaoService,
                                  ViagemRepository viagemRepository,
                                  AuditoriaService auditoriaService) {
        this.linhaRepository = linhaRepository;
        this.cidadeRepository = cidadeRepository;
        this.configuracaoService = configuracaoService;
        this.viagemRepository = viagemRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<LinhaProgramada> listar() {
        return linhaRepository.findAllByOrderByHorarioSaidaAsc();
    }

    public LinhaProgramada buscarPorId(UUID id) {
        return linhaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Linha programada", id));
    }

    @Transactional
    public LinhaProgramada criar(LinhaProgramadaForm form) {
        LinhaProgramada linha = new LinhaProgramada();
        aplicar(linha, form);
        linha = linhaRepository.save(linha);
        auditoriaService.registrarOperacao("LINHA_CRIADA", "LinhaProgramada",
                linha.getId().toString(), descricao(linha));
        return linha;
    }

    @Transactional
    public LinhaProgramada atualizar(UUID id, LinhaProgramadaForm form) {
        LinhaProgramada linha = buscarPorId(id);
        aplicar(linha, form);
        linha = linhaRepository.save(linha);
        auditoriaService.registrarOperacao("LINHA_ATUALIZADA", "LinhaProgramada",
                linha.getId().toString(), descricao(linha));
        return linha;
    }

    /** Habilita/desabilita a linha sem apagá-la (RN-VIA-10). */
    @Transactional
    public void alternarAtiva(UUID id) {
        LinhaProgramada linha = buscarPorId(id);
        linha.setAtiva(!linha.isAtiva());
        linhaRepository.save(linha);
        auditoriaService.registrarOperacao(linha.isAtiva() ? "LINHA_HABILITADA" : "LINHA_DESABILITADA",
                "LinhaProgramada", linha.getId().toString(), descricao(linha));
    }

    /** Exclui a linha; bloqueia se já houver viagens materializadas dela. */
    @Transactional
    public void excluir(UUID id) {
        LinhaProgramada linha = buscarPorId(id);
        if (viagemRepository.countByLinhaProgramada_Id(id) > 0) {
            throw new RegraNegocioException(
                    "Esta linha já tem viagens registradas. Desabilite-a em vez de excluir.");
        }
        linhaRepository.delete(linha);
        auditoriaService.registrarOperacao("LINHA_EXCLUIDA", "LinhaProgramada", id.toString(), descricao(linha));
    }

    // ----------------------------------------------------------- helpers

    private void aplicar(LinhaProgramada linha, LinhaProgramadaForm form) {
        Cidade destino = cidadeRepository.findById(form.cidadeDestinoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", form.cidadeDestinoId()));
        Cidade origem = resolverOrigem(form.cidadeOrigemId());

        if (!form.horarioChegada().isAfter(form.horarioSaida())) {
            throw new RegraNegocioException("O horário de chegada deve ser após o horário de saída");
        }
        if (form.horarioRetorno() != null && !form.horarioRetorno().isAfter(form.horarioChegada())) {
            throw new RegraNegocioException("O horário de retorno deve ser após o horário de chegada");
        }
        if (form.diasOuVazio().isEmpty()) {
            throw new RegraNegocioException("Selecione ao menos um dia da semana");
        }

        linha.setCidadeOrigem(origem);
        linha.setCidadeDestino(destino);
        linha.setHorarioSaida(form.horarioSaida());
        linha.setHorarioChegada(form.horarioChegada());
        linha.setHorarioRetorno(form.horarioRetorno());
        linha.setAtiva(form.ativaOuPadrao());
        linha.setDias(EnumSet.copyOf(form.diasOuVazio()));
    }

    /** Origem informada ou, na ausência, a cidade-sede configurada. */
    private Cidade resolverOrigem(UUID origemId) {
        UUID alvo = origemId != null ? origemId : configuracaoService.getCidadeSedeId().orElse(null);
        if (alvo == null) {
            return null;
        }
        return cidadeRepository.findById(alvo).orElse(null);
    }

    private String descricao(LinhaProgramada l) {
        String origem = l.getCidadeOrigem() != null ? l.getCidadeOrigem().getNome() : "Sede";
        return origem + " → " + l.getCidadeDestino().getNome() + " " + l.getHorarioSaida();
    }
}
