package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Veiculo;
import br.ufpb.dsc.caladrius.domain.enums.StatusVeiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link Veiculo}.
 *
 * <p>Todos os métodos filtram {@code removido_em IS NULL} para respeitar o
 * soft-delete: veículos "excluídos" não aparecem nas listagens nem nas buscas.
 */
@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, UUID> {

    /** Lista veículos ativos (não removidos), paginados. */
    Page<Veiculo> findByRemovidoEmIsNull(Pageable pageable);

    /** Lista todos os veículos ativos ordenados por placa (para selects). */
    List<Veiculo> findByRemovidoEmIsNullOrderByPlacaAsc();

    /**
     * Lista veículos ativos com um status específico, ordenados por placa
     * (DT-04: alimentar o select de viagens apenas com veículos DISPONÍVEL).
     */
    List<Veiculo> findByRemovidoEmIsNullAndStatusOrderByPlacaAsc(StatusVeiculo status);

    /** Busca um veículo ativo pelo id. */
    Optional<Veiculo> findByIdAndRemovidoEmIsNull(UUID id);

    /**
     * Busca veículos ativos por placa, marca ou modelo (case-insensitive).
     */
    @Query("""
            select v from Veiculo v
            where v.removidoEm is null
              and (lower(v.placa)  like lower(concat('%', :busca, '%'))
                or lower(v.marca)  like lower(concat('%', :busca, '%'))
                or lower(v.modelo) like lower(concat('%', :busca, '%')))
            """)
    Page<Veiculo> buscar(@Param("busca") String busca, Pageable pageable);

    /** Verifica se já existe veículo ativo com a placa informada. */
    boolean existsByPlacaIgnoreCaseAndRemovidoEmIsNull(String placa);

    /** Verifica duplicidade de placa ignorando o próprio veículo (na edição). */
    boolean existsByPlacaIgnoreCaseAndRemovidoEmIsNullAndIdNot(String placa, UUID id);

    /** Conta veículos ativos (para o painel inicial). */
    long countByRemovidoEmIsNull();
}
