package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Cidade;
import br.ufpb.dsc.caladrius.dto.CidadeForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.repository.CidadeRepository;
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

    public CidadeService(CidadeRepository cidadeRepository) {
        this.cidadeRepository = cidadeRepository;
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
        return cidadeRepository.save(cidade);
    }

    /** Atualiza uma cidade existente. */
    @Transactional
    public Cidade atualizar(UUID id, CidadeForm form) {
        Cidade cidade = buscarPorId(id);
        cidade.setNome(form.nome().trim());
        cidade.setUf(form.uf().trim().toUpperCase());
        cidade.setTipo(form.tipo());
        return cidadeRepository.save(cidade);
    }

    /** Exclui uma cidade (remoção física — cidades são dados de referência). */
    @Transactional
    public void excluir(UUID id) {
        Cidade cidade = buscarPorId(id);
        cidadeRepository.delete(cidade);
    }
}
