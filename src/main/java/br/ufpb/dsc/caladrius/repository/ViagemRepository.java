package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Viagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositório Spring Data JPA para {@link Viagem}.
 */
@Repository
public interface ViagemRepository extends JpaRepository<Viagem, UUID> {

    /**
     * Lista viagens já com veículo, motorista e cidade carregados (JOIN FETCH),
     * evitando o problema de N+1 consultas na tela de listagem.
     *
     * <p>Como todos os relacionamentos são {@code @ManyToOne} (sem coleções), o
     * {@code JOIN FETCH} é compatível com paginação.
     */
    @Query(value = """
            select v from Viagem v
            join fetch v.veiculo
            join fetch v.motorista
            join fetch v.cidadeDestino
            order by v.dataViagem desc, v.horarioSaida desc
            """,
            countQuery = "select count(v) from Viagem v")
    Page<Viagem> listarComRelacionamentos(Pageable pageable);
}
