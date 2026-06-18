package br.ufpb.dsc.caladrius.repository;

import br.ufpb.dsc.caladrius.domain.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório dos endereços do passageiro + consultas de análise (SPEC-07).
 */
@Repository
public interface EnderecoRepository extends JpaRepository<Endereco, UUID> {

    /** Endereço do passageiro (1-para-1). */
    Optional<Endereco> findByUsuarioId(UUID usuarioId);

    boolean existsByUsuarioId(UUID usuarioId);

    /** Contagem de passageiros por bairro (aba de análise). */
    @Query("""
            select e.bairro as rotulo, count(e) as total
            from Endereco e
            where e.bairro is not null and e.bairro <> ''
            group by e.bairro
            order by count(e) desc, e.bairro asc
            """)
    List<Contagem> contarPorBairro();

    /** Contagem de passageiros por município (aba de análise). */
    @Query("""
            select m.nome as rotulo, count(e) as total
            from Endereco e join e.municipio m
            group by m.nome
            order by count(e) desc, m.nome asc
            """)
    List<Contagem> contarPorMunicipio();

    /** Projeção para as contagens da análise. */
    interface Contagem {
        String getRotulo();
        long getTotal();
    }
}
