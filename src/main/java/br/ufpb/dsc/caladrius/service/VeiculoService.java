package br.ufpb.dsc.caladrius.service;

import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.enums.StatusVeiculo;
import br.ufpb.dsc.caladrius.dto.VeiculoForm;
import br.ufpb.dsc.caladrius.exception.RecursoNaoEncontradoException;
import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de negócio dos veículos da frota.
 *
 * <p>Aplica <strong>soft-delete</strong> (excluir = preencher {@code removidoEm})
 * e garante a unicidade de placa entre veículos ativos.
 */
@Service
@Transactional(readOnly = true)
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;

    public VeiculoService(VeiculoRepository veiculoRepository) {
        this.veiculoRepository = veiculoRepository;
    }

    /** Lista/busca veículos ativos paginados. Busca vazia retorna todos. */
    public Page<Veiculo> buscar(String busca, Pageable pageable) {
        if (!StringUtils.hasText(busca)) {
            return veiculoRepository.findByRemovidoEmIsNull(pageable);
        }
        return veiculoRepository.buscar(busca.trim(), pageable);
    }

    /** Busca um veículo ativo pelo id ou lança {@link RecursoNaoEncontradoException}. */
    public Veiculo buscarPorId(UUID id) {
        return veiculoRepository.findByIdAndRemovidoEmIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    /** Lista todos os veículos ativos (para selects de viagem). */
    public List<Veiculo> listarAtivos() {
        return veiculoRepository.findByRemovidoEmIsNullOrderByPlacaAsc();
    }

    /** Cria um novo veículo a partir do formulário. */
    @Transactional
    public Veiculo criar(VeiculoForm form) {
        String placa = normalizarPlaca(form.placa());
        if (veiculoRepository.existsByPlacaIgnoreCaseAndRemovidoEmIsNull(placa)) {
            throw new RegraNegocioException("Já existe um veículo ativo com a placa " + placa);
        }
        Veiculo veiculo = new Veiculo(
                placa, form.marca().trim(), form.modelo().trim(),
                form.ano(), form.tipo(), form.capacidade(), form.possuiAcessibilidade());
        // Na criação o status padrão é DISPONIVEL (definido no construtor),
        // mas respeitamos um status informado, se houver.
        if (form.status() != null) {
            veiculo.setStatus(form.status());
        }
        return veiculoRepository.save(veiculo);
    }

    /** Atualiza um veículo existente. */
    @Transactional
    public Veiculo atualizar(UUID id, VeiculoForm form) {
        Veiculo veiculo = buscarPorId(id);
        String placa = normalizarPlaca(form.placa());
        if (veiculoRepository.existsByPlacaIgnoreCaseAndRemovidoEmIsNullAndIdNot(placa, id)) {
            throw new RegraNegocioException("Já existe outro veículo ativo com a placa " + placa);
        }
        veiculo.setPlaca(placa);
        veiculo.setMarca(form.marca().trim());
        veiculo.setModelo(form.modelo().trim());
        veiculo.setAno(form.ano());
        veiculo.setTipo(form.tipo());
        veiculo.setCapacidade(form.capacidade());
        veiculo.setPossuiAcessibilidade(form.possuiAcessibilidade());
        veiculo.setStatus(form.status() != null ? form.status() : StatusVeiculo.DISPONIVEL);
        return veiculoRepository.save(veiculo);
    }

    /** Exclusão lógica (soft-delete): marca {@code removidoEm}, não apaga a linha. */
    @Transactional
    public void excluir(UUID id) {
        Veiculo veiculo = buscarPorId(id);
        veiculo.setRemovidoEm(Instant.now());
        veiculoRepository.save(veiculo);
    }

    private String normalizarPlaca(String placa) {
        return placa == null ? null : placa.trim().toUpperCase().replace("-", "");
    }
}
