package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.dto.CidadeForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de negócio das cidades (origem e destinos metropolitanos).
 */
@Service
@Transactional(readOnly = true)
public class CidadeService {

    private final CidadeRepository cidadeRepository;
    private final ViagemRepository viagemRepository;
    private final AuditoriaService auditoriaService;

    public CidadeService(CidadeRepository cidadeRepository, ViagemRepository viagemRepository,
                         AuditoriaService auditoriaService) {
        this.cidadeRepository = cidadeRepository;
        this.viagemRepository = viagemRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Lista/busca cidades paginadas. Busca vazia retorna todas. */
    public Page<Cidade> buscar(String busca, Pageable pageable) {
        if (!StringUtils.hasText(busca)) {
            return cidadeRepository.findAll(pageable);
        }
        return cidadeRepository.buscar(busca.trim(), pageable);
    }

    /** Busca uma cidade pelo id ou lança {@link RecursoNaoEncontradoException}. */
    public Cidade buscarPorId(UUID id) {
        return cidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }

    /** Lista todas as cidades ordenadas por nome (para selects de viagem). */
    public List<Cidade> listarTodas() {
        return cidadeRepository.findAllByOrderByNomeAsc();
    }

    /** Cria uma nova cidade. */
    @Transactional
    public Cidade criar(CidadeForm form) {
        Cidade cidade = new Cidade(form.nome().trim(), form.uf().trim().toUpperCase(), form.tipo());
        cidade = cidadeRepository.save(cidade);
        auditoriaService.registrarOperacao("CIDADE_CRIADA", "Cidade",
                cidade.getId().toString(), cidade.getNome() + "/" + cidade.getUf());
        return cidade;
    }

    /** Atualiza uma cidade existente. */
    @Transactional
    public Cidade atualizar(UUID id, CidadeForm form) {
        Cidade cidade = buscarPorId(id);
        cidade.setNome(form.nome().trim());
        cidade.setUf(form.uf().trim().toUpperCase());
        cidade.setTipo(form.tipo());
        cidade = cidadeRepository.save(cidade);
        auditoriaService.registrarOperacao("CIDADE_ATUALIZADA", "Cidade",
                cidade.getId().toString(), cidade.getNome() + "/" + cidade.getUf());
        return cidade;
    }

    /** Conta quantas viagens têm esta cidade como destino (DT-01: aviso de exclusão). */
    public long contarViagensVinculadas(UUID id) {
        return viagemRepository.countByCidadeDestino_Id(id);
    }

    /**
     * Exclui uma cidade (remoção física — cidades são dados de referência).
     *
     * <p>DT-01: como as viagens referenciam a cidade de destino (FK), as viagens
     * vinculadas são removidas antes da cidade, evitando violação de integridade.
     * O usuário é avisado no popup de confirmação antes de chamar este método.
     *
     * @return quantidade de viagens removidas em cascata
     */
    @Transactional
    public long excluir(UUID id) {
        Cidade cidade = buscarPorId(id);
        long viagensRemovidas = viagemRepository.countByCidadeDestino_Id(id);
        if (viagensRemovidas > 0) {
            viagemRepository.deleteByCidadeDestino_Id(id);
        }
        cidadeRepository.delete(cidade);
        auditoriaService.registrarOperacao("CIDADE_EXCLUIDA", "Cidade", id.toString(),
                cidade.getNome() + "/" + cidade.getUf() + " — " + viagensRemovidas + " viagem(ns) removida(s)");
        return viagensRemovidas;
    }
}
